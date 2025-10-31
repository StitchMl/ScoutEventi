package it.buonacaccia.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import it.buonacaccia.app.R
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// === Widget-local state keys ===
private val KEY_ONLY_FOLLOWED = booleanPreferencesKey("widget_only_followed")

// --- Palette ---
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

    // ✅ enable the local state of the widget
    override val stateDefinition = PreferencesGlanceStateDefinition

    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        val palette = if (isDark) {
            WidgetPalette(
                bg = ColorProvider(Color(0x00000000)),
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

        // “Slow” data read here (persistent)
        val today = LocalDate.now()
        val subscribedKeys = EventStore.subscribedIdsFlow(context).first()
        val cached = EventStore.cachedEventsFlow(context).first()

        // The onlyFollowed filter will be read by the STATE of the widget inside provideContent.
        provideContent {
            val prefs: Preferences = currentState()
            val onlyFollowedLocal = prefs[KEY_ONLY_FOLLOWED]
            // fallback to global preference if state is not yet initialized
                ?: false

            // We filter HERE, using the local (immediate) state.
            val upcoming = cached
                .filter { e ->
                    val open = e.subsOpenDate
                    val end = e.endDate
                    (open != null && !open.isBefore(today)) &&
                            (end == null || !end.isBefore(today)) &&
                            (!onlyFollowedLocal || (EventStore.eventKeyOf(e) in subscribedKeys))
                }
                .sortedBy { it.subsOpenDate }
                .take(30)

            WidgetContent(
                ctx = context,
                palette = palette,
                onlyFollowed = onlyFollowedLocal,
                upcoming = upcoming
            )
        }
    }
}

// ----- UI -----
@Composable
private fun WidgetContent(
    ctx: Context,
    palette: WidgetPalette,
    onlyFollowed: Boolean,
    upcoming: List<BcEvent>
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.bg_widget_round))
            .padding(12.dp)
    ) {
        Column(GlanceModifier.fillMaxWidth()) {

            HeaderRow(palette = palette, onlyFollowed = onlyFollowed)

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
                        text = if (onlyFollowed)
                            "Nessun evento seguito in apertura"
                        else
                            "Nessun evento imminente",
                        style = TextStyle(color = palette.onCard2, fontSize = 13.sp)
                    )
                }
            } else {
                LazyColumn {
                    items(upcoming) { ev ->
                        val onClick = clickActionFor(ctx, ev)
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

@SuppressLint("RestrictedApi")
@Composable
private fun HeaderRow(palette: WidgetPalette, onlyFollowed: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_launcher_foreground_inset),
            contentDescription = "Logo",
            modifier = GlanceModifier.width(18.dp).height(18.dp).cornerRadius(9.dp)
        )
        Spacer(GlanceModifier.width(8.dp))

        Text(
            text = "Prossimi eventi",
            style = TextStyle(color = palette.onBg, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight()
        )

        val iconColor = ColorProvider(
            day = Color(0xFF1C1B1F),
            night = Color(0xFFE8E8E8)
        )

        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = "Aggiorna",
            modifier = GlanceModifier.size(22.dp).clickable(actionRunCallback<RefreshAction>()),
            colorFilter = ColorFilter.tint(iconColor)
        )
        Spacer(GlanceModifier.width(10.dp))

        val pillBg = if (onlyFollowed)
            ColorProvider(day = Color(0xFF01BAEF), night = Color(0xFF4FC3F7))
        else
            ColorProvider(Color.Transparent)

        Box(
            modifier = GlanceModifier
                .cornerRadius(8.dp)
                .background(pillBg)
                .padding(3.dp)
                .clickable(actionRunCallback<ToggleFollowedAction>())
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_bell),
                contentDescription = if (onlyFollowed) "Solo seguiti: ON" else "Solo seguiti: OFF",
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
        Spacer(GlanceModifier.width(10.dp))

        Image(
            provider = ImageProvider(R.drawable.ic_widget_info),
            contentDescription = "Info",
            modifier = GlanceModifier.size(22.dp).clickable(actionRunCallback<OpenInfoAction>()),
            colorFilter = ColorFilter.tint(iconColor)
        )
    }
}

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
        val mon = open.format(DateTimeFormatter.ofPattern("LLL", Locale.ITALIAN)).uppercase(Locale.ITALIAN)

        val lane = colorFromKey(e.id ?: e.title)
        val laneProvider = ColorProvider(lane)

        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .fillMaxHeight()
                .background(laneProvider)
        ) {}

        Spacer(GlanceModifier.width(10.dp))

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
                style = TextStyle(color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = mon,
                style = TextStyle(color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            )
        }

        Spacer(GlanceModifier.width(12.dp))

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = e.title,
                style = TextStyle(color = palette.onCard, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            if (!e.location.isNullOrBlank()) {
                Text(
                    text = e.location,
                    style = TextStyle(color = palette.onCard2, fontSize = 12.sp)
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

// ===== Receiver =====
class UpcomingOpeningsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingOpeningsWidget()
}

// ===== Actions =====

/** Update via WorkManager and provide immediate feedback */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val req = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueue(req)
        UpcomingOpeningsWidget().update(context, glanceId)
    }
}

/** Toggle "followed only": IMMEDIATELY update the widget status and, at the same time, the global preference. */
class ToggleFollowedAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // read current local status
        val curLocal = androidx.glance.appwidget.state.getAppWidgetState(
            context, PreferencesGlanceStateDefinition, glanceId
        )[KEY_ONLY_FOLLOWED] ?: false

        val newVal = !curLocal

        // ✅ 1) update the STATE of the widget (return the modified preferences)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_ONLY_FOLLOWED] = newVal
            }
        }

        // ✅ 2) also updates the global (persistent) preference
        withContext(Dispatchers.IO) {
            EventStore.setWidgetOnlyFollowed(context, newVal)
        }

        // ✅ 3) rebuild the current widget IMMEDIATELY
        UpcomingOpeningsWidget().update(context, glanceId)
    }
}

/** Open Info */
class OpenInfoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("open_info", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** Tap on event row → opens the app and filters on that event */
private fun clickActionFor(ctx: Context, e: BcEvent): Action {
    val intent = Intent(ctx, MainActivity::class.java)
        .putExtra("open_event_id", e.id)
        .putExtra("open_event_title", e.title)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return actionStartActivity(intent)
}