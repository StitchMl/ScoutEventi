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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first

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

            // 1) Update cache remotely
            val latest = repo.fetch(all = true)
            EventStore.upsertEvents(applicationContext, latest)

            // 2) Purge events with closed enrollment
            EventStore.purgeClosed(applicationContext, LocalDate.now())

            // 3) Upload current cache + set reminders already sent
            val events = EventStore.cachedEventsFlow(applicationContext).first()
            val sent = EventStore.sentRemindersFlow(applicationContext).first().toMutableSet()

            val today = LocalDate.now()
            val perm = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            // 4) Calculates and sends reminders avoiding duplicates
            for (ev in events) {
                val startDate = ev.startDate ?: continue
                val diff = ChronoUnit.DAYS.between(today, startDate)

                val tag = when (diff) {
                    1L -> "OPEN-1"
                    0L -> "OPEN"
                    -3L -> "CLOSE"
                    else -> null
                } ?: continue

                val key = "${ev.id}|$today|$tag"
                if (key !in sent && perm) {
                    try {
                        notifier.notifySubscriptionReminder(applicationContext, ev, tag)
                        sent += key
                    } catch (se: SecurityException) {
                        Timber.e(se, "SecurityException while notifying %s", ev.id)
                    }
                }
            }

            if (sent.isNotEmpty()) {
                EventStore.addSentReminder(applicationContext, sent)
            }

            Timber.d("SubscriptionsWorker completato (${events.size} eventi in cache)")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Errore in SubscriptionsWorker")
            Result.retry()
        }
    }
}