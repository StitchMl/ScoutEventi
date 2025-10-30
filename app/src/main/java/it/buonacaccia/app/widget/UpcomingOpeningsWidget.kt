package it.buonacaccia.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import androidx.glance.Image

// Top-level palette so it is visible everywhere
data class WidgetPalette(
    val bg: ColorProvider,
    val card: ColorProvider,
    val onBg: ColorProvider,
    val onCard: ColorProvider,
    val onCard2: ColorProvider,
    val divider: ColorProvider,
    val accent: ColorProvider
)

class UpcomingOpeningsWidget : GlanceAppWidget() {

    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val isDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        // Colors consistent with the app (accent #01BAEF)
        val palette = if (isDark) {
            WidgetPalette(
                bg = ColorProvider(Color(0x00000000)),         // transparent: leave the system border
                card = ColorProvider(Color(0xFF1B1D21)),
                onBg = ColorProvider(Color(0xFFEDE7F1)),
                onCard = ColorProvider(Color(0xFFEDE7F1)),
                onCard2 = ColorProvider(Color(0xFFB7B2B9)),
                divider = ColorProvider(Color(0x332C2F36)),
                accent = ColorProvider(Color(0xFF01BAEF))
            )
        } else {
            WidgetPalette(
                bg = ColorProvider(Color(0x00000000)),
                card = ColorProvider(Color(0xFFFFFFFF)),
                onBg = ColorProvider(Color(0xFF1B1C1E)),
                onCard = ColorProvider(Color(0xFF1B1C1E)),
                onCard2 = ColorProvider(Color(0xFF5C6066)),
                divider = ColorProvider(Color(0x1A000000)),
                accent = ColorProvider(Color(0xFF01BAEF))
            )
        }

        // Data from the cache
        val today = LocalDate.now()
        val events: List<BcEvent> = runBlocking { EventStore.cachedEventsFlow(context).first() }

        val upcoming = events
            .filter { e ->
                val open = e.subsOpenDate
                val end = e.endDate
                (open != null && !open.isBefore(today)) &&
                        (end == null || !end.isBefore(today))
            }
            .sortedBy { it.subsOpenDate }
            .take(30)

        // Wrapper that draws the rounded background as the *only* basic view
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(androidx.glance.ImageProvider(it.buonacaccia.app.R.drawable.bg_widget_round))
                .padding(12.dp) // internal padding on the rounded edge
        ) {
            // Widget content
            Column(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                // HEADER: app icon + title "Upcoming events"
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = androidx.glance.ImageProvider(
                            it.buonacaccia.app.R.drawable.ic_launcher_foreground_inset
                        ),
                        contentDescription = "Logo",
                        modifier = GlanceModifier
                            .width(18.dp)
                            .height(18.dp)
                            .cornerRadius(9.dp)
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "Prossimi eventi",
                        style = TextStyle(
                            color = palette.onBg,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(GlanceModifier.height(10.dp))

                if (upcoming.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(16.dp)
                            .background(palette.card)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nessun evento imminente",
                            style = TextStyle(color = palette.onCard2, fontSize = 13.sp)
                        )
                    }
                } else {
                    LazyColumn {
                        items(upcoming) { ev ->
                            val onClick = clickActionFor(context, ev)
                            EventRow(e = ev, palette = palette, onClick = onClick)
                            Spacer(GlanceModifier.height(8.dp))
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(palette.divider)
                            ) {}
                            Spacer(GlanceModifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    /** If the URL is valid -> browser; otherwise, open app with precompiled filter. */
    private fun clickActionFor(ctx: Context, e: BcEvent): Action {
        val url = e.detailUrl
        return if (url.startsWith("http", ignoreCase = true)) {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            actionStartActivity(intent)
        } else {
            val intent = Intent(ctx, MainActivity::class.java)
                .putExtra("prefill_query", e.title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            actionStartActivity(intent)
        }
    }
}

/** Calendar/Gmail style event row */
@SuppressLint("RestrictedApi")
@Composable
private fun EventRow(
    e: BcEvent,
    palette: WidgetPalette,
    onClick: Action
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(palette.card)
            .cornerRadius(16.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val open = e.subsOpenDate!!
        val day = open.format(DateTimeFormatter.ofPattern("dd"))
        val mon = open.format(
            DateTimeFormatter.ofPattern("LLL", Locale.ITALIAN)
        ).uppercase(Locale.ITALIAN)

        // Stable "per-event" color
        val lane = colorFromKey(e.id ?: e.title)
        val laneProvider = ColorProvider(lane)

        // Colored bar on the left
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .fillMaxHeight()
                .background(laneProvider)
        ) {}

        Spacer(GlanceModifier.width(10.dp))

        // Pill data
        Column(
            modifier = GlanceModifier
                .width(56.dp)
                .background(ColorProvider(Color(0x14000000)))
                .cornerRadius(12.dp)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day,
                style = TextStyle(
                    color = palette.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = mon,
                style = TextStyle(
                    color = palette.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(GlanceModifier.width(12.dp))

        // Title + location
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = e.title,
                style = TextStyle(
                    color = palette.onCard,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (!e.location.isNullOrBlank()) {
                Text(
                    text = e.location,
                    style = TextStyle(
                        color = palette.onCard2,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

private fun colorFromKey(key: String): Color {
    val h = abs(key.hashCode())
    val palette = listOf(
        0xFF01BAEF, 0xFF4CAF50, 0xFFFFA000, 0xFF7E57C2, 0xFFE91E63, 0xFF26C6DA, 0xFFEC407A
    ).map { Color(it.toInt()) }
    return palette[h % palette.size]
}

class UpcomingOpeningsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingOpeningsWidget()
}