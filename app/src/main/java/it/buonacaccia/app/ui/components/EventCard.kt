package it.buonacaccia.app.ui.components

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.Branch
import it.buonacaccia.app.data.EventStore
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun branchColor(branch: Branch?): Color = when (branch) {
    Branch.RS   -> Color(0xFFEF5350) // rosso
    Branch.EG   -> Color(0xFF66BB6A) // verde
    Branch.LC   -> Color(0xFFFFCA28) // giallo
    Branch.CAPI, null -> Color(0xFF8E24AA) // viola default
}

@Composable
fun EventCard(ev: BcEvent, modifier: Modifier = Modifier) {
    val tint = branchColor(ev.branch)
    val ctx = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY)
    val scope = rememberCoroutineScope()

    // Underwriting status
    val subscribedSet by EventStore.subscribedIdsFlow(ctx).collectAsState(initial = emptySet())
    val eventKey = EventStore.eventKeyOf(ev)
    val isSubscribed = eventKey in subscribedSet

    fun looksLikeDate(s: String) = Regex("""\b\d{1,2}/\d{1,2}/\d{4}\b""").containsMatchIn(s)
    fun looksLikeMoney(s: String) = ('€' in s) || Regex("""\d+[.,]\d{2}""").containsMatchIn(s)

    val feeDisplay: String? = when {
        ev.fee?.let { looksLikeMoney(it) } == true -> ev.fee
        ev.enrolled?.let { looksLikeMoney(it) } == true -> ev.enrolled
        else -> ev.fee
    }
    val deadlineDisplay: String? = ev.fee?.takeIf { looksLikeDate(it) }
    val enrolledDisplay: String? = ev.enrolled?.takeIf { !looksLikeMoney(it) }
    val color = when (ev.statusColor) {
        "green" -> Color(0xFF4CAF50)
        "yellow" -> Color(0xFFFFC107)
        "dual" -> Color(0xFF9C27B0)
        "red" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val i = Intent(Intent.ACTION_VIEW, ev.detailUrl.toUri())
                ctx.startActivity(i)
            }
            .border(2.dp, color, RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Title + "Follow" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ev.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                val activeColor = MaterialTheme.colorScheme.primary
                val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
                val activeIconColor = Color.White
                val inactiveIconColor = MaterialTheme.colorScheme.onSurfaceVariant

                // Color animations
                val bgColor by animateColorAsState(
                    targetValue = if (isSubscribed) activeColor else inactiveColor,
                    animationSpec = tween(durationMillis = 250)
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSubscribed) activeIconColor else inactiveIconColor,
                    animationSpec = tween(durationMillis = 250)
                )

                Surface(
                    onClick = {
                        scope.launch {
                            EventStore.setSubscribed(ctx, eventKey, !isSubscribed)
                        }
                    },
                    shape = CircleShape,
                    color = bgColor,
                    tonalElevation = if (isSubscribed) 4.dp else 0.dp,
                    shadowElevation = if (isSubscribed) 2.dp else 0.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSubscribed)
                                Icons.Filled.NotificationsActive
                            else
                                Icons.Filled.Notifications,
                            contentDescription = if (isSubscribed) "Seguito" else "Segui",
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            ev.type?.takeIf { it.isNotBlank() }?.let { t ->
                Surface(
                    color = tint.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = t,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = tint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ev.region?.takeIf { it.isNotBlank() }?.let {
                    AssistChip(onClick = {}, label = { Text(it) })
                }
                ev.type?.takeIf { it.isNotBlank() }?.let {
                    AssistChip(onClick = {}, label = { Text(it) })
                }
                ev.status?.takeIf { it.isNotBlank() }?.let {
                    AssistChip(onClick = {}, label = { Text(it) })
                }
            }

            Spacer(Modifier.height(6.dp))

            val dateLine = buildString {
                ev.startDate?.let { append("Dal ${it.format(fmt)}") }
                ev.endDate?.let {
                    if (isNotEmpty()) append(" ")
                    append("al ${it.format(fmt)}")
                }
            }.ifBlank { null }
            dateLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            ev.location?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            deadlineDisplay?.let {
                Text("Scadenza iscrizioni: $it", style = MaterialTheme.typography.bodySmall)
            }

            feeDisplay?.takeIf { looksLikeMoney(it) }?.let {
                Text("Quota: $it", style = MaterialTheme.typography.bodySmall)
            }

            enrolledDisplay?.takeIf { it.isNotBlank() }?.let {
                Text("Iscritti: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}