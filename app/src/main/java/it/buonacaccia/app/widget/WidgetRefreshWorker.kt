package it.buonacaccia.app.widget

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.EventsRepository
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override suspend fun doWork(): Result {
        return try {
            // Defensive timeout (avoids "endless" work)
            withTimeout(20_000) {
                val repo = EventsRepository(OkHttpClient())
                val fresh = repo.fetch(all = true)   // same method you already use elsewhere
                EventStore.upsertEvents(applicationContext, fresh)
                // update the widget
                UpcomingOpeningsWidget().updateAll(applicationContext)
            }
            Result.success()
        } catch (_: Throwable) {
            // Update the widget anyway to reflect the cache status.
            runCatching { UpcomingOpeningsWidget().updateAll(applicationContext) }
            Result.retry()
        }
    }
}