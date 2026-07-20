package it.buonacaccia.app.widget

import it.buonacaccia.app.data.BcEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetEventSelectorsTest {

    private val today = LocalDate.parse("2026-04-09")

    @Test
    fun upcomingOpenings_filtersClosedAndOrdersByOpeningDate() {
        val first = event(
            id = "first",
            title = "Campo Alfa",
            startDate = LocalDate.parse("2026-05-10"),
            endDate = LocalDate.parse("2026-05-12"),
            subsOpenDate = LocalDate.parse("2026-04-10")
        )
        val second = event(
            id = "second",
            title = "Campo Beta",
            startDate = LocalDate.parse("2026-06-01"),
            endDate = LocalDate.parse("2026-06-03"),
            subsOpenDate = LocalDate.parse("2026-04-15")
        )
        val alreadyPast = event(
            id = "past",
            title = "Campo Passato",
            startDate = LocalDate.parse("2026-04-01"),
            endDate = LocalDate.parse("2026-04-05"),
            subsOpenDate = LocalDate.parse("2026-04-12")
        )

        val selected = selectWidgetEvents(
            kind = EventsWidgetKind.UPCOMING_OPENINGS,
            events = listOf(second, alreadyPast, first),
            today = today,
            onlyFollowed = false,
            subscribedKeys = emptySet()
        )

        assertEquals(listOf("first", "second"), selected.map { it.id })
    }

    @Test
    fun eventsByDate_keepsActiveAndClosedRegistrationEventsAndPutsUndatedLast() {
        val pastWithoutEnd = event(
            id = "past-no-end",
            title = "Evento passato",
            startDate = LocalDate.parse("2026-04-01")
        )
        val alreadyStarted = event(
            id = "already-started",
            title = "Evento gia iniziato",
            startDate = LocalDate.parse("2026-01-09"),
            endDate = LocalDate.parse("2026-05-23")
        )
        val endOnly = event(
            id = "end-only",
            title = "Evento fine mese",
            endDate = LocalDate.parse("2026-04-30")
        )
        val dated = event(
            id = "dated",
            title = "Evento maggio",
            startDate = LocalDate.parse("2026-05-10"),
            endDate = LocalDate.parse("2026-05-12")
        )
        val futureClosed = event(
            id = "future-closed",
            title = "Evento futuro chiuso",
            startDate = LocalDate.parse("2026-04-20"),
            endDate = LocalDate.parse("2026-04-21"),
            subsCloseDate = LocalDate.parse("2026-04-08")
        )
        val undated = event(
            id = "undated",
            title = "Evento senza data"
        )

        val selected = selectWidgetEvents(
            kind = EventsWidgetKind.EVENTS_BY_DATE,
            events = listOf(undated, dated, futureClosed, endOnly, pastWithoutEnd, alreadyStarted),
            today = today,
            onlyFollowed = false,
            subscribedKeys = emptySet()
        )

        assertEquals(
            listOf("already-started", "future-closed", "end-only", "dated", "undated"),
            selected.map { it.id },
        )
        assertEquals(LocalDate.parse("2026-04-30"), badgeDateFor(EventsWidgetKind.EVENTS_BY_DATE, endOnly))
    }

    @Test
    fun selectors_honorOnlyFollowedFilter() {
        val followed = event(
            id = "followed",
            title = "Evento seguito",
            startDate = LocalDate.parse("2026-05-01"),
            endDate = LocalDate.parse("2026-05-03"),
            subsOpenDate = LocalDate.parse("2026-04-11")
        )
        val other = event(
            id = "other",
            title = "Altro evento",
            startDate = LocalDate.parse("2026-05-05"),
            endDate = LocalDate.parse("2026-05-06"),
            subsOpenDate = LocalDate.parse("2026-04-12")
        )

        val selected = selectWidgetEvents(
            kind = EventsWidgetKind.EVENTS_BY_DATE,
            events = listOf(followed, other),
            today = today,
            onlyFollowed = true,
            subscribedKeys = setOf("followed")
        )

        assertEquals(listOf("followed"), selected.map { it.id })
    }

    private fun event(
        id: String,
        title: String,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        subsOpenDate: LocalDate? = null,
        subsCloseDate: LocalDate? = null
    ) = BcEvent(
        id = id,
        type = null,
        title = title,
        region = null,
        startDate = startDate,
        endDate = endDate,
        fee = null,
        location = null,
        enrolled = null,
        status = null,
        detailUrl = "https://example.com/$id",
        statusColor = null,
        branch = null,
        subsOpenDate = subsOpenDate,
        subsCloseDate = subsCloseDate
    )
}
