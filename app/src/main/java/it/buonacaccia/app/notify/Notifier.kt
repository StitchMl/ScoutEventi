package it.buonacaccia.app.notify

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import it.buonacaccia.app.R
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.ui.MainActivity
import timber.log.Timber

object Notifier {
    private const val CHANNEL_NEW_EVENTS = "new_events"
    private const val CHANNEL_SUB_REMINDER = "sub_reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val newEvents = NotificationChannel(
            CHANNEL_NEW_EVENTS,
            "Nuovi eventi BuonaCaccia",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifiche per nuovi eventi pubblicati su BuonaCaccia"
            enableLights(true)
            lightColor = "#01BAEF".toColorInt()
            enableVibration(true)
        }

        val reminders = NotificationChannel(
            CHANNEL_SUB_REMINDER,
            "Promemoria iscrizioni",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifiche per ricordare l'apertura o chiusura delle iscrizioni"
            enableLights(true)
            lightColor = "#ED254E".toColorInt()
            enableVibration(true)
        }

        manager.createNotificationChannel(newEvents)
        manager.createNotificationChannel(reminders)
        Timber.d("Notifier.ensureChannel: canali aggiornati")
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun notifyNewEvent(context: Context, event: BcEvent) {
        if (!notificationsAllowed(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_event_id", event.id)
            putExtra("open_event_title", event.title)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventRequestCode(event, "new"),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_NEW_EVENTS)
            .setSmallIcon(R.drawable.ic_new_event)
            .setContentTitle("Nuovo evento disponibile!")
            .setContentText(event.title)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${event.title}\n${event.region ?: "Regione sconosciuta"}"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor("#01BAEF".toColorInt())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context)
            .notify(notificationId("new", EventStore.eventKeyOf(event)), builder.build())
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun notifySubscriptionReminder(context: Context, event: BcEvent, tag: String) {
        if (!notificationsAllowed(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_event_id", event.id)
            putExtra("open_event_title", event.title)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventRequestCode(event, "reminder:$tag"),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (title: String, text: String, icon: Int, color: String) = when (tag) {
            "OPEN-7" -> Quadruple(
                "Tra una settimana aprono le iscrizioni",
                "L'evento \"${event.title}\" aprira le iscrizioni tra 7 giorni.",
                R.drawable.ic_reminder_open,
                "#4CAF50"
            )
            "OPEN-1" -> Quadruple(
                "Domani aprono le iscrizioni!",
                "L'evento \"${event.title}\" apre domani.",
                R.drawable.ic_reminder_open,
                "#4CAF50"
            )
            "OPEN" -> Quadruple(
                "Iscrizioni aperte!",
                "L'evento \"${event.title}\" e ora disponibile per l'iscrizione.",
                R.drawable.ic_reminder_open_today,
                "#2E7D32"
            )
            "CLOSE" -> Quadruple(
                "Ultimi giorni per iscriversi!",
                "L'evento \"${event.title}\" chiude presto le iscrizioni.",
                R.drawable.ic_reminder_close,
                "#F44336"
            )
            else -> Quadruple(
                "Promemoria evento",
                "Controlla l'evento \"${event.title}\" su BuonaCaccia.",
                R.drawable.ic_new_event,
                "#999999"
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_SUB_REMINDER)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(color.toColorInt())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(context)
            .notify(notificationId("reminder", EventStore.eventKeyOf(event), tag), builder.build())
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private fun notificationsAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun eventRequestCode(event: BcEvent, suffix: String): Int =
        notificationId(EventStore.eventKeyOf(event), suffix)

    private fun notificationId(vararg parts: String): Int =
        parts.joinToString("|").hashCode()
}
