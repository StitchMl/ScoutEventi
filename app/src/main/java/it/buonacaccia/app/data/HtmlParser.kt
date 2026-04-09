package it.buonacaccia.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.net.URLDecoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object HtmlParser {

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY),
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ITALY)
    )
    private val inlineDatePattern = Regex("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b")

    fun parseEvents(html: String, baseUrl: String): List<BcEvent> {
        val doc = Jsoup.parse(html, baseUrl)
        val today = LocalDate.now()
        val structuredEvents = parseStructuredEvents(doc, today)
        val flexibleEvents = parseFlexibleEvents(doc, today)

        if (structuredEvents.isEmpty() && flexibleEvents.isEmpty()) {
            Timber.w("No recognizable events found in the HTML.")
            return emptyList()
        }

        val merged = LinkedHashMap<String, BcEvent>()
        structuredEvents.forEach { event ->
            merged[eventKeyOf(event)] = event
        }
        flexibleEvents.forEach { event ->
            val key = eventKeyOf(event)
            merged[key] = merged[key]?.let { structured ->
                mergeEvent(primary = structured, fallback = event)
            } ?: event
        }

        Timber.d(
            "Parsed events structured=%d flexible=%d merged=%d",
            structuredEvents.size,
            flexibleEvents.size,
            merged.size
        )
        return merged.values.toList()
    }

    fun inspectEventsPage(html: String, baseUrl: String): EventsPageInspection {
        val doc = Jsoup.parse(html, baseUrl)
        val table = findEventsTable(doc)
        val uniqueEventLinks = doc
            .select("a[href]")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                if (href.isBlank() || !isEventLink(href)) return@mapNotNull null
                extractEventId(href) ?: href
            }
            .toSet()

        val candidateRows = table
            ?.let { eventsTable ->
                (eventsTable.select("> tbody > tr") + eventsTable.select("> tr"))
                    .distinct()
                    .count { row ->
                        row.select("> th").isEmpty() &&
                            row.select("a[href]").any { isEventLink(it.attr("href")) }
                    }
            } ?: 0

        return EventsPageInspection(
            recognizableTable = table != null,
            uniqueEventLinkCount = uniqueEventLinks.size,
            candidateRowCount = candidateRows,
            hasWebFormsMarkers = html.contains("__VIEWSTATE") || html.contains("__doPostBack")
        )
    }

    fun hasRecognizableEventsTable(html: String, baseUrl: String): Boolean =
        findEventsTable(Jsoup.parse(html, baseUrl)) != null

    private fun parseStructuredEvents(doc: Document, today: LocalDate): List<BcEvent> {
        val table = findEventsTable(doc) ?: return emptyList()
        Timber.d("Found events table.")

        val rows = (table.select("> tbody > tr") + table.select("> tr"))
            .distinct()
            .filter { row -> row.select("> th").isEmpty() && row.select("> td").isNotEmpty() }
        Timber.d("Found %d potential event rows.", rows.size)

        return rows.mapNotNull { parseStructuredRow(it, today) }
    }

    private fun parseStructuredRow(row: Element, today: LocalDate): BcEvent? {
        val cells = row.select("> th, > td")
        if (cells.isEmpty()) return null

        val titleIndex = cells.indexOfFirst {
            it.select("a[href]").any { anchor -> isEventLink(anchor.attr("href")) }
        }
        if (titleIndex == -1) return null

        val link = cells[titleIndex].selectFirst("a[href]") ?: return null
        val title = link.text().trim()
        if (title.isBlank()) return null

        val detailUrl = link.absUrl("href").ifBlank { return null }
        val start = parseDate(cells.getOrNull(titleIndex + 2)?.text())
        if (start?.isBefore(today) == true) {
            return null
        }

        val branch = detectBranch(cells.getOrNull(0) ?: row)
        val event = BcEvent(
            id = extractEventId(detailUrl),
            type = cells.getOrNull(0)?.text()?.trim()?.ifBlank { null } ?: deriveType(branch),
            title = title,
            region = normalizeRegion(cells.getOrNull(titleIndex + 1)?.text()),
            startDate = start,
            endDate = parseDate(cells.getOrNull(titleIndex + 3)?.text()),
            fee = cells.getOrNull(titleIndex + 4)?.text()?.trim()?.ifBlank { null },
            location = cells.getOrNull(titleIndex + 5)?.text()?.trim()?.ifBlank { null },
            enrolled = cells.getOrNull(titleIndex + 6)?.text()?.trim()?.ifBlank { null },
            status = cells.getOrNull(titleIndex + 8)?.text()?.trim()?.ifBlank { null },
            detailUrl = detailUrl,
            statusColor = detectStatusColor(row),
            branch = branch
        )
        Timber.v("Parsed structured event: %s", event)
        return event
    }

    private fun parseFlexibleEvents(doc: Document, today: LocalDate): List<BcEvent> {
        val events = LinkedHashMap<String, BcEvent>()

        doc.select("a[href]").forEach { anchor ->
            if (!isEventLink(anchor.attr("href"))) return@forEach

            val detailUrl = anchor.absUrl("href").ifBlank { return@forEach }
            val title = anchor.text().trim()
            if (title.isBlank()) return@forEach

            val container = findEventContainer(anchor)
            val context = buildFlexibleContext(anchor, container)
            val dates = extractDates(context)
            val start = dates.firstOrNull() ?: return@forEach
            val end = dates.getOrNull(1)

            if (end?.isBefore(today) == true) return@forEach
            if (end == null && start.isBefore(today)) return@forEach

            val sourceElement = container ?: anchor
            val branch = detectBranch(sourceElement)
            val event = BcEvent(
                id = extractEventId(detailUrl),
                type = deriveType(branch),
                title = title,
                region = normalizeRegion(BuonaCacciaRegions.firstCanonicalNameIn(context)),
                startDate = start,
                endDate = end,
                fee = null,
                location = null,
                enrolled = null,
                status = detectStatusText(context),
                detailUrl = detailUrl,
                statusColor = detectStatusColor(sourceElement, context),
                branch = branch
            )

            events.putIfAbsent(eventKeyOf(event), event)
        }

        if (events.isNotEmpty()) {
            Timber.d("Flexible parser recovered %d event candidates.", events.size)
        }

        return events.values.toList()
    }

    private fun buildFlexibleContext(anchor: Element, container: Element?): String =
        listOfNotNull(
            container?.text()?.trim()?.takeIf { it.isNotEmpty() },
            anchor.parent()?.text()?.trim()?.takeIf { it.isNotEmpty() },
            anchor.text().trim().takeIf { it.isNotEmpty() }
        ).distinct().joinToString(" | ")

    private fun extractDates(text: String): List<LocalDate> =
        inlineDatePattern.findAll(text)
            .mapNotNull { match -> parseDate(match.value) }
            .distinct()
            .toList()

    private fun findEventContainer(anchor: Element): Element? {
        val ancestors = generateSequence(anchor.parent()) { it.parent() }
            .take(8)
            .toList()

        return ancestors.firstOrNull { candidate ->
            eventLinkCount(candidate) == 1 && extractDates(candidate.text()).isNotEmpty()
        } ?: ancestors.firstOrNull { candidate ->
            val linkCount = eventLinkCount(candidate)
            val textLength = candidate.text().trim().length
            linkCount in 1..3 && textLength in 20..700
        } ?: anchor.parent()
    }

    private fun eventLinkCount(element: Element): Int =
        element.select("a[href]").count { anchor -> isEventLink(anchor.attr("href")) }

    private fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (fmt in dateFormats) {
            runCatching { return LocalDate.parse(s, fmt) }
        }
        return null
    }

    private fun isEventLink(href: String): Boolean {
        val h = href.lowercase(Locale.ROOT)
        if ("event.aspx" in h) return true
        if (h.contains("/event") && h.contains("e=")) return true
        if (Regex("/event/\\d+").containsMatchIn(h)) return true
        return false
    }

    private fun findEventsTable(doc: Document): Element? {
        doc.selectFirst("table#MainContent_EventsGridView")?.let { return it }

        val tables = doc.select("table")
        return tables.firstOrNull { table ->
            val headers = table.select("th")
                .map { it.text().trim().lowercase(Locale.ITALY) }
            listOf("titolo", "regione", "partenza", "rientro")
                .all { h -> headers.any { it.contains(h) } }
        }
    }

    private fun extractEventId(url: String): String? {
        val q = url.substringAfter('?', "")
        if (q.isNotEmpty()) {
            val params = q.split('&').mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) {
                    parts[0].lowercase(Locale.ROOT) to URLDecoder.decode(parts[1], "UTF-8")
                } else {
                    null
                }
            }.toMap()
            params["e"]?.let { return it }
        }

        Regex("(?i)[?&]e=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("(?i)/event/(\\d+)").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    fun parseSubscriptions(html: String): SubsWindow {
        val doc = Jsoup.parse(html)
        fun grab(id: String): LocalDate? = parseDate(doc.selectFirst("#$id")?.text())

        var open = grab("MainContent_EventFormView_lbSubsFrom")
        var close = grab("MainContent_EventFormView_lbSubsTo")
        var seats = doc.selectFirst("#MainContent_EventFormView_lbSeats")?.text()?.trim()
        var taken = doc.selectFirst("#MainContent_EventFormView_lbTaken")?.text()?.trim()

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

    private fun normalizeRegion(region: String?): String? =
        BuonaCacciaRegions.canonicalNameOf(region) ?: region?.trim()?.ifBlank { null }

    private fun deriveType(branch: Branch?): String? =
        when (branch) {
            Branch.RS -> "RS"
            Branch.EG -> "EG"
            Branch.LC -> "LC"
            Branch.CAPI -> "CAPI"
            null -> null
        }

    private fun detectBranch(element: Element): Branch? {
        val imageSources = element.select("img[src]").map { it.attr("src").lowercase(Locale.ROOT) }
        imageSources.firstOrNull { it.contains("branch_") }?.let { src ->
            return when {
                "branch_rs" in src -> Branch.RS
                "branch_eg" in src -> Branch.EG
                "branch_lc" in src -> Branch.LC
                else -> Branch.CAPI
            }
        }

        val text = element.text().lowercase(Locale.ROOT)
        return when {
            Regex("\\blc\\b").containsMatchIn(text) -> Branch.LC
            Regex("\\beg\\b").containsMatchIn(text) -> Branch.EG
            Regex("\\brs\\b").containsMatchIn(text) -> Branch.RS
            Regex("\\bcapi\\b").containsMatchIn(text) -> Branch.CAPI
            else -> null
        }
    }

    private fun detectStatusColor(element: Element, fallbackText: String? = null): String? {
        val statusImgSrc = element
            .select("img[src]")
            .map { it.attr("src").lowercase(Locale.ROOT) }
            .firstOrNull { it.contains("light_") }

        val fromImage = when {
            statusImgSrc?.contains("light_green") == true -> "green"
            statusImgSrc?.contains("light_yellow") == true -> "yellow"
            statusImgSrc?.contains("light_dual") == true -> "dual"
            statusImgSrc?.contains("light_red") == true -> "red"
            else -> null
        }
        if (fromImage != null) return fromImage

        val normalizedText = fallbackText?.lowercase(Locale.ROOT) ?: return null
        return when {
            "lista d'attesa" in normalizedText || "lista di attesa" in normalizedText -> "yellow"
            "chius" in normalizedText -> "red"
            "apert" in normalizedText -> "green"
            else -> null
        }
    }

    private fun detectStatusText(text: String): String? {
        val normalizedText = text.lowercase(Locale.ROOT)
        return when {
            "lista d'attesa" in normalizedText || "lista di attesa" in normalizedText -> "Lista d'attesa"
            "chius" in normalizedText -> "Chiuso"
            "apert" in normalizedText -> "Aperto"
            else -> null
        }
    }

    private fun eventKeyOf(event: BcEvent): String =
        event.id?.takeIf { it.isNotBlank() } ?: event.detailUrl

    private fun mergeEvent(primary: BcEvent, fallback: BcEvent): BcEvent =
        primary.copy(
            type = primary.type ?: fallback.type,
            region = primary.region ?: fallback.region,
            startDate = primary.startDate ?: fallback.startDate,
            endDate = primary.endDate ?: fallback.endDate,
            fee = primary.fee ?: fallback.fee,
            location = primary.location ?: fallback.location,
            enrolled = primary.enrolled ?: fallback.enrolled,
            status = primary.status ?: fallback.status,
            statusColor = primary.statusColor ?: fallback.statusColor,
            branch = primary.branch ?: fallback.branch
        )

    data class SubsWindow(
        val opening: LocalDate?,
        val closing: LocalDate?,
        val seats: String? = null,
        val taken: String? = null
    )

    data class EventsPageInspection(
        val recognizableTable: Boolean,
        val uniqueEventLinkCount: Int,
        val candidateRowCount: Int,
        val hasWebFormsMarkers: Boolean
    )
}
