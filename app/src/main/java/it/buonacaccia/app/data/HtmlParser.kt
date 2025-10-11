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
            val locality = getCell(index, tds, "Località")?.text()?.trim()?.ifBlank { null }
            val iscritti = getCell(index, tds, "Iscritti")?.text()?.trim()?.ifBlank { null }
            val state    = getCell(index, tds, "Stato")?.text()?.trim()?.ifBlank { null }

            BcEvent(
                id = id,
                type = tipo,
                title = titolo,
                region = regione,
                startDate = partenza,
                endDate = rientro,
                fee = quota,
                location = locality,
                enrolled = iscritti,
                status = state,
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

    /** Find the table that contains the expected headers. */
    private fun findEventsTable(doc: Document): Element? {
        val tables = doc.select("table")
        return tables.firstOrNull { table ->
            val headers = table.select("th").map { it.text().trim().lowercase(Locale.ITALY) }
            listOf("titolo", "regione", "partenza", "rientro").all { h -> headers.any { it.contains(h) } }
        } ?: doc.selectFirst("table")
    }

    /** Returns the header line that contains "Title". */
    private fun headerRow(table: Element): Element? {
        val allRows = table.select("thead tr, tr")
        // prefer the line that contains a th "Title"
        return allRows.firstOrNull { row ->
            row.select("th").any { it.text().trim().equals("Titolo", ignoreCase = true) }
        } ?: allRows.firstOrNull { it.select("th").isNotEmpty() }
    }

    /** Create the NameColumn → Index map starting ONLY from the identified header row. */
    private fun headerMapFromRow(row: Element): Map<String, Int> {
        val ths = row.select("th")
        // For logging, we create a textual representation of the found headers
        val headerTextsForLogging = ths.mapIndexed { index, element -> "$index:'${element.text().trim()}'" }
        Timber.d("Headings available for mapping: %s", headerTextsForLogging)

        fun idx(vararg keys: String): Int? {
            // Search for a match for each key provided (e.g., "Location," "Locality")
            for (k in keys) {
                // indexOfFirst finds the index of the first element that satisfies the condition
                val i = ths.indexOfFirst { it.text().trim().contains(k, ignoreCase = true) }

                // If the index is valid (>= 0), it means that we have found a match
                if (i >= 0) {
                    // Log indicating which key produced a match and at which index
                    Timber.d("Found match for key '%s' at index %d (text: '%s')", k, i, ths[i].text().trim())
                    return i
                }
            }
            // If the loop ends without finding anything, log failure
            Timber.w("No matches found for keys: %s", keys.joinToString())
            return null
        }

        // Map creation remains unchanged, but now the 'idx' function will print logs
        val indexMap = mapOf(
            "Tipo"     to (idx("Tipo") ?: -1),
            "Titolo"   to (idx("Titolo") ?: 0),
            "Regione"  to (idx("Regione") ?: -1),
            "Partenza" to (idx("Partenza") ?: -1),
            "Rientro"  to (idx("Rientro") ?: -1),
            "Quota"    to (idx("Quota") ?: -1),
            "Località" to (idx("Località", "Localita") ?: -1),
            "Iscritti" to (idx("Iscritti") ?: -1),
            "Stato"    to (idx("Stato") ?: -1)
        )

        // Final log with the resulting map
        Timber.d("Column index map created: %s", indexMap)

        return indexMap
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