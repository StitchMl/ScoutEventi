package it.buonacaccia.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.buonacaccia.app.background.NewEventsWorker
import it.buonacaccia.app.background.SubscriptionsWorker
import it.buonacaccia.app.data.EventsRepository
import it.buonacaccia.app.di.networkModule
import it.buonacaccia.app.notify.Notifier
import it.buonacaccia.app.ui.EventsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import timber.log.Timber
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class App : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

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

        val newEventsWork = PeriodicWorkRequestBuilder<NewEventsWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        val initialDelayMs = delayToNextTwoAm()
        val subscriptionsWork = PeriodicWorkRequestBuilder<SubscriptionsWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

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
