package it.buonacaccia.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventTimelineTest {

    private val today = LocalDate.parse("2026-04-09")

    @Test
    fun isCurrentOrUpcoming_rejectsPastSingleDayEventsWithoutEndDate() {
        val event = event(
            startDate = LocalDate.parse("2026-04-01"),
            endDate = null
        )

        assertFalse(event.isCurrentOrUpcoming(today))
    }

    @Test
    fun isCurrentOrUpcoming_rejectsEventsWhoseStartDateIsAlreadyPast() {
        val event = event(
            startDate = LocalDate.parse("2026-04-08"),
            endDate = LocalDate.parse("2026-04-10")
        )

        assertFalse(event.isCurrentOrUpcoming(today))
    }

    @Test
    fun isCurrentOrUpcoming_keepsFutureEventsWithOnlyEndDate() {
        val event = event(
            startDate = null,
            endDate = LocalDate.parse("2026-04-30")
        )

        assertTrue(event.isCurrentOrUpcoming(today))
    }

    @Test
    fun isCurrentOrUpcoming_keepsUndatedEvents() {
        assertTrue(event(startDate = null, endDate = null).isCurrentOrUpcoming(today))
    }

    @Test
    fun isStillRelevant_rejectsFutureEventsWithClosedRegistrations() {
        val event = event(
            startDate = LocalDate.parse("2026-04-20"),
            endDate = LocalDate.parse("2026-04-22"),
            subsCloseDate = LocalDate.parse("2026-04-08")
        )

        assertFalse(event.isStillRelevant(today))
    }

    @Test
    fun isStillRelevant_keepsFutureEventsWithOpenRegistrations() {
        val event = event(
            startDate = LocalDate.parse("2026-04-20"),
            endDate = LocalDate.parse("2026-04-22"),
            subsCloseDate = LocalDate.parse("2026-04-09")
        )

        assertTrue(event.isStillRelevant(today))
    }

    private fun event(
        startDate: LocalDate?,
        endDate: LocalDate?,
        subsCloseDate: LocalDate? = null
    ) = BcEvent(
        id = "event",
        type = null,
        title = "Evento",
        region = null,
        startDate = startDate,
        endDate = endDate,
        fee = null,
        location = null,
        enrolled = null,
        status = null,
        detailUrl = "https://example.com/event",
        subsCloseDate = subsCloseDate
    )
}
