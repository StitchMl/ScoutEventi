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
    }

    @Test
    fun historicalAliasesResolveToStableKeys() {
        assertEquals("ALTRO_E_G", EventTypeCatalog.resolve("E/G").key)
        assertEquals("MF_AGGIORNAM_METODOLOGICO_E_G", EventTypeCatalog.resolve("MAM E/G").key)
        assertEquals("MF_AGGIORNAM_METODOLOGICO_E_G", EventTypeCatalog.resolve("MF-AM E/G").key)
        assertEquals("MF_PERCORSO_DI_TIROCINIO", EventTypeCatalog.resolve("PT").key)
        assertEquals("WEBICOMP", EventTypeCatalog.resolve("web i comp").key)
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
}
