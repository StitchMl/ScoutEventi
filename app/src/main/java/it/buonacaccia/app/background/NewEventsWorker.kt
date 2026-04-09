package it.buonacaccia.app.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.data.BuonaCacciaRegions
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.data.FetchSafety
import it.buonacaccia.app.notify.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class NewEventsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val repo: EventsRepository by inject()
    private val notifier: Notifier by inject()

    override suspend fun doWork(): Result = try {
        Timber.d("NewEventsWorker.start")
        val cachedSnapshot = EventStore.cachedEventsFlow(applicationContext).first()
        val seenKeysBefore = EventStore.seenIdsFlow(applicationContext).first()
        val minimumExpectedCount = FetchSafety.minimumExpectedCountForFullDataset(cachedSnapshot.size)
        val interestedRegions = EventStore.notifyRegionsFlow(applicationContext).first()
        val filtersByRegion = interestedRegions.associateWith { BuonaCacciaRegions.filterOf(it) }
        val filters = filtersByRegion.values.filterNotNull()
        val unmappedRegions = filtersByRegion.filterValues { it == null }.keys
        val canUseRegionFilters = interestedRegions.isNotEmpty() && unmappedRegions.isEmpty() && filters.isNotEmpty()

        if (interestedRegions.isNotEmpty() && filters.isEmpty()) {
            Timber.w("No BuonaCaccia region filters mapped from %s, falling back to full fetch", interestedRegions)
        } else if (unmappedRegions.isNotEmpty()) {
            Timber.w(
                "Some interested regions could not be mapped to BuonaCaccia filters (%s), falling back to full fetch",
                unmappedRegions
            )
        }

        val events = if (canUseRegionFilters) {
            try {
                repo.fetchByFilters(filters, enrichPredicate = { false })
            } catch (ce: CancellationException) {
                throw ce
            } catch (error: Throwable) {
                Timber.w(error, "Filtered new-events fetch failed, retrying with full fetch")
                repo.fetch(
                    minimumExpectedCount = minimumExpectedCount,
                    enrichPredicate = { false }
                )
            }
        } else {
            repo.fetch(
                minimumExpectedCount = minimumExpectedCount,
                enrichPredicate = { false }
            )
        }
        Timber.d("downloaded events=%d filters=%d", events.size, filters.size)

        EventStore.upsertEvents(applicationContext, events)
        val removed = EventStore.purgeClosed(applicationContext, java.time.LocalDate.now())
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
        val interestedTypes = EventStore.notifyTypesFlow(applicationContext).first()
        val mutedTypes = EventStore.muteTypesFlow(applicationContext).first()

        val fresh = cached
            .filter { EventStore.eventKeyOf(it) !in seenKeys }
            .filter { e ->
                val typeOk = if (mutedTypes.isNotEmpty()) {
                    e.type?.isNotBlank() != true || (e.type !in mutedTypes)
                } else {
                    interestedTypes.isEmpty() || (e.type?.isNotBlank() == true && e.type in interestedTypes)
                }
                val regionOk = interestedRegions.isEmpty() ||
                    (e.region?.isNotBlank() == true && e.region in interestedRegions)
                typeOk && regionOk
            }

        Timber.d("toNotify count=%d", fresh.size)
        fresh.forEach { e ->
            Timber.i("notify id=%s title=%s", e.id, e.title)
            try {
                val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                if (canNotify) {
                    notifier.notifyNewEvent(applicationContext, e)
                } else {
                    Timber.w("Permission POST_NOTIFICATIONS not granted, skipping notify for %s", e.id)
                }
            } catch (se: SecurityException) {
                Timber.e(se, "SecurityException while notifying %s", e.id)
            }
        }

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
}
