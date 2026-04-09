package it.buonacaccia.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.data.BuonaCacciaScopes
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.data.FetchSafety
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.time.LocalDate

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val repo: EventsRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            withTimeout(20_000) {
                val onlyFollowed = EventStore.widgetOnlyFollowedFlow(applicationContext).first()
                val cachedSnapshot = EventStore.cachedEventsFlow(applicationContext).first()
                val minimumExpectedCount = FetchSafety.minimumExpectedCountForFullDataset(cachedSnapshot.size)
                val fresh: List<BcEvent> = if (onlyFollowed) {
                    val subscribed = EventStore.subscribedIdsFlow(applicationContext).first()
                    if (subscribed.isEmpty()) {
                        Timber.d("Widget refresh: solo seguiti attivo ma nessun evento seguito, skip rete")
                        emptyList()
                    } else {
                        val subscribedEvents = cachedSnapshot.filter { ev ->
                            EventStore.eventKeyOf(ev) in subscribed
                        }
                        val regionFilters = BuonaCacciaScopes.regionFiltersOf(subscribedEvents.map { it.region })
                        val unmappedRegions = BuonaCacciaScopes.unmappedRegionsOf(subscribedEvents.map { it.region })
                        val canUseRegionFilters =
                            subscribedEvents.isNotEmpty() &&
                                regionFilters.isNotEmpty() &&
                                unmappedRegions.isEmpty()

                        if (subscribedEvents.isEmpty()) {
                            Timber.w("Widget refresh: seguiti non presenti in cache, full fetch")
                            repo.fetch(
                                all = true,
                                minimumExpectedCount = minimumExpectedCount,
                                enrichPredicate = { false }
                            )
                        } else if (canUseRegionFilters) {
                            try {
                                repo.fetchByFilters(
                                    filters = regionFilters,
                                    all = true,
                                    enrichPredicate = { false }
                                )
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (error: Throwable) {
                                Timber.w(error, "Widget refresh: filtered fetch failed, full fetch fallback")
                                repo.fetch(
                                    all = true,
                                    minimumExpectedCount = minimumExpectedCount,
                                    enrichPredicate = { false }
                                )
                            }
                        } else {
                            Timber.w(
                                "Widget refresh: regioni seguite non mappabili %s, full fetch",
                                unmappedRegions
                            )
                            repo.fetch(
                                all = true,
                                minimumExpectedCount = minimumExpectedCount,
                                enrichPredicate = { false }
                            )
                        }
                    }
                } else {
                    repo.fetch(
                        all = true,
                        minimumExpectedCount = minimumExpectedCount,
                        enrichPredicate = { false }
                    )
                }

                EventStore.upsertEvents(applicationContext, fresh)
                EventStore.purgeClosed(applicationContext, LocalDate.now())
                UpcomingOpeningsWidget().updateAll(applicationContext)
            }
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.w(e, "Widget refresh failed, retrying with cached data")
            runCatching { UpcomingOpeningsWidget().updateAll(applicationContext) }
            Result.retry()
        }
    }
}
