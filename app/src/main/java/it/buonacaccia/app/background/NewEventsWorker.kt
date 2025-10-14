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

        val seen = EventStore.seenIdsFlow(applicationContext).first()
        val interestedTypes = EventStore.notifyTypesFlow(applicationContext).first()
        val mutedTypes = EventStore.muteTypesFlow(applicationContext).first()
        val interestedRegions = EventStore.notifyRegionsFlow(applicationContext).first()

        val fresh = events
            .filter { it.id != null && it.id !in seen }
            .filter { e ->
                val typeOk = if (mutedTypes.isNotEmpty()) {
                    e.type?.isNotBlank() != true || (e.type !in mutedTypes)
                } else {
                    interestedTypes.isEmpty() ||
                            (e.type?.isNotBlank() == true && e.type in interestedTypes)
                }
                val regionOk = interestedRegions.isEmpty() ||
                        (e.region?.isNotBlank() == true && e.region in interestedRegions)
                typeOk && regionOk
            }

        Timber.d("toNotify count=%d", fresh.size)
        fresh.forEach { e ->
            Timber.i("notify id=%s title=%s", e.id, e.title)
            notifier.notifyNewEvent(applicationContext, e)
        }
        val newIds = fresh.mapNotNull { it.id }.toSet()
        if (newIds.isNotEmpty()) EventStore.addSeenIds(applicationContext, newIds)

        Result.success()
    } catch (t: Throwable) {
        Timber.e(t, "NewEventsWorker.error")
        Result.retry()
    }
}