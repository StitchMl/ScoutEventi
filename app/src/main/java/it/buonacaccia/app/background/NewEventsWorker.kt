package it.buonacaccia.app.background

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.notify.Notifier
import it.buonacaccia.app.data.EventsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class NewEventsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: EventsRepository
) : CoroutineWorker(appContext, params) {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override suspend fun doWork(): Result = try {
        // 1) download events
        val events = repo.fetch()

        // 2) get user preferences
        val seen = EventStore.seenIdsFlow(applicationContext).first()
        val interestedTypes = EventStore.notifyTypesFlow(applicationContext).first()
        val mutedTypes = EventStore.muteTypesFlow(applicationContext).first()
        val interestedRegions = EventStore.notifyRegionsFlow(applicationContext).first()

        // 3) filters by "new" and by interest types (if selected)
        val fresh = events
            .filter { e -> e.id != null && e.id !in seen }
            .filter { e ->
                // types: if there are "muted", we notify everything except muted (new types included)
                val typeOk = if (mutedTypes.isNotEmpty()) {
                    e.type?.isNotBlank() != true || (e.type !in mutedTypes)
                } else {
                    // fallback to existing allowlist for retrocompat
                    interestedTypes.isEmpty() ||
                            (e.type?.isNotBlank() == true && e.type in interestedTypes)
                }

                val regionOk = interestedRegions.isEmpty() ||
                        (e.region?.isNotBlank() == true && e.region in interestedRegions)

                typeOk && regionOk
            }

        if (fresh.isNotEmpty()) {
            // 4) a notification for each event
            fresh.forEach { e -> Notifier.notifyNewEvent(applicationContext, e) }

            // 5) Add to list "already seen" so as not to repeat
            val newIds = fresh.mapNotNull { it.id }.toSet()
            EventStore.addSeenIds(applicationContext, newIds)
        }

        Timber.d("Worker: new notified events=${fresh.size}")
        Result.success()
    } catch (t: Throwable) {
        Timber.e(t, "Worker error")
        Result.retry()
    }
}