package it.buonacaccia.app.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.buonacaccia.app.R
import it.buonacaccia.app.ui.MainActivity
import it.buonacaccia.app.update.AppUpdateManager

class UpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        AppUpdateManager.findOrDownloadLatest(
            context = applicationContext,
            suppressFailures = false,
        )?.let(::notifyUpdateReady)
        Result.success()
    }.getOrElse { Result.retry() }

    private fun notifyUpdateReady(update: it.buonacaccia.app.update.PreparedUpdate) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Aggiornamenti app", NotificationManager.IMPORTANCE_DEFAULT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra("open_update_install", true)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(applicationContext).notify(
            9001,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_new_event)
                .setContentTitle("ScoutEventi ${update.versionName} pronto")
                .setContentText("Tocca per confermare l’installazione")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "app_updates"
    }
}
