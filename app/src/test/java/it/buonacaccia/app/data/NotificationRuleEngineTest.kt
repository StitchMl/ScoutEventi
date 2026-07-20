package it.buonacaccia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRuleEngineTest {

    @Test
    fun shouldNotify_supportsMixedTypeAndRegionFilters() {
        val preferences = NotificationPreferences(
            allowedTypes = setOf("Specialità", "Moduli"),
            defaultRegions = setOf("Lazio"),
            typeRegionRules = listOf(
                NotificationTypeRegionRule(type = "Moduli", regions = emptySet())
            )
        )

        assertTrue(NotificationRuleEngine.shouldNotify(event(type = "Specialità", region = "Lazio"), preferences))
        assertFalse(NotificationRuleEngine.shouldNotify(event(type = "Specialità", region = "Lombardia"), preferences))
        assertTrue(NotificationRuleEngine.shouldNotify(event(type = "Moduli", region = "Lazio"), preferences))
        assertTrue(NotificationRuleEngine.shouldNotify(event(type = "Moduli", region = "Lombardia"), preferences))
        assertFalse(NotificationRuleEngine.shouldNotify(event(type = "Campetti", region = "Lazio"), preferences))
    }

    @Test
    fun regionsForFetchPrefilter_returnsNullWhenAnySelectedTypeNeedsAllItaly() {
        val preferences = NotificationPreferences(
            allowedTypes = setOf("Specialità", "Moduli"),
            defaultRegions = setOf("Lazio"),
            typeRegionRules = listOf(
                NotificationTypeRegionRule(type = "Moduli", regions = emptySet())
            )
        )

        assertNull(NotificationRuleEngine.regionsForFetchPrefilter(preferences))
    }

    @Test
    fun regionsForFetchPrefilter_usesUnionWhenEverySelectedTypeIsRegionBound() {
        val preferences = NotificationPreferences(
            allowedTypes = setOf("Specialità", "Moduli"),
            typeRegionRules = listOf(
                NotificationTypeRegionRule(type = "Specialità", regions = setOf("Lazio")),
                NotificationTypeRegionRule(type = "Moduli", regions = setOf("Lombardia"))
            )
        )

        assertEquals(
            linkedSetOf("Lazio", "Lombardia"),
            NotificationRuleEngine.regionsForFetchPrefilter(preferences)
        )
    }

    @Test
    fun shouldNotify_keepsLegacyDenylistAndGlobalRegionsBehavior() {
        val preferences = NotificationPreferences(
            mutedTypes = setOf("Specialità"),
            defaultRegions = setOf("Lazio")
        )

        assertFalse(NotificationRuleEngine.shouldNotify(event(type = "Specialità", region = "Lazio"), preferences))
        assertTrue(NotificationRuleEngine.shouldNotify(event(type = "Moduli", region = "Lazio"), preferences))
        assertFalse(NotificationRuleEngine.shouldNotify(event(type = "Moduli", region = "Lombardia"), preferences))
    }

    @Test
    fun guessZone_extractsFromZoneOrLocation() {
        val eventWithZone = event(type = "Specialità", region = "Piemonte").copy(zone = "Langhe")
        assertEquals("Langhe", eventWithZone.guessZone())

        // 2-letter province codes in parentheses should be ignored
        val eventWithProvince = event(type = "Specialità", region = "Piemonte").copy(location = "Alba (CN)")
        assertNull(eventWithProvince.guessZone())

        // Valid zone names in parentheses should be extracted
        val eventWithRealZone = event(type = "Specialità", region = "Lazio").copy(location = "Roma (Ostiense)")
        assertEquals("Ostiense", eventWithRealZone.guessZone())

        // Title zone extraction
        val eventWithTitleZone = event(type = "Specialità", region = "Marche").copy(
            title = "MF-Aggiornam. metodologico E/G - Zona Pesaro_Patto"
        )
        assertEquals("Pesaro", eventWithTitleZone.guessZone())

        val eventWithoutZone = event(type = "Specialità", region = "Piemonte")
        assertNull(eventWithoutZone.guessZone())
    }

    @Test
    fun shouldNotify_filtersByZone() {
        val preferences = NotificationPreferences(
            defaultZones = setOf("Ostiense", "Pesaro")
        )

        // Event in zone Ostiense should notify
        val eventOstiense = event(type = "Specialità", region = "Lazio").copy(location = "Roma (Ostiense)")
        assertTrue(NotificationRuleEngine.shouldNotify(eventOstiense, preferences))

        // Event with province code CN should not notify (since its zone is guessed as null, not CN)
        val eventCN = event(type = "Specialità", region = "Piemonte").copy(location = "Alba (CN)")
        assertFalse(NotificationRuleEngine.shouldNotify(eventCN, preferences))

        // Event in zone Milano (not in allowed list) should not notify
        val eventMilano = event(type = "Specialità", region = "Lombardia").copy(location = "Milano (Milano)")
        assertFalse(NotificationRuleEngine.shouldNotify(eventMilano, preferences))

        // Empty zones preferences should notify everything
        val emptyPrefs = NotificationPreferences(defaultZones = emptySet())
        assertTrue(NotificationRuleEngine.shouldNotify(eventMilano, emptyPrefs))
    }

    private fun event(type: String?, region: String?) = BcEvent(
        id = "id-${type ?: "none"}-${region ?: "none"}",
        type = type,
        title = "Evento",
        region = region,
        startDate = null,
        endDate = null,
        fee = null,
        location = null,
        enrolled = null,
        status = null,
        detailUrl = "https://example.com/${type ?: "none"}-${region ?: "none"}"
    )
}
