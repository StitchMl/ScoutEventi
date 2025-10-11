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
                // titles of the new ones (show the first 5)
                val titles = events.filter { it.id in newIds }.map { it.title }
                Notifier.notifyNewEvents(applicationContext, titles)
                EventStore.saveKnownIds(applicationContext, known + newIds)
            } else if (known.isEmpty()) {
                // First run: we save the current state without notifying
                EventStore.saveKnownIds(applicationContext, current)
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}