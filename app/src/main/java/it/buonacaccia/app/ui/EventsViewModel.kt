package it.buonacaccia.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.Branch
import it.buonacaccia.app.data.EventsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class UnitFilter { TUTTE, BRANCO, REPARTO, CLAN, CAPI }

data class EventsUiState(
    val loading: Boolean = false,
    val items: List<BcEvent> = emptyList(),
    val error: String? = null,
    val query: String = "",
    val region: String? = null,
    val unit: UnitFilter = UnitFilter.TUTTE,
    val onlyOpen: Boolean = false
)

private val IT_REGIONS = listOf(
    "Abruzzo", "Basilicata", "Calabria", "Campania", "Emilia-Romagna", "Friuli-Venezia Giulia",
    "Lazio", "Liguria", "Lombardia", "Marche", "Molise", "Piemonte", "Puglia", "Sardegna",
    "Sicilia", "Toscana", "Trentino-Alto Adige", "Umbria", "Valle d'Aosta", "Veneto",
    // short/common forms
    "Emilia Romagna", "Friuli Venezia Giulia", "Trentino", "Alto Adige", "Val d'Aosta"
).sortedBy { it.length }.reversed()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class EventsViewModel(
    private val repo: EventsRepository
) : ViewModel() {

    var state by mutableStateOf(EventsUiState(loading = true))
        private set

    private var loadJob: Job? = null

    init {
        refresh()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun refresh() {
        // Avoid double refresh if already in progress
        if (loadJob?.isActive == true) return

        // Delete possibly an old job
        loadJob?.cancel()

        state = state.copy(loading = true, error = null)

        loadJob = viewModelScope.launch {
            try {
                val list = repo.fetch(all = true)
                state = state.copy(loading = false, items = list, error = null)
            } catch (_: CancellationException) {
                // ⚠️ cancellation is "normal": don't show it in UI
                // Relaunch if you want to propagate to higher level:
                // throw ce
                state = state.copy(loading = false) // no error
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Errore di rete")
            }
        }
    }

    fun onQueryChange(q: String) { state = state.copy(query = q) }
    fun onRegionChange(r: String?) { state = state.copy(region = r) }
    fun onUnitChange(u: UnitFilter) { state = state.copy(unit = u) }
    fun onOnlyOpenChange(enabled: Boolean) { state = state.copy(onlyOpen = enabled) }
    fun seedFromCache(items: List<BcEvent>) {
        if (items.isNotEmpty() && state.items.isEmpty()) {
            state = state.copy(loading = false, items = items, error = null)
        }
    }

    private fun guessRegion(ev: BcEvent): String? {
        ev.region?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val hay = listOfNotNull(ev.location, ev.title).joinToString(" ").lowercase()
        return IT_REGIONS.firstOrNull { r -> hay.contains(r.lowercase()) }
    }

    val regions: List<String> get() {
        val present = state.items.mapNotNull { guessRegion(it) }
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)
        return listOf("Tutte") + present.toList()
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
                        UnitFilter.BRANCO -> ev.branch == Branch.LC
                        UnitFilter.REPARTO -> ev.branch == Branch.EG
                        UnitFilter.CLAN -> ev.branch == Branch.RS
                        UnitFilter.CAPI -> ev.branch == Branch.CAPI
                    }
                }
                .filter { ev ->
                    if (state.onlyOpen)
                        ev.statusColor == "green" || ev.statusColor == "yellow"
                    else true
                }
                .toList()
        }
}