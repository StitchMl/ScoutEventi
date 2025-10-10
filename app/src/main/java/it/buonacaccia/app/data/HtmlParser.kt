package it.buonacaccia.app.data

import android.os.Build
import androidx.annotation.RequiresApi
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object HtmlParser {

    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY),
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ITALY)
    )

    /**
     * Finds the table that contains the known columns ("Title," "Region," "Departure," "Return," etc.).
     * and returns the parsed event list.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun parseEvents(html: String, baseUrl: String): List<BcEvent> {
        val doc = Jsoup.parse(html, baseUrl)
        val table = findEventsTable(doc) ?: return emptyList()

        val headerIndex = headerMap(table)
        val rows = table.select("tbody tr").ifEmpty { table.select("tr").drop(1) }

        return rows.mapNotNull { tr ->
            val tds = tr.select("td")
            if (tds.isEmpty()) return@mapNotNull null

            val titleEl = getCell(headerIndex, tds, "Titolo")
            val titleLink = titleEl?.selectFirst("a")
            val title = (titleLink?.text() ?: titleEl?.text()).orEmpty().trim()
            if (title.isBlank()) return@mapNotNull null

            val detailUrl = (titleLink?.absUrl("href")).takeUnless { it.isNullOrBlank() }
                ?: baseUrl

            val id = extractEventId(detailUrl)

            val type = getCell(headerIndex, tds, "Tipo")?.text()?.trim()
            val region = getCell(headerIndex, tds, "Regione")?.text()?.trim()
            val fee = getCell(headerIndex, tds, "Quota")?.text()?.trim()?.ifBlank { null }
            val location = getCell(headerIndex, tds, "Località")?.text()?.trim()?.ifBlank { null }
            val enrolled = getCell(headerIndex, tds, "Iscritti")?.text()?.trim()?.ifBlank { null }
            val status = getCell(headerIndex, tds, "Stato")?.text()?.trim()?.ifBlank { null }

            val start = parseDate(getCell(headerIndex, tds, "Partenza")?.text())
            val end = parseDate(getCell(headerIndex, tds, "Rientro")?.text())

            BcEvent(
                id = id,
                type = type,
                title = title,
                region = region,
                startDate = start,
                endDate = end,
                fee = fee,
                location = location,
                enrolled = enrolled,
                status = status,
                detailUrl = detailUrl
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (fmt in dateFormats) {
            runCatching { return LocalDate.parse(s, fmt) }.onFailure { /* try next */ }
        }
        return null
    }

    private fun findEventsTable(doc: Document): Element? {
        val tables = doc.select("table")
        return tables.firstOrNull { table ->
            val headers = table.select("th").map { it.text().trim().lowercase(Locale.ITALY) }
            listOf("titolo", "regione", "partenza", "rientro").all { h -> headers.any { it.contains(h) } }
        } ?: doc.selectFirst("table")
    }

    private fun headerMap(table: Element): Map<String, Int> {
        val headers = table.select("th").mapIndexed { idx, th ->
            th.text().trim() to idx
        }.toMap()

        fun findIndex(key: String): Int? =
            headers.entries.firstOrNull { it.key.contains(key, ignoreCase = true) }?.value

        return mapOf(
            "Tipo" to (findIndex("tipo") ?: -1),
            "Titolo" to (findIndex("titolo") ?: 0),
            "Regione" to (findIndex("regione") ?: -1),
            "Partenza" to (findIndex("partenza") ?: -1),
            "Rientro" to (findIndex("rientro") ?: -1),
            "Quota" to (findIndex("quota") ?: -1),
            "Località" to (findIndex("local") ?: -1),
            "Iscritti" to (findIndex("iscr") ?: -1),
            "Stato" to (findIndex("stato") ?: -1)
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
            val parts = it.split('=')
            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], StandardCharsets.UTF_8) else null
        }.toMap()
        return params["e"]
    }
}