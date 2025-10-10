package it.buonacaccia.app.data

import android.os.Build
import androidx.annotation.RequiresApi
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object HtmlParser {

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY),
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ITALY)
    )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun parseEvents(html: String, baseUrl: String): List<BcEvent> {
        val doc = Jsoup.parse(html, baseUrl)
        val table = findEventsTable(doc) ?: return emptyList()

        val header = headerRow(table) ?: return emptyList()
        val index = headerMapFromRow(header)

        Timber.d("Events table header -> %s",
            header.select("th").joinToString(" | ") { it.text().trim() })
        Timber.d("Column index map -> %s", index.toString())

        val rows = table.select("tbody tr").ifEmpty { table.select("tr").drop(table.indexOf(header) + 1) }

        return rows.mapNotNull { tr ->
            val tds = tr.select("td")
            if (tds.isEmpty()) return@mapNotNull null

            val titoloCell = getCell(index, tds, "Titolo") ?: return@mapNotNull null
            val titoloLink = titoloCell.selectFirst("a")
            val titolo = (titoloLink?.text() ?: titoloCell.text()).trim()
            if (titolo.isBlank()) return@mapNotNull null

            val detailUrl = titoloLink?.absUrl("href")?.takeIf { it.isNotBlank() } ?: baseUrl
            val id = extractEventId(detailUrl)

            val tipo     = getCell(index, tds, "Tipo")?.text()?.trim()
            val regione  = getCell(index, tds, "Regione")?.text()?.trim()?.ifBlank { null }
            val partenza = parseDate(getCell(index, tds, "Partenza")?.text())
            val rientro  = parseDate(getCell(index, tds, "Rientro")?.text())
            val quota    = getCell(index, tds, "Quota")?.text()?.trim()?.ifBlank { null }
            val localita = getCell(index, tds, "Località")?.text()?.trim()?.ifBlank { null }
            val iscritti = getCell(index, tds, "Iscritti")?.text()?.trim()?.ifBlank { null }
            val stato    = getCell(index, tds, "Stato")?.text()?.trim()?.ifBlank { null }

            BcEvent(
                id = id,
                type = tipo,            // ← Tipo/Unità (LC, EG, RS/ROSS, Capi…)
                title = titolo,         // ← sempre dalla colonna “Titolo”
                region = regione,
                startDate = partenza,
                endDate = rientro,
                fee = quota,
                location = localita,
                enrolled = iscritti,
                status = stato,
                detailUrl = detailUrl
            )
        }
    }

    private fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (fmt in dateFormats) {
            runCatching { return LocalDate.parse(s, fmt) }.onFailure { /* try next */ }
        }
        return null
    }

    /** Trova la tabella che contiene gli header attesi. */
    private fun findEventsTable(doc: Document): Element? {
        val tables = doc.select("table")
        return tables.firstOrNull { table ->
            val headers = table.select("th").map { it.text().trim().lowercase(Locale.ITALY) }
            listOf("titolo", "regione", "partenza", "rientro").all { h -> headers.any { it.contains(h) } }
        } ?: doc.selectFirst("table")
    }

    /** Restituisce la riga header che contiene “Titolo”. */
    private fun headerRow(table: Element): Element? {
        val allRows = table.select("thead tr, tr")
        // preferisci la riga che contiene un th “Titolo”
        return allRows.firstOrNull { row ->
            row.select("th").any { it.text().trim().equals("Titolo", ignoreCase = true) }
        } ?: allRows.firstOrNull { it.select("th").isNotEmpty() }
    }

    /** Crea la mappa NomeColonna → indice partendo SOLO dalla riga header individuata. */
    private fun headerMapFromRow(row: Element): Map<String, Int> {
        val ths = row.select("th")
        fun idx(vararg keys: String): Int? {
            // cerca per contains su ognuna delle varianti
            for (k in keys) {
                val i = ths.indexOfFirst { it.text().trim().contains(k, ignoreCase = true) }
                if (i >= 0) return i
            }
            return null
        }
        return mapOf(
            "Tipo"     to (idx("Tipo") ?: -1),
            "Titolo"   to (idx("Titolo") ?: 0), // Titolo DEVE esserci
            "Regione"  to (idx("Regione") ?: -1),
            "Partenza" to (idx("Partenza") ?: -1),
            "Rientro"  to (idx("Rientro") ?: -1),
            "Quota"    to (idx("Quota") ?: -1),
            "Località" to (idx("Località", "Localita") ?: -1),
            "Iscritti" to (idx("Iscritti") ?: -1),
            "Stato"    to (idx("Stato") ?: -1)
        )
    }

    private fun getCell(map: Map<String, Int>, tds: List<Element>, key: String): Element? {
        val idx = map[key] ?: -1
        return if (idx in tds.indices) tds[idx] else null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun extractEventId(url: String): String? {
        // es: https://buonacaccia.net/event.aspx?e=12345
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return null
        val params = q.split('&').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], StandardCharsets.UTF_8) else null
        }.toMap()
        return params["e"]
    }
}