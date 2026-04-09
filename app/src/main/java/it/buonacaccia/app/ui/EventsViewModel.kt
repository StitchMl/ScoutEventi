package it.buonacaccia.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.Branch
import it.buonacaccia.app.data.BuonaCacciaFilter
import it.buonacaccia.app.data.BuonaCacciaScopes
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.data.FetchSafety
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
    "Emilia Romagna", "Friuli Venezia Giulia", "Trentino", "Alto Adige", "Val d'Aosta"
).sortedBy { it.length }.reversed()

class EventsViewModel(
    private val repo: EventsRepository
) : ViewModel() {

    var state by mutableStateOf(EventsUiState(loading = true))
        private set

    private var loadJob: Job? = null
    private var cachedSnapshot: List<BcEvent> = emptyList()
    private var loadGeneration: Long = 0

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        val fallbackItems = cachedSnapshot.ifEmpty { state.items }
        val scopeFilter = currentServerFilter()
        val minimumExpectedCount = minimumExpectedCountForFullRefresh(scopeFilter)
        val generation = ++loadGeneration
        state = state.copy(loading = true, error = null, items = fallbackItems)

        loadJob = viewModelScope.launch {
            try {
                val list = if (scopeFilter != null) {
                    try {
                        repo.fetchByFilters(filters = listOf(scopeFilter), all = true)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Throwable) {
                        repo.fetch(all = true, minimumExpectedCount = minimumExpectedCount)
                    }
                } else {
                    repo.fetch(all = true, minimumExpectedCount = minimumExpectedCount)
                }
                if (generation == loadGeneration) {
                    state = state.copy(loading = false, items = list, error = null)
                }
            } catch (_: CancellationException) {
                if (generation == loadGeneration) {
                    state = state.copy(loading = false)
                }
            } catch (e: Exception) {
                val fallback = cachedSnapshot.ifEmpty { state.items }
                if (generation == loadGeneration) {
                    state = state.copy(
                        loading = false,
                        items = fallback,
                        error = e.message ?: "Errore di rete"
                    )
                }
            }
        }
    }

    fun onQueryChange(q: String) { state = state.copy(query = q) }
    fun onRegionChange(r: String?) {
        if (state.region == r) return
        val previousFilter = currentServerFilter()
        state = state.copy(region = r)
        if (currentServerFilter() != previousFilter) {
            refresh()
        }
    }
    fun onUnitChange(u: UnitFilter) {
        if (state.unit == u) return
        val previousFilter = currentServerFilter()
        state = state.copy(unit = u)
        if (currentServerFilter() != previousFilter) {
            refresh()
        }
    }
    fun onOnlyOpenChange(enabled: Boolean) { state = state.copy(onlyOpen = enabled) }

    fun seedFromCache(items: List<BcEvent>) {
        cachedSnapshot = items
        if (items.isNotEmpty() && state.items.isEmpty()) {
            state = state.copy(loading = false, items = items, error = null)
        }
    }

    private fun currentServerFilter(): BuonaCacciaFilter? =
        BuonaCacciaScopes.filterOf(
            regionName = state.region,
            branch = selectedBranch(),
        )

    private fun minimumExpectedCountForFullRefresh(scopeFilter: BuonaCacciaFilter?): Int? {
        if (scopeFilter != null) return null

        val referenceCount = maxOf(cachedSnapshot.size, state.items.size)
        return FetchSafety.minimumExpectedCountForFullDataset(referenceCount)
    }

    private fun selectedBranch(): Branch? =
        when (state.unit) {
            UnitFilter.TUTTE -> null
            UnitFilter.BRANCO -> Branch.LC
            UnitFilter.REPARTO -> Branch.EG
            UnitFilter.CLAN -> Branch.RS
            UnitFilter.CAPI -> Branch.CAPI
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
                    if (q.isBlank()) {
                        true
                    } else {
                        ev.title.lowercase().contains(q) ||
                            (ev.region?.lowercase()?.contains(q) == true) ||
                            (ev.type?.lowercase()?.contains(q) == true) ||
                            (ev.location?.lowercase()?.contains(q) == true)
                    }
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
                    if (state.onlyOpen) {
                        ev.statusColor == "green" || ev.statusColor == "yellow"
                    } else {
                        true
                    }
                }
                .toList()
        }
}
