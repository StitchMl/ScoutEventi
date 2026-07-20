package it.buonacaccia.app.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.Branch
import it.buonacaccia.app.data.BuonaCacciaFilter
import it.buonacaccia.app.data.BuonaCacciaScopes
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.data.FetchSafety
import it.buonacaccia.app.data.guessZone
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class EventsUiState(
    val loading: Boolean = false,
    val items: List<BcEvent> = emptyList(),
    val error: String? = null,
    val query: String = "",
    val region: String? = null,
    val zone: String? = null,
    val unit: UnitFilter = UnitFilter.TUTTE,
    val onlyOpen: Boolean = false
)

@SuppressLint("StaticFieldLeak")
class EventsViewModel(
    private val repo: EventsRepository,
    private val context: Context
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
                val cachedIds = cachedSnapshot.mapNotNull { it.id }.toSet()

                val list = if (scopeFilter != null) {
                    try {
                        repo.fetchByFilters(
                            filters = listOf(scopeFilter),
                            all = true,
                            enrichPredicate = { ev -> ev.id !in cachedIds }
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Throwable) {
                        repo.fetch(
                            all = true,
                            minimumExpectedCount = minimumExpectedCount,
                            enrichPredicate = { ev -> ev.id !in cachedIds }
                        )
                    }
                } else {
                    repo.fetch(
                        all = true,
                        minimumExpectedCount = minimumExpectedCount,
                        enrichPredicate = { ev -> ev.id !in cachedIds }
                    )
                }

                // Merge details from cache for events that were already cached
                val enrichedList = list.map { ev ->
                    val cached = cachedSnapshot.firstOrNull { it.id == ev.id }
                    if (cached != null) {
                        ev.copy(
                            zone = cached.zone ?: ev.zone,
                            subsOpenDate = cached.subsOpenDate ?: ev.subsOpenDate,
                            subsCloseDate = cached.subsCloseDate ?: ev.subsCloseDate
                        )
                    } else {
                        ev
                    }
                }

                // Persist the newly fetched/enriched events into the local cache
                runCatching {
                    EventStore.upsertEvents(context, enrichedList)
                }

                if (generation == loadGeneration) {
                    state = state.copy(loading = false, items = enrichedList, error = null)
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
    fun onZoneChange(z: String?) {
        if (state.zone == z) return
        state = state.copy(zone = z)
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

    val regions: List<String> get() = EventFiltering.availableRegions(state.items)

    val zones: List<String> get() {
        val present = state.items.mapNotNull { it.guessZone() }
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)
        return listOf("Tutte") + present.toList()
    }

    val filtered: List<BcEvent>
        get() = EventFiltering.filter(state.items, state)
}
