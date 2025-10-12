package it.buonacaccia.app.background

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class NewEventsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    // Simple Repo without Hilt to avoid extra dependencies in the Worker
    private val repo by lazy { EventsRepository(OkHttpClient()) }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val events = repo.fetch(all = true)
            val current = events.mapNotNull { it.id }.toSet()
            if (current.isEmpty()) return@withContext Result.success()

            val known = EventStore.getKnownIds(applicationContext)
            val newIds = current - known

            if (newIds.isNotEmpty()) {
                // complete new events
                val newEvents = events.filter { it.id in newIds }

                // ⬇️ load favorite types from DataStore
                val preferredTypes = EventStore.currentNotifyTypes(applicationContext)

                // ⬇️ If you have no preferences, notify everything; otherwise only the types you choose
                val filtered = if (preferredTypes.isEmpty()) {
                    newEvents
                } else {
                    newEvents.filter { ev ->
                        val t = ev.type?.trim().orEmpty()
                        t.isNotEmpty() && preferredTypes.contains(t)
                    }
                }

                if (filtered.isNotEmpty()) {
                    Notifier.notifyNewEvents(
                        applicationContext,
                        filtered.map { it.title }
                    )
                    EventStore.saveKnownIds(applicationContext, known + filtered.mapNotNull { it.id }.toSet())
                } else {
                    // no notification, but still we save new IDs so they are not renotified
                    EventStore.saveKnownIds(applicationContext, known + newIds)
                }
            } else if (known.isEmpty()) {
                // first startup: saves the current state without notifying
                EventStore.saveKnownIds(applicationContext, current)
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}