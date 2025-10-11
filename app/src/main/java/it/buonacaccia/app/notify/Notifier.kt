package it.buonacaccia.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.buonacaccia.app.R
import it.buonacaccia.app.ui.MainActivity

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
            enableLights(true)
            lightColor = Color.MAGENTA
        }
        mgr.createNotificationChannel(ch)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun notifyNewEvents(context: Context, titles: List<String>) {
        if (titles.isEmpty()) return
        ensureChannel(context)

        // Tap -> after app
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = if (titles.size == 1) titles.first() else "${titles.size} nuovi eventi"
        val inbox = NotificationCompat.InboxStyle().also { style ->
            titles.take(5).forEach { style.addLine(it) }
            if (titles.size > 5) style.addLine("…")
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background) // put a small icon in mipmap/drawable
            .setContentTitle("BuonaCaccia")
            .setContentText(summary)
            .setStyle(inbox)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1001, notif)
    }
}