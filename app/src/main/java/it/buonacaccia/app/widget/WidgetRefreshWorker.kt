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
import it.buonacaccia.app.data.shouldEnrichRegistrationWindow
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
                val today = LocalDate.now()
                val onlyFollowedEverywhere = EventsWidgetKind.entries.all { kind ->
                    EventStore.widgetOnlyFollowedFlow(applicationContext, kind).first()
                }
                val cachedSnapshot = EventStore.cachedEventsFlow(applicationContext).first()
                val minimumExpectedCount = FetchSafety.minimumExpectedCountForFullDataset(cachedSnapshot.size)
                val fresh = refreshEvents(onlyFollowedEverywhere, cachedSnapshot, minimumExpectedCount, today)

                EventStore.upsertEvents(applicationContext, fresh)
                EventStore.purgeClosed(applicationContext, today)
                UpcomingOpeningsWidget().updateAll(applicationContext)
                EventsByDateWidget().updateAll(applicationContext)
            }
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.w(e, "Widget refresh failed, retrying with cached data")
            runCatching { UpcomingOpeningsWidget().updateAll(applicationContext) }
            runCatching { EventsByDateWidget().updateAll(applicationContext) }
            Result.retry()
        }
    }

    private suspend fun refreshEvents(
        onlyFollowed: Boolean,
        cached: List<BcEvent>,
        minimumExpectedCount: Int?,
        today: LocalDate,
    ): List<BcEvent> {
        if (!onlyFollowed) return fetchAll(minimumExpectedCount, today)
        val subscribed = EventStore.subscribedIdsFlow(applicationContext).first()
        if (subscribed.isEmpty()) {
            Timber.d("Widget refresh: solo seguiti attivo ma nessun evento seguito, skip rete")
            return emptyList()
        }
        val subscribedEvents = cached.filter { EventStore.eventKeyOf(it) in subscribed }
        if (subscribedEvents.isEmpty()) {
            Timber.w("Widget refresh: seguiti non presenti in cache, full fetch")
            return fetchAll(minimumExpectedCount, today)
        }
        val filters = BuonaCacciaScopes.regionFiltersOf(subscribedEvents.map { it.region })
        val unmapped = BuonaCacciaScopes.unmappedRegionsOf(subscribedEvents.map { it.region })
        if (filters.isEmpty() || unmapped.isNotEmpty()) {
            Timber.w("Widget refresh: regioni seguite non mappabili %s, full fetch", unmapped)
            return fetchAll(minimumExpectedCount, today)
        }
        return try {
            repo.fetchByFilters(filters, all = true) { it.shouldEnrichRegistrationWindow(today) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (error: Throwable) {
            Timber.w(error, "Widget refresh: filtered fetch failed, full fetch fallback")
            fetchAll(minimumExpectedCount, today)
        }
    }

    private suspend fun fetchAll(minimumExpectedCount: Int?, today: LocalDate): List<BcEvent> =
        repo.fetch(all = true, minimumExpectedCount = minimumExpectedCount) {
            it.shouldEnrichRegistrationWindow(today)
        }
}
