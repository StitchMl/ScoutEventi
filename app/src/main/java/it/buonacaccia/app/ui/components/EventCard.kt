package it.buonacaccia.app.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import it.buonacaccia.app.data.BcEvent
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EventCard(ev: BcEvent, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY)

    // helper to understand what we are showing
    fun looksLikeDate(s: String) = Regex("""\b\d{1,2}/\d{1,2}/\d{4}\b""").containsMatchIn(s)
    fun looksLikeMoney(s: String) = ('€' in s) || Regex("""\d+[.,]\d{2}""").containsMatchIn(s)

    // Normalize the quota/enrolled fields: if "enrolled" looks like a price, then it is the true quota.
    val feeDisplay: String? = when {
        ev.fee?.let { looksLikeMoney(it) } == true -> ev.fee
        ev.enrolled?.let { looksLikeMoney(it) } == true -> ev.enrolled
        else -> ev.fee
    }
    val deadlineDisplay: String? = ev.fee?.takeIf { looksLikeDate(it) }
    val enrolledDisplay: String? = ev.enrolled?.takeIf { !looksLikeMoney(it) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val i = Intent(Intent.ACTION_VIEW, ev.detailUrl.toUri())
                ctx.startActivity(i)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = ev.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(6.dp))

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

            // Show dates a single line "From ... to ..."
            val dateLine = buildString {
                ev.startDate?.let { append("Dal ${it.format(fmt)}") }
                ev.endDate?.let {
                    if (isNotEmpty()) append(" ")
                    append("al ${it.format(fmt)}")
                }
            }.ifBlank { null }
            dateLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            // Location (if any)
            ev.location?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            // Enrollment deadline (if "quota" is actually a date).
            deadlineDisplay?.let {
                Text("Scadenza iscrizioni: $it", style = MaterialTheme.typography.bodySmall)
            }

            // Share (price)
            feeDisplay?.takeIf { looksLikeMoney(it) }?.let {
                Text("Quota: $it", style = MaterialTheme.typography.bodySmall)
            }

            // Enrolled (only if it is not a price)
            enrolledDisplay?.takeIf { it.isNotBlank() }?.let {
                Text("Iscritti: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}