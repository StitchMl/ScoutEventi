package it.buonacaccia.app.background

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.notify.Notifier
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class NewEventsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val repo: EventsRepository by inject()
    private val notifier: Notifier by inject()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override suspend fun doWork(): Result = try {
        Timber.d("NewEventsWorker.start")
        val events = repo.fetch()
        Timber.d("downloaded events=%d", events.size)

        // ✅ Refresh Cache
        EventStore.upsertEvents(applicationContext, events)
        // ✅ Purge of closed (subsCloseDate < today)
        val removed = EventStore.purgeClosed(applicationContext, java.time.LocalDate.now())
        if (removed.isNotEmpty()) Timber.d("purged closed events: %s", removed)

        // Reload current cache for notifications
        val cached = EventStore.cachedEventsFlow(applicationContext).first()

        val seen = EventStore.seenIdsFlow(applicationContext).first()
        val interestedTypes = EventStore.notifyTypesFlow(applicationContext).first()
        val mutedTypes = EventStore.muteTypesFlow(applicationContext).first()
        val interestedRegions = EventStore.notifyRegionsFlow(applicationContext).first()

        val fresh = cached
            .filter { it.id != null && it.id !in seen }
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
                val perm = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                )
                if (perm == PackageManager.PERMISSION_GRANTED) {
                    notifier.notifyNewEvent(applicationContext, e)
                } else {
                    Timber.w("Permission POST_NOTIFICATIONS not granted, skipping notify for %s", e.id)
                }
            } catch (se: SecurityException) {
                Timber.e(se, "SecurityException while notifying %s", e.id)
            }
        }
        val newIds = fresh.mapNotNull { it.id }.toSet()
        if (newIds.isNotEmpty()) EventStore.addSeenIds(applicationContext, newIds)

        Result.success()
    } catch (t: Throwable) {
        Timber.e(t, "NewEventsWorker.error")
        Result.retry()
    }
}