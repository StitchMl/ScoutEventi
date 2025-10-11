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

        // 1) find the table with the expected headers
        val table = findEventsTable(doc)
        if (table == null) {
            Timber.w("No suitable table found for events in the HTML.")
            return emptyList()
        }
        Timber.d("Found events table.")

        // 2) DATA rows: only direct children of the table, no nested <tr> / header
        val rows = (table.select("> tbody > tr") + table.select("> tr"))
            .distinct()
            .filter { row -> row.select("> th").isEmpty() && row.select("> td").isNotEmpty() }
        Timber.d("Found %d potential event rows.", rows.size)

        return rows.mapNotNull { tr ->
            // 3) cells: only direct children of the <tr>
            val cells = tr.select("> th, > td")
            if (cells.isEmpty()) return@mapNotNull null

            // 4) find the cell that contains the LINK to the event (it is ALWAYS the Title)
            val iTitle = cells.indexOfFirst {
                it.select("a[href]").any { a ->
                    val h = a.attr("href")
                    h.contains("event.aspx", ignoreCase = true) || h.contains("/event.aspx", ignoreCase = true)
                }
            }
            if (iTitle == -1) {
                Timber.w("Row skipped: could not find event link (title). Row HTML: %s", tr.html())
                return@mapNotNull null
            }
            Timber.d("Row link candidate: %s", cells.select("a[href]").joinToString { it.attr("href") })

            val link = cells[iTitle].selectFirst("a[href]") ?: return@mapNotNull null
            val title = link.text().trim()
            if (title.isBlank()) {
                Timber.w("Row skipped: event title is blank. Row HTML: %s", tr.html())
                return@mapNotNull null
            }

            val detailUrl = link.absUrl("href").ifBlank { baseUrl }
            val id = extractEventId(detailUrl)
            // 5) reading RELATIVE to positions with respect to the title (coincides with the structure you pasted)
            val typeText = cells.getOrNull(0)?.text()?.trim()?.ifBlank { null }          // "ROSS", "CapiLC", ...
            val region   = cells.getOrNull(iTitle + 1)?.text()?.trim()?.ifBlank { null } // "Piemonte"
            val start    = parseDate(cells.getOrNull(iTitle + 2)?.text())                // "23/10/2025"
            val end      = parseDate(cells.getOrNull(iTitle + 3)?.text())                // "28/10/2025"
            val fee      = cells.getOrNull(iTitle + 4)?.text()?.trim()?.ifBlank { null } // "20,00 €"
            val location = cells.getOrNull(iTitle + 5)?.text()?.trim()?.ifBlank { null } // "Ivrea (TO)"
            val enrolled = cells.getOrNull(iTitle + 6)?.text()?.trim()?.ifBlank { null } // "35 / 30"
            // after "Enrolled" there is a blank column, then "Status"
            val status   = cells.getOrNull(iTitle + 8)?.text()?.trim()?.ifBlank { null }
            // Branch from the first cell: search for the image branch_*.png
            val branch: Branch? = cells.getOrNull(0)
                ?.selectFirst("img[src]")
                ?.attr("src")
                ?.lowercase()
                ?.let { src ->
                    when {
                        "branch_rs" in src -> Branch.RS
                        "branch_eg" in src -> Branch.EG
                        "branch_lc" in src -> Branch.LC
                        else -> Branch.CAPI
                    }
                }

            val event = BcEvent(
                id = id,
                type = typeText,
                title = title,
                region = region,
                startDate = start,
                endDate = end,
                fee = fee,
                location = location,
                enrolled = enrolled,
                status = status,
                detailUrl = detailUrl,
                branch = branch
            )
            Timber.v("Parsed event: %s", event)
            event
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