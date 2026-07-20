package it.buonacaccia.app.ui

import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.Branch
import it.buonacaccia.app.data.guessZone
import java.util.Locale

enum class UnitFilter { TUTTE, BRANCO, REPARTO, CLAN, CAPI }

internal object EventFiltering {
    private val italianRegions = listOf(
        "Abruzzo", "Basilicata", "Calabria", "Campania", "Emilia-Romagna",
        "Friuli-Venezia Giulia", "Lazio", "Liguria", "Lombardia", "Marche", "Molise",
        "Piemonte", "Puglia", "Sardegna", "Sicilia", "Toscana", "Trentino-Alto Adige",
        "Umbria", "Valle d'Aosta", "Veneto", "Emilia Romagna", "Friuli Venezia Giulia",
        "Trentino", "Alto Adige", "Val d'Aosta",
    ).sortedByDescending(String::length)

    fun availableRegions(events: List<BcEvent>): List<String> =
        listOf(ALL_REGIONS_LABEL) + events
            .mapNotNull(::regionOf)
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)

    fun filter(events: List<BcEvent>, state: EventsUiState): List<BcEvent> {
        val query = state.query.normalized()
        return events.filter { event ->
            matchesQuery(event, query) &&
                matchesRegion(event, state.region) &&
                matchesZone(event, state.zone) &&
                matchesUnit(event, state.unit) &&
                (!state.onlyOpen || event.statusColor in OPEN_STATUS_COLORS)
        }
    }

    fun regionOf(event: BcEvent): String? =
        event.region?.trim()?.takeIf(String::isNotEmpty)
            ?: listOfNotNull(event.location, event.title)
                .joinToString(" ")
                .normalized()
                .let { text -> italianRegions.firstOrNull { it.normalized() in text } }

    private fun matchesQuery(event: BcEvent, query: String): Boolean =
        query.isEmpty() || listOf(event.title, event.region, event.type, event.location)
            .filterNotNull()
            .any { query in it.normalized() }

    private fun matchesRegion(event: BcEvent, region: String?): Boolean =
        region == null || region == ALL_REGIONS_LABEL ||
            regionOf(event)?.equals(region, ignoreCase = true) == true

    private fun matchesZone(event: BcEvent, zone: String?): Boolean =
        zone == null || zone == ALL_REGIONS_LABEL ||
            event.guessZone()?.equals(zone, ignoreCase = true) == true

    private fun matchesUnit(event: BcEvent, unit: UnitFilter): Boolean = when (unit) {
        UnitFilter.TUTTE -> true
        UnitFilter.BRANCO -> event.branch == Branch.LC
        UnitFilter.REPARTO -> event.branch == Branch.EG
        UnitFilter.CLAN -> event.branch == Branch.RS
        UnitFilter.CAPI -> event.branch == Branch.CAPI
    }

    private fun String.normalized(): String = trim().lowercase(Locale.ROOT)

    private const val ALL_REGIONS_LABEL = "Tutte"
    private val OPEN_STATUS_COLORS = setOf("green", "yellow")
}
