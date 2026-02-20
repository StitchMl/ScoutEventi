package it.buonacaccia.app.data

import android.os.Build
import androidx.annotation.RequiresApi
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import timber.log.Timber
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
                it.select("a[href]").any { a -> isEventLink(a.attr("href")) }
            }
            if (iTitle == -1) {
                return@mapNotNull null
            }
            Timber.d("Row link candidate: %s", cells.select("a[href]").joinToString { it.attr("href") })

            val link = cells[iTitle].selectFirst("a[href]") ?: return@mapNotNull null
            val title = link.text().trim()
            if (title.isBlank()) {
                return@mapNotNull null
            }

            val detailUrl = link.absUrl("href").ifBlank { baseUrl }
            val id = extractEventId(detailUrl)
            // 5) reading RELATIVE to positions with respect to the title (coincides with the structure you pasted)
            val typeText = cells.getOrNull(0)?.text()?.trim()?.ifBlank { null }          // "ROSS", "CapiLC", ...
            var region = cells.getOrNull(iTitle + 1)?.text()?.trim()?.ifBlank { null }

            // Normalize known abbreviations
            region = when (region?.lowercase(Locale.ROOT)) {
                "vda", "val d'aosta", "valdaosta", "valle d’aosta", "valle d'aosta" -> "Valle d'Aosta"
                "emiro", "emilia romagna", "emilia-romagna" -> "Emilia-Romagna"
                "taa" -> "Trentino Alto Adige"
                "fvg" -> "Friuli Venezia Giulia"
                else -> region
            }
            val start    = parseDate(cells.getOrNull(iTitle + 2)?.text())                // "23/10/2025"
            if (start?.isBefore(LocalDate.now()) == true) {
                return@mapNotNull null // Skip event if it is in the past
            }
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

            val effectiveType = typeText ?: when (branch) {
                Branch.RS -> "RS"
                Branch.EG -> "EG"
                Branch.LC -> "LC"
                Branch.CAPI -> "CAPI"
                null -> null
            }

            // Look for the status image (light_*.png) in the whole row
            val statusImgSrc = cells
                .select("img[src]")
                .map { it.attr("src").lowercase(Locale.ROOT) }
                .firstOrNull { it.contains("light_") }

            val statusColor = when {
                statusImgSrc?.contains("light_green") == true -> "green"   // many places
                statusImgSrc?.contains("light_yellow") == true -> "yellow" // almost full
                statusImgSrc?.contains("light_dual") == true -> "dual"     // waiting list
                statusImgSrc?.contains("light_red") == true -> "red"       // registrations closed
                else -> null
            }

            val event = BcEvent(
                id = id,
                type = effectiveType,
                title = title,
                region = region,
                startDate = start,
                endDate = end,
                fee = fee,
                location = location,
                enrolled = enrolled,
                status = status,
                statusColor = statusColor,
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

    private fun isEventLink(href: String): Boolean {
        val h = href.lowercase(Locale.ROOT)
        if ("event.aspx" in h) return true
        // rewrite style: /event?e=123
        if (h.contains("/event") && h.contains("e=")) return true
        // path style (if it ever arrives): /event/12345
        if (Regex("/event/\\d+").containsMatchIn(h)) return true
        return false
    }

    /** Find the table that contains the expected headers. */
    private fun findEventsTable(doc: Document): Element? {
        // ✅ Stable hook (present in the current list)
        doc.selectFirst("table#MainContent_EventsGridView")?.let { return it }

        // Fallback: any table with expected headers
        val tables = doc.select("table")
        return tables.firstOrNull { table ->
            val headers = table.select("th")
                .map { it.text().trim().lowercase(Locale.ITALY) }
            listOf("titolo", "regione", "partenza", "rientro")
                .all { h -> headers.any { it.contains(h) } }
        } ?: doc.selectFirst("table")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun extractEventId(url: String): String? {
        val q = url.substringAfter('?', "")
        if (q.isNotEmpty()) {
            val params = q.split('&').mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) parts[0].lowercase(Locale.ROOT) to URLDecoder.decode(parts[1], "UTF-8") else null
            }.toMap()
            params["e"]?.let { return it }
        }
        // fallback: search for e=123 throughout the entire string
        Regex("(?i)[?&]e=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        // fallback path: /event/123
        Regex("(?i)/event/(\\d+)").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun parseSubscriptions(html: String): SubsWindow {
        val doc = Jsoup.parse(html)
        fun grab(id: String): LocalDate? = parseDate(doc.selectFirst("#$id")?.text())

        var open  = grab("MainContent_EventFormView_lbSubsFrom")
        var close = grab("MainContent_EventFormView_lbSubsTo")
        var seats = doc.selectFirst("#MainContent_EventFormView_lbSeats")?.text()?.trim()
        var taken = doc.selectFirst("#MainContent_EventFormView_lbTaken")?.text()?.trim()

        // ✅ Textual fallback (more resilient to ID/markup changes)
        val text = doc.text()

        if (open == null) {
            Regex("(?i)apriranno\\s+il\\s+(\\d{1,2}/\\d{1,2}/\\d{4})")
                .find(text)?.groupValues?.getOrNull(1)?.let { open = parseDate(it) }
        }
        if (close == null) {
            Regex("(?i)chiuderanno\\s+il\\s+(\\d{1,2}/\\d{1,2}/\\d{4})")
                .find(text)?.groupValues?.getOrNull(1)?.let { close = parseDate(it) }
        }
        if (seats == null) {
            Regex("""posti\s+disponibili:\s*([0-9]+(?:/[0-9]+)?)""", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.getOrNull(1)?.let { seats = it }
        }
        if (taken == null) {
            Regex("(?i)al\\s+momento\\s+ci\\s+sono\\s+(\\d+)\\s+iscritt")
                .find(text)?.groupValues?.getOrNull(1)?.let { taken = it }
        }

        if (open == null && close == null) {
            Timber.w("SubsWindow: no opening/closing dates found in detail HTML.")
        }

        return SubsWindow(opening = open, closing = close, seats = seats, taken = taken)
    }

    data class SubsWindow(
        val opening: LocalDate?,
        val closing: LocalDate?,
        val seats: String? = null,
        val taken: String? = null
    )
}