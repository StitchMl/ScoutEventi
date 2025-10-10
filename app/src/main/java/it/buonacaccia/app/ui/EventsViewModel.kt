package it.buonacaccia.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class UnitFilter { TUTTE, BRANCO, REPARTO, CLAN, CAPI }

data class EventsUiState(
    val loading: Boolean = false,
    val items: List<BcEvent> = emptyList(),
    val error: String? = null,
    val query: String = "",
    val region: String? = null,           // null = tutte
    val unit: UnitFilter = UnitFilter.TUTTE
)

private val IT_REGIONS = listOf(
    "Abruzzo","Basilicata","Calabria","Campania","Emilia-Romagna","Friuli-Venezia Giulia",
    "Lazio","Liguria","Lombardia","Marche","Molise","Piemonte","Puglia","Sardegna",
    "Sicilia","Toscana","Trentino-Alto Adige","Umbria","Valle d'Aosta","Veneto",
    // short/common forms
    "Emilia Romagna","Friuli Venezia Giulia","Trentino","Alto Adige","Val d'Aosta"
).sortedBy { it.length }.reversed() // prima i nomi più lunghi per match più stabili

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repo: EventsRepository
) : ViewModel() {

    var state by mutableStateOf(EventsUiState(loading = true))
        private set

    private var loadJob: Job? = null

    init { refresh() }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun refresh() {
        loadJob?.cancel()
        state = state.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            runCatching { repo.fetch(all = true) }
                .onSuccess { list -> state = state.copy(loading = false, items = list) }
                .onFailure { e -> state = state.copy(loading = false, error = e.message ?: "Error") }
        }
    }

    fun onQueryChange(q: String) { state = state.copy(query = q) }
    fun onRegionChange(r: String?) { state = state.copy(region = r) }
    fun onUnitChange(u: UnitFilter) { state = state.copy(unit = u) }

    /** Attempt to get region from ev.region, otherwise from location/title */
    private fun guessRegion(ev: BcEvent): String? {
        ev.region?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val hay = listOfNotNull(ev.location, ev.title).joinToString(" ").lowercase()
        return IT_REGIONS.firstOrNull { r -> hay.contains(r.lowercase()) }
    }

    /** List regions really present in events (with "All" in the lead). */
    val regions: List<String> get() {
        val present = state.items.mapNotNull { guessRegion(it) }
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)
        return listOf("Tutte") + present.toList()
    }

    /** Classify the event in the unit using type/title and actual acronyms (LC/EG/RS/ROSS) */
    private fun classifyUnit(ev: BcEvent): UnitFilter? {
        // we read directly from the "Type" column
        val t = (ev.type ?: "").trim().uppercase(Locale.ROOT)

        return when {
            t.startsWith("LC") -> UnitFilter.BRANCO
            t.startsWith("EG") -> UnitFilter.REPARTO
            t.startsWith("RS") || t.contains("ROSS") -> UnitFilter.CLAN
            t.contains("CAPI") -> UnitFilter.CAPI
            else -> null
        }
    }

    val filtered: List<BcEvent>
        get() {
            val q = state.query.trim().lowercase()
            return state.items
                .asSequence()
                .filter { ev ->
                    if (q.isBlank()) true else
                        ev.title.lowercase().contains(q) ||
                                (ev.region?.lowercase()?.contains(q) == true) ||
                                (ev.type?.lowercase()?.contains(q) == true) ||
                                (ev.location?.lowercase()?.contains(q) == true)
                }
                .filter { ev ->
                    state.region == null ||
                            state.region == "Tutte" ||
                            guessRegion(ev)?.equals(state.region, ignoreCase = true) == true
                }
                .filter { ev ->
                    when (state.unit) {
                        UnitFilter.TUTTE -> true
                        else -> classifyUnit(ev) == state.unit
                    }
                }
                .toList()
        }
}