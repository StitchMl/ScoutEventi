package it.buonacaccia.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import it.buonacaccia.app.R
import it.buonacaccia.app.data.BcEvent

object Notifier {
    private const val CHANNEL_ID = "new_events"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.channel_new_events),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /** Single event notification, with notification ID = event id (if available) */
    fun notifyNewEvent(ctx: Context, ev: BcEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val title = ev.title
        val text = buildString {
            ev.region?.let { append("$it • ") }
            ev.startDate?.let { append(it) }
        }

        // Open detail page if available
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = ev.detailUrl.toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = androidx.core.app.PendingIntentCompat.getActivity(
            ctx, (ev.id ?: title.hashCode()).hashCode(), intent, 0, false
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // put your own icon
            .setContentTitle(title)
            .setContentText(text.takeIf { it.isNotBlank() })
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(ctx)
            .notify((ev.id ?: title.hashCode()).hashCode(), notif)
    }
}