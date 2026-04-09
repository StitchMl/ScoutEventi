package it.buonacaccia.app.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.data.BuonaCacciaScopes
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.data.FetchSafety
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
            val regionFilters = BuonaCacciaScopes.regionFiltersOf(subscribedEvents.map { it.region })
            val unmappedRegions = BuonaCacciaScopes.unmappedRegionsOf(subscribedEvents.map { it.region })
            val canUseRegionFilters =
                subscribedEvents.isNotEmpty() &&
                    regionFilters.isNotEmpty() &&
                    unmappedRegions.isEmpty()

            if (subscribedEvents.isEmpty()) {
                Timber.w("SubscriptionsWorker: eventi seguiti non trovati in cache, full fetch")
            } else if (unmappedRegions.isNotEmpty()) {
                Timber.w(
                    "SubscriptionsWorker: regioni non mappabili %s, full fetch",
                    unmappedRegions
                )
            }

            val latest = if (canUseRegionFilters) {
                try {
                    repo.fetchByFilters(
                        filters = regionFilters,
                        all = true,
                        enrichPredicate = { ev -> EventStore.eventKeyOf(ev) in subscribed }
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (error: Throwable) {
                    Timber.w(error, "SubscriptionsWorker: filtered fetch failed, full fetch fallback")
                    repo.fetch(
                        all = true,
                        minimumExpectedCount = minimumExpectedCount,
                        enrichPredicate = { ev -> EventStore.eventKeyOf(ev) in subscribed }
                    )
                }
            } else {
                repo.fetch(
                    all = true,
                    minimumExpectedCount = minimumExpectedCount,
                    enrichPredicate = { ev -> EventStore.eventKeyOf(ev) in subscribed }
                )
            }
            EventStore.upsertEvents(applicationContext, latest)

            EventStore.purgeClosed(applicationContext, LocalDate.now())

            val events = EventStore.cachedEventsFlow(applicationContext).first()
            val sent = EventStore.sentRemindersFlow(applicationContext).first().toMutableSet()
            val newReminderKeys = mutableSetOf<String>()

            val toRemind = events.filter { ev ->
                EventStore.eventKeyOf(ev) in subscribed
            }

            val today = LocalDate.now()
            val perm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

            val now = LocalTime.now()
            val before9 = now.isBefore(LocalTime.of(9, 0))

            for (ev in toRemind) {
                val open = ev.subsOpenDate
                val close = ev.subsCloseDate

                val tag: String? = when {
                    open != null && ChronoUnit.DAYS.between(today, open) == 7L -> "OPEN-7"
                    open != null && ChronoUnit.DAYS.between(today, open) == 1L -> "OPEN-1"
                    open != null && ChronoUnit.DAYS.between(today, open) == 0L && before9 -> "OPEN"
                    close != null && ChronoUnit.DAYS.between(today, close) == 1L -> "CLOSE"
                    else -> null
                }

                if (tag == null) continue

                val key = "${EventStore.eventKeyOf(ev)}|$today|$tag"
                if (key !in sent && perm) {
                    try {
                        notifier.notifySubscriptionReminder(applicationContext, ev, tag)
                        sent += key
                        newReminderKeys += key
                    } catch (se: SecurityException) {
                        Timber.e(se, "SecurityException while notifying %s", ev.id)
                    }
                }
            }

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
}
