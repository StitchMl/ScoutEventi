package it.buonacaccia.app.background

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SubscriptionsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val repo by inject<EventsRepository>()
    private val notifier by inject<Notifier>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("SubscriptionsWorker avviato")

            // We get all available events
            val events = repo.fetch(all = true)

            val today = LocalDate.now()
            for (ev in events) {
                val startDate = ev.startDate ?: continue

                val diff = ChronoUnit.DAYS.between(today, startDate)
                when (diff) {
                    1L -> notifier.notifySubscriptionReminder(applicationContext, ev, "OPEN-1")
                    0L -> notifier.notifySubscriptionReminder(applicationContext, ev, "OPEN")
                    -3L -> notifier.notifySubscriptionReminder(applicationContext, ev, "CLOSE")
                }
            }

            Timber.d("SubscriptionsWorker completato (${events.size} eventi analizzati)")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Errore in SubscriptionsWorker")
            Result.retry()
        }
    }
}