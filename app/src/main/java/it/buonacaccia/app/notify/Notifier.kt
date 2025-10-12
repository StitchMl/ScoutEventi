package it.buonacaccia.app.notify

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
import it.buonacaccia.app.R
import it.buonacaccia.app.ui.MainActivity
import timber.log.Timber

object Notifier {
    const val CHANNEL_ID = "new_events_channel"
    private const val CHANNEL_NAME = "Nuovi eventi BuonaCaccia"
    private const val CHANNEL_DESC = "Alerts when new events appear"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
        }
        mgr.createNotificationChannel(ch)
    }

    // ------- NEW: permit check -------
    private fun canPostNotifications(ctx: Context): Boolean {
        // If the user has disabled system-wide notifications.
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false

        // From Android 13 (API 33) need runtime permission
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyNewEvents(context: Context, titles: List<String>) {
        if (titles.isEmpty()) return
        if (!canPostNotifications(context)) return  // no permission: going out

        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (titles.size == 1) titles.first()
        else titles.take(5).joinToString("\n").let {
            if (titles.size > 5) "$it\n…" else it
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background) // use a valid small icon of your own
            .setContentTitle("Nuovi eventi BuonaCaccia")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1001, notif)
        } catch (se: SecurityException) {
            Timber.tag("Notifier").w(se, "Permission notifications denied: skip sending")
        }
    }
}