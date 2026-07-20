package it.buonacaccia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HtmlParserTest {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    @Test
    fun parseEvents_parsesExpectedRowAndNormalizesRegion() {
        val start = LocalDate.now().plusDays(10)
        val end = start.plusDays(2)
        val html = """
            <html>
              <body>
                <table id="MainContent_EventsGridView">
                  <tr>
                    <th>Tipo</th>
                    <th>Titolo</th>
                    <th>Regione</th>
                    <th>Partenza</th>
                    <th>Rientro</th>
                    <th>Quota</th>
                    <th>Luogo</th>
                    <th>Iscritti</th>
                    <th></th>
                    <th>Status</th>
                  </tr>
                  <tr>
                    <td><img src="branch_eg.png" /> CFM</td>
                    <td><a href="Event.aspx?e=123">Campo Futuro</a></td>
                    <td>fvg</td>
                    <td>${start.format(dateFormatter)}</td>
                    <td>${end.format(dateFormatter)}</td>
                    <td>20,00 €</td>
                    <td>Trieste</td>
                    <td>12 / 30</td>
                    <td><img src="light_green.png" /></td>
                    <td>Aperto</td>
                  </tr>
                </table>
              </body>
            </html>
        """.trimIndent()

        val events = HtmlParser.parseEvents(html, "https://buonacaccia.agesci.it/Events.aspx")

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals("123", event.id)
        assertEquals("Campo Futuro", event.title)
        assertEquals("Friuli-Venezia Giulia", event.region)
        assertEquals(Branch.EG, event.branch)
        assertEquals("green", event.statusColor)
        assertEquals("https://buonacaccia.agesci.it/Event.aspx?e=123", event.detailUrl)
    }

    @Test
    fun parseEvents_ignoresUnknownTables() {
        val html = """
            <html>
              <body>
                <table>
                  <tr><th>Foo</th><th>Bar</th></tr>
                  <tr><td>Not</td><td>An event table</td></tr>
                </table>
              </body>
            </html>
        """.trimIndent()

        val events = HtmlParser.parseEvents(html, "https://buonacaccia.agesci.it/Events.aspx")

        assertTrue(events.isEmpty())
        assertFalse(HtmlParser.hasRecognizableEventsTable(html, "https://buonacaccia.agesci.it/Events.aspx"))
    }

    @Test
    fun parseSubscriptions_usesTextFallbackWhenIdsAreMissing() {
        val html = """
            <html>
              <body>
                <p>Le iscrizioni apriranno il 10/12/2026 e chiuderanno il 20/12/2026.</p>
                <p>Posti disponibili: 30/40</p>
                <p>Al momento ci sono 12 iscritti.</p>
              </body>
            </html>
        """.trimIndent()

        val subs = HtmlParser.parseSubscriptions(html)

        assertEquals(LocalDate.of(2026, 12, 10), subs.opening)
        assertEquals(LocalDate.of(2026, 12, 20), subs.closing)
        assertEquals("30/40", subs.seats)
        assertEquals("12", subs.taken)
    }

    @Test
    fun parseEvents_returnsEmptyWhenLinkCannotBeResolved() {
        val start = LocalDate.now().plusDays(5)
        val html = """
            <html>
              <body>
                <table id="MainContent_EventsGridView">
                  <tr>
                    <th>Titolo</th>
                    <th>Regione</th>
                    <th>Partenza</th>
                    <th>Rientro</th>
                  </tr>
                  <tr>
                    <td><a href="">Evento senza link</a></td>
                    <td>Lazio</td>
                    <td>${start.format(dateFormatter)}</td>
                    <td>${start.plusDays(1).format(dateFormatter)}</td>
                  </tr>
                </table>
              </body>
            </html>
        """.trimIndent()

        val events = HtmlParser.parseEvents(html, "https://buonacaccia.agesci.it/Events.aspx")

        assertEquals(emptyList<BcEvent>(), events)
    }

    @Test
    fun inspectEventsPage_countsRecognizableSignals() {
        val start = LocalDate.now().plusDays(5)
        val html = """
            <html>
              <body>
                <form>
                  <input type="hidden" name="__VIEWSTATE" value="x" />
                </form>
                <table id="MainContent_EventsGridView">
                  <tr>
                    <th>Titolo</th>
                    <th>Regione</th>
                    <th>Partenza</th>
                    <th>Rientro</th>
                  </tr>
                  <tr>
                    <td><a href="Event.aspx?e=1">Evento Uno</a></td>
                    <td>Lazio</td>
                    <td>${start.format(dateFormatter)}</td>
                    <td>${start.plusDays(1).format(dateFormatter)}</td>
                  </tr>
                  <tr>
                    <td><a href="Event.aspx?e=2">Evento Due</a></td>
                    <td>Lazio</td>
                    <td>${start.format(dateFormatter)}</td>
                    <td>${start.plusDays(1).format(dateFormatter)}</td>
                  </tr>
                </table>
              </body>
            </html>
        """.trimIndent()

        val inspection = HtmlParser.inspectEventsPage(html, "https://buonacaccia.agesci.it/Events.aspx")

        assertTrue(inspection.recognizableTable)
        assertTrue(inspection.hasWebFormsMarkers)
        assertEquals(2, inspection.uniqueEventLinkCount)
        assertEquals(2, inspection.candidateRowCount)
    }

    @Test
    fun parseEvents_recoversEventsFromFlexibleCardLayout() {
        val start = LocalDate.now().plusDays(12)
        val end = start.plusDays(3)
        val html = """
            <html>
              <body>
                <section class="events">
                  <article class="event-card">
                    <img src="branch_rs.png" />
                    <h2><a href="/Event.aspx?e=987">Route Futuro</a></h2>
                    <p>Lazio</p>
                    <p>${start.format(dateFormatter)} - ${end.format(dateFormatter)}</p>
                    <p>Iscrizioni aperte</p>
                  </article>
                </section>
              </body>
            </html>
        """.trimIndent()

        val events = HtmlParser.parseEvents(html, "https://buonacaccia.agesci.it/Events.aspx")

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals("987", event.id)
        assertEquals("Route Futuro", event.title)
        assertEquals("Lazio", event.region)
        assertEquals(Branch.RS, event.branch)
        assertEquals(start, event.startDate)
        assertEquals(end, event.endDate)
        assertEquals("green", event.statusColor)
    }

    @Test
    fun guessZone_extractsFromTitle() {
        val event1 = BcEvent(
            id = "1",
            type = "CFM",
            title = "MF-Aggiornam. metodologico E/G - Zona Pesaro_Patto associativo",
            region = "Marche",
            startDate = LocalDate.now(),
            endDate = LocalDate.now(),
            fee = "0 €",
            location = "Pesaro (PU)",
            enrolled = "0",
            status = "Aperto",
            detailUrl = "https://example.com"
        )
        assertEquals("Pesaro", event1.guessZone())

        val event2 = event1.copy(title = "Assemblea di Zona Ostiense - Convocazione")
        assertEquals("Ostiense", event2.guessZone())

        val event3 = event1.copy(title = "MF-Percorso di tirocinio - Zona Lucca Massa Carrara - Tirocinio 2")
        assertEquals("Lucca Massa Carrara", event3.guessZone())
    }

    @Test
    fun guessZone_ignoresProvinceAbbreviation() {
        val event = BcEvent(
            id = "2",
            type = "PO",
            title = "Piccole Orme - Blackout a Mostropoli",
            region = "Piemonte",
            startDate = LocalDate.now(),
            endDate = LocalDate.now(),
            fee = "55 €",
            location = "Alba (CN)",
            enrolled = "10",
            status = "Aperto",
            detailUrl = "https://example.com"
        )
        // CN is a province abbreviation (2 letters), so it should be ignored and guessZone should return null
        assertEquals(null, event.guessZone())
    }

    @Test
    fun guessZone_normalizesSiteFormattingAndKnownNames() {
        val base = BcEvent(
            id = "zone",
            type = "MF",
            title = "Evento",
            region = "Lazio",
            startDate = LocalDate.now(),
            endDate = LocalDate.now(),
            fee = null,
            location = null,
            enrolled = null,
            status = null,
            detailUrl = "https://example.com",
        )

        assertEquals("Ostiense", base.copy(zone = "  ZONA   OSTIENSE  ").guessZone())
        assertEquals("Pesaro", base.copy(zone = "Pesaro_Patto associativo").guessZone())
        assertEquals("Riviera d’Ulisse", base.copy(zone = "riviera d’ulisse").guessZone())
        assertEquals(null, base.copy(zone = "(da definire)").guessZone())
    }

    @Test
    fun parseSubscriptions_extractsFromDetailText() {
        val html = """
            <html>
              <body>
                <h2>[Marche] MF-Aggiornam. metodologico E/G - Zona Pesaro_Patto associativo</h2>
                <p>Le iscrizioni apriranno il 10/12/2026 e chiuderanno il 20/12/2026.</p>
                <p>Altro testo descrittivo...</p>
              </body>
            </html>
        """.trimIndent()

        val subs = HtmlParser.parseSubscriptions(html)
        assertEquals("Pesaro", subs.zone)
    }

    private fun assertTrue(value: Boolean) {
        assertEquals(true, value)
    }
}
