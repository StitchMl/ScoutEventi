package it.buonacaccia.app.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.data.BuonaCacciaScopes
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.data.FetchSafety
import it.buonacaccia.app.data.NotificationRuleEngine
import it.buonacaccia.app.notify.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class SubscriptionsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val repo by inject<EventsRepository>()
    private val notifier by inject<Notifier>()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("SubscriptionsWorker avviato")

            val subscribed = EventStore.subscribedIdsFlow(applicationContext).first()
            if (subscribed.isEmpty()) {
                Timber.d("SubscriptionsWorker: nessun evento seguito, skip rete")
                return@withContext Result.success()
            }

            val cachedSnapshot = EventStore.cachedEventsFlow(applicationContext).first()
            val minimumExpectedCount = FetchSafety.minimumExpectedCountForFullDataset(cachedSnapshot.size)
            val subscribedEvents = cachedSnapshot.filter { ev ->
                EventStore.eventKeyOf(ev) in subscribed
            }
            val latest = fetchLatest(subscribedEvents, subscribed, minimumExpectedCount)
            EventStore.upsertEvents(applicationContext, latest)

            EventStore.purgeClosed(applicationContext, LocalDate.now())

            val events = EventStore.cachedEventsFlow(applicationContext).first()
            val notificationPreferences = EventStore.notificationPreferences(applicationContext)
            val sent = EventStore.sentRemindersFlow(applicationContext).first()

            val toRemind = events.filter { ev ->
                EventStore.eventKeyOf(ev) in subscribed &&
                    NotificationRuleEngine.shouldNotify(ev, notificationPreferences)
            }

            val newReminderKeys = sendReminders(toRemind, sent, LocalDate.now())

            if (newReminderKeys.isNotEmpty()) {
                EventStore.addSentReminder(applicationContext, newReminderKeys)
            }

            Timber.d("SubscriptionsWorker completato (${events.size} eventi in cache)")
            Result.success()
        } catch (ce: CancellationException) {
            Timber.i("SubscriptionsWorker cancelled")
            throw ce
        } catch (e: Exception) {
            Timber.e(e, "Errore in SubscriptionsWorker")
            Result.retry()
        }
    }

    private suspend fun fetchLatest(
        subscribedEvents: List<BcEvent>,
        subscribedIds: Set<String>,
        minimumExpectedCount: Int?,
    ): List<BcEvent> {
        val enrich: (BcEvent) -> Boolean = { EventStore.eventKeyOf(it) in subscribedIds }
        if (subscribedEvents.isEmpty()) {
            Timber.w("SubscriptionsWorker: eventi seguiti non trovati in cache, full fetch")
            return repo.fetch(true, minimumExpectedCount = minimumExpectedCount, enrichPredicate = enrich)
        }
        val filters = BuonaCacciaScopes.regionFiltersOf(subscribedEvents.map { it.region })
        val unmapped = BuonaCacciaScopes.unmappedRegionsOf(subscribedEvents.map { it.region })
        if (filters.isEmpty() || unmapped.isNotEmpty()) {
            Timber.w("SubscriptionsWorker: regioni non mappabili %s, full fetch", unmapped)
            return repo.fetch(true, minimumExpectedCount = minimumExpectedCount, enrichPredicate = enrich)
        }
        return try {
            repo.fetchByFilters(filters, true, enrichPredicate = enrich)
        } catch (ce: CancellationException) {
            throw ce
        } catch (error: Throwable) {
            Timber.w(error, "SubscriptionsWorker: filtered fetch failed, full fetch fallback")
            repo.fetch(true, minimumExpectedCount = minimumExpectedCount, enrichPredicate = enrich)
        }
    }

    private fun sendReminders(events: List<BcEvent>, sent: Set<String>, today: LocalDate): Set<String> {
        if (!canPostNotifications()) return emptySet()
        val beforeNine = LocalTime.now().isBefore(LocalTime.of(9, 0))
        return events.mapNotNull { event -> sendReminder(event, sent, today, beforeNine) }.toSet()
    }

    private fun sendReminder(event: BcEvent, sent: Set<String>, today: LocalDate, beforeNine: Boolean): String? {
        val tag = reminderTag(event, today, beforeNine) ?: return null
        val key = "${EventStore.eventKeyOf(event)}|$today|$tag"
        if (key in sent) return null
        return try {
            notifier.notifySubscriptionReminder(applicationContext, event, tag)
            key
        } catch (error: SecurityException) {
            Timber.e(error, "SecurityException while notifying %s", event.id)
            null
        }
    }

    private fun reminderTag(event: BcEvent, today: LocalDate, beforeNine: Boolean): String? {
        val openingDays = event.subsOpenDate?.let { ChronoUnit.DAYS.between(today, it) }
        val closingDays = event.subsCloseDate?.let { ChronoUnit.DAYS.between(today, it) }
        return when {
            openingDays == 7L -> "OPEN-7"
            openingDays == 1L -> "OPEN-1"
            openingDays == 0L && beforeNine -> "OPEN"
            closingDays == 1L -> "CLOSE"
            else -> null
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}
