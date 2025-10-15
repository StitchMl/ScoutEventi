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

    private fun scheduleWorkers() {
        val workManager = WorkManager.getInstance(this)

        // ✅ Worker for new events every 6 hours
        val newEventsWork = PeriodicWorkRequestBuilder<NewEventsWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // ✅ Worker for enrollment reminder every 12 hours
        val subscriptionsWork = PeriodicWorkRequestBuilder<SubscriptionsWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // Plan ahead to avoid duplicates
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
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}