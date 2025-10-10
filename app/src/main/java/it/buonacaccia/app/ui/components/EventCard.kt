package it.buonacaccia.app.ui.components

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import it.buonacaccia.app.data.BcEvent
import java.time.format.DateTimeFormatter
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EventCard(ev: BcEvent, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY)

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
            Text(text = ev.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ev.region?.takeIf { it.isNotBlank() }?.let {
                    AssistChip(onClick = {}, label = { Text(it) })
                }
                ev.type?.takeIf { it.isNotBlank() }?.let {
                    AssistChip(onClick = {}, label = { Text(it) }, colors = AssistChipDefaults.assistChipColors())
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

            ev.fee?.takeIf { it.isNotBlank() }?.let {
                Text("Quota: $it", style = MaterialTheme.typography.bodySmall)
            }

            ev.enrolled?.takeIf { it.isNotBlank() }?.let {
                Text("Iscritti: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}