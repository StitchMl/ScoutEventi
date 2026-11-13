package it.buonacaccia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTypeCatalogTest {
    @Test
    fun catalogContainsHistoricalNormalizedTypesAndWebiComp() {
        assertEquals(78, EventTypeCatalog.knownTypes.size)
        assertTrue(EventTypeCatalog.knownTypes.all { it.key.isNotBlank() && it.aliases.isNotEmpty() })
        assertEquals(77, EventTypeCatalog.knownTypes.count { it.key != "WEBICOMP" })
        assertEquals(38, EventTypeCatalog.notificationCodes.size)
        assertTrue("WebiComp" in EventTypeCatalog.notificationCodes)
    }

    @Test
    fun historicalAliasesResolveToStableKeys() {
        assertEquals("ALTRO_E_G", EventTypeCatalog.resolve("E/G").key)
        assertEquals("MF_AGGIORNAM_METODOLOGICO_E_G", EventTypeCatalog.resolve("MAM E/G").key)
        assertEquals("MF_AGGIORNAM_METODOLOGICO_E_G", EventTypeCatalog.resolve("MF-AM E/G").key)
        assertEquals("MF_PERCORSO_DI_TIROCINIO", EventTypeCatalog.resolve("PT").key)
        assertEquals("WEBICOMP", EventTypeCatalog.resolve("web i comp").key)
        assertEquals("MF_VITA_CRISTIANA", EventTypeCatalog.resolve("MF-EVC").key)
        assertEquals("MF_VOLONTARI_PC_FORMATI_INFORMATI_D_LGS_81_08", EventTypeCatalog.resolve("MF-VPCFI").key)
        assertEquals("ASSEMBLEA_DI_ZONA", EventTypeCatalog.resolve("AssZona").key)
        assertEquals("EVENTO_START_ASSISTANT", EventTypeCatalog.resolve("StartAssit").key)
    }

    @Test
    fun unseenTypeRemainsAvailableAsUncatalogued() {
        val definition = EventTypeCatalog.resolve("Nuovo evento 2030")

        assertFalse(definition.known)
        assertEquals("Nuovo evento 2030", definition.label)
        assertTrue(definition.key.startsWith("UNKNOWN:"))
    }

    @Test
    fun savedAliasMigratesToCurrentLabel() {
        assertEquals("MAM E/G", EventTypeCatalog.canonicalLabel("MF-AM E/G"))
        assertEquals("MAM", EventTypeCatalog.canonicalLabel("MAM"))
    }

    @Test
    fun displayCodeUsesTheValueReceivedFromBuonaCaccia() {
        val event = BcEvent(
            id = "1",
            title = "Evento",
            detailUrl = "https://example.test",
            type = "MF-EVC",
            region = null,
            startDate = null,
            endDate = null,
            fee = null,
            location = null,
            enrolled = null,
            status = null,
        )

        assertEquals("MF-EVC", EventTypeCatalog.displayCode("MF - Vita cristiana", listOf(event)))
        assertEquals("Modulo formativo – Vita cristiana", EventTypeCatalog.explanation("MF-EVC"))
    }

    @Test
    fun unknownTypeMeaningIsInferredFromItsEventTitle() {
        val event = BcEvent(
            id = "2",
            title = "MF-Nuove competenze - Primo incontro",
            detailUrl = "https://example.test",
            type = "MF-NC",
            region = null,
            startDate = null,
            endDate = null,
            fee = null,
            location = null,
            enrolled = null,
            status = null,
        )

        assertEquals(
            "Significato ipotizzato dal titolo: MF-Nuove competenze",
            EventTypeCatalog.explanation("MF-NC", listOf(event)),
        )
        assertEquals(
            "Significato ipotizzato dalle sigle: Modulo formativo",
            EventTypeCatalog.explanation("MF-XYZ"),
        )
    }
}
