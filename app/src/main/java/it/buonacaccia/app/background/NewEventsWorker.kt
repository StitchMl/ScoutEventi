package it.buonacaccia.app.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.data.BuonaCacciaRegions
import it.buonacaccia.app.data.BuonaCacciaFilter
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.data.FetchSafety
import it.buonacaccia.app.data.NotificationRuleEngine
import it.buonacaccia.app.data.shouldEnrichRegistrationWindow
import it.buonacaccia.app.notify.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.time.LocalDate

class NewEventsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val repo: EventsRepository by inject()
    private val notifier: Notifier by inject()

    override suspend fun doWork(): Result = try {
        Timber.d("NewEventsWorker.start")
        val today = LocalDate.now()
        val cachedSnapshot = EventStore.cachedEventsFlow(applicationContext).first()
        val seenKeysBefore = EventStore.seenIdsFlow(applicationContext).first()
        val minimumExpectedCount = FetchSafety.minimumExpectedCountForFullDataset(cachedSnapshot.size)
        val notificationPreferences = EventStore.notificationPreferences(applicationContext)
        val prefilterRegions = NotificationRuleEngine.regionsForFetchPrefilter(notificationPreferences) ?: emptySet()
        val filtersByRegion = prefilterRegions.mapNotNull { region ->
            BuonaCacciaRegions.filterOf(region)?.let { region to it }
        }.toMap()
        val filters = filtersByRegion.values
        val unmappedRegions = prefilterRegions - filtersByRegion.keys
        val canUseRegionFilters = prefilterRegions.isNotEmpty() && unmappedRegions.isEmpty() && filters.isNotEmpty()

        if (prefilterRegions.isNotEmpty() && filters.isEmpty()) {
            Timber.w("No BuonaCaccia region filters mapped from %s, falling back to full fetch", prefilterRegions)
        } else if (unmappedRegions.isNotEmpty()) {
            Timber.w(
                "Some interested regions could not be mapped to BuonaCaccia filters (%s), falling back to full fetch",
                unmappedRegions
            )
        }

        val events = fetchEvents(filters, canUseRegionFilters, minimumExpectedCount, today)
        Timber.d("downloaded events=%d filters=%d", events.size, filters.size)

        EventStore.upsertEvents(applicationContext, events)
        val removed = EventStore.purgeClosed(applicationContext, today)
        if (removed.isNotEmpty()) Timber.d("purged closed events: %s", removed)

        val cached = EventStore.cachedEventsFlow(applicationContext).first()
        if (cachedSnapshot.isEmpty() && seenKeysBefore.isEmpty()) {
            val bootstrapKeys = cached.map(EventStore::eventKeyOf).toSet()
            if (bootstrapKeys.isNotEmpty()) {
                EventStore.addSeenIds(applicationContext, bootstrapKeys)
            }
            Timber.i("Initial sync bootstrap detected, seeded %d seen events without notifications", bootstrapKeys.size)
            return Result.success()
        }

        val seenKeys = EventStore.seenIdsFlow(applicationContext).first()
        val fresh = cached
            .filter { EventStore.eventKeyOf(it) !in seenKeys }
            .filter { event -> NotificationRuleEngine.shouldNotify(event, notificationPreferences) }

        Timber.d("toNotify count=%d", fresh.size)
        fresh.forEach(::notifyEvent)

        val newKeys = fresh.map(EventStore::eventKeyOf).toSet()
        if (newKeys.isNotEmpty()) EventStore.addSeenIds(applicationContext, newKeys)

        Result.success()
    } catch (ce: CancellationException) {
        Timber.i("NewEventsWorker cancelled")
        throw ce
    } catch (t: Throwable) {
        Timber.e(t, "NewEventsWorker.error")
        Result.retry()
    }

    private suspend fun fetchEvents(
        filters: Collection<BuonaCacciaFilter>,
        useFilters: Boolean,
        minimumExpectedCount: Int?,
        today: LocalDate,
    ): List<BcEvent> {
        val enrich: (BcEvent) -> Boolean = { it.shouldEnrichRegistrationWindow(today) }
        if (!useFilters) return repo.fetch(minimumExpectedCount = minimumExpectedCount, enrichPredicate = enrich)
        return try {
            repo.fetchByFilters(filters, enrichPredicate = enrich)
        } catch (ce: CancellationException) {
            throw ce
        } catch (error: Throwable) {
            Timber.w(error, "Filtered new-events fetch failed, retrying with full fetch")
            repo.fetch(minimumExpectedCount = minimumExpectedCount, enrichPredicate = enrich)
        }
    }

    private fun notifyEvent(event: BcEvent) {
        Timber.i("notify id=%s title=%s", event.id, event.title)
        if (!canPostNotifications()) {
            Timber.w("Permission POST_NOTIFICATIONS not granted, skipping notify for %s", event.id)
            return
        }
        try {
            notifier.notifyNewEvent(applicationContext, event)
        } catch (error: SecurityException) {
            Timber.e(error, "SecurityException while notifying %s", event.id)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}
