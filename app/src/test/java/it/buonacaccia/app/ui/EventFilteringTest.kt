package it.buonacaccia.app.ui

import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.Branch
import org.junit.Assert.assertEquals
import org.junit.Test

class EventFilteringTest {
    private val events = listOf(
        event("Lombardia", "Route nazionale", Branch.RS, "green", "Milano"),
        event(null, "Campo regionale Veneto", Branch.EG, "red", "Verona"),
        event("Lazio", "Formazione capi", Branch.CAPI, "yellow", "Roma"),
    )

    @Test
    fun filter_combinesSearchRegionUnitAndOpenStatus() {
        val state = EventsUiState(
            items = events,
            query = "FORMAZIONE",
            region = "lazio",
            unit = UnitFilter.CAPI,
            onlyOpen = true,
        )

        assertEquals(listOf("Formazione capi"), EventFiltering.filter(events, state).map { it.title })
    }

    @Test
    fun availableRegions_infersMissingRegionAndSortsCaseInsensitively() {
        assertEquals(
            listOf("Tutte", "Lazio", "Lombardia", "Veneto"),
            EventFiltering.availableRegions(events),
        )
    }

    @Test
    fun onlyOpen_acceptsGreenAndYellowStatuses() {
        val result = EventFiltering.filter(events, EventsUiState(onlyOpen = true))

        assertEquals(listOf("Route nazionale", "Formazione capi"), result.map { it.title })
    }

    private fun event(
        region: String?,
        title: String,
        branch: Branch,
        statusColor: String,
        location: String,
    ) = BcEvent(
        id = title,
        type = "Evento",
        title = title,
        region = region,
        startDate = null,
        endDate = null,
        fee = null,
        location = location,
        enrolled = null,
        status = null,
        detailUrl = "https://example.test/$title",
        statusColor = statusColor,
        branch = branch,
    )
}
