package it.buonacaccia.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import it.buonacaccia.app.background.NewEventsWorker
import it.buonacaccia.app.notify.Notifier
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Notification channel
        Notifier.ensureChannel(this)

        // WorkManager: checks for new events every 6 hours with available network
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workReq = PeriodicWorkRequestBuilder<NewEventsWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "new_events_checker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workReq
        )
    }
}