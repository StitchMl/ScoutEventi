package it.buonacaccia.app

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.*
import it.buonacaccia.app.background.NewEventsWorker
import it.buonacaccia.app.background.SubscriptionsWorker
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.di.networkModule
import it.buonacaccia.app.notify.Notifier
import it.buonacaccia.app.ui.EventsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class App : Application(), Configuration.Provider {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        // --- Koin setup ---
        val appModule = module {
            single<Notifier> { Notifier }
            singleOf(::EventsRepository)
            viewModelOf(::EventsViewModel)
        }

        startKoin {
            androidLogger()
            androidContext(this@App)
            workManagerFactory()
            modules(listOf(networkModule, appModule))
        }

        Notifier.ensureChannel(this)

        // --- Worker Scheduling ---
        scheduleWorkers()

        Timber.d("Koin e WorkManager inizializzati correttamente")
    }

    private fun delayToNextTwoAm(): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(2).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    private fun scheduleWorkers() {
        val workManager = WorkManager.getInstance(this)

        // ✅ New events every 6 hours
        val newEventsWork = PeriodicWorkRequestBuilder<NewEventsWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // ✅ Reminder subscriptions once a day, at 02:00 local time
        val initialDelayMs = delayToNextTwoAm() // next 2:00 a.m.
        val subscriptionsWork = PeriodicWorkRequestBuilder<SubscriptionsWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // Plan uniquely: UPDATE replaces any old config.
        workManager.enqueueUniquePeriodicWork(
            "NewEventsWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            newEventsWork
        )

        workManager.enqueueUniquePeriodicWork(
            "SubscriptionsWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            subscriptionsWork
        )

        // (Optional) one shot "right away" at first start, then periodicals take care of it
        val bootNow = OneTimeWorkRequestBuilder<NewEventsWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            "NewEventsNow",
            ExistingWorkPolicy.KEEP,
            bootNow
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}