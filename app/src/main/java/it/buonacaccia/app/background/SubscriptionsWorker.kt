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
import java.time.LocalTime

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
            val subscribed = EventStore.subscribedIdsFlow(applicationContext).first()

            // Consider ONLY those events that are subscribed
            val toRemind = events.filter { ev ->
                EventStore.eventKeyOf(ev) in subscribed
            }

            val today = LocalDate.now()
            val perm = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            // 4) Calculates and sends reminders avoiding duplicates
            val now = LocalTime.now()
            val before9 = now.isBefore(LocalTime.of(9, 0))

            for (ev in toRemind) {
                val open = ev.subsOpenDate
                val close = ev.subsCloseDate

                val tag: String? = when {
                    // 🆕 One week before the opening
                    open != null && ChronoUnit.DAYS.between(today, open) == 7L -> "OPEN-7"

                    // Day before opening
                    open != null && ChronoUnit.DAYS.between(today, open) == 1L -> "OPEN-1"

                    // Opening day itself, only before 09:00 am
                    open != null && ChronoUnit.DAYS.between(today, open) == 0L && before9 -> "OPEN"

                    // Day before closing
                    close != null && ChronoUnit.DAYS.between(today, close) == 1L -> "CLOSE"

                    else -> null
                }

                if (tag == null) continue

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