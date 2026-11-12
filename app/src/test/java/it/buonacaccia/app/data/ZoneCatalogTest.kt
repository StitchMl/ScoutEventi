package it.buonacaccia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneCatalogTest {
    @Test
    fun catalog_usesCanonicalNamesAndParenthesizedAliases() {
        assertEquals("Roma Centro Urbis", ZoneCatalog.identify("Evento Centro Urbis", "Lazio"))
        assertEquals("Roma Centro Urbis", ZoneCatalog.identify("Evento Andromeda", "Lazio"))
        assertEquals("Como-Lecco-Sondrio", ZoneCatalog.identify("Incontro SoLCo", "Lombardia"))
        assertFalse(ZoneCatalog.allNames().contains("Andromeda"))
    }

    @Test
    fun identify_flattensTextAndToleratesTypos() {
        assertEquals("Ostiense", ZoneCatalog.identify("MAM_ZONA-OSTIENSE/E-G", "Lazio"))
        assertEquals("Ostiense", ZoneCatalog.identify("MAM zona Ostiense", "Lazio"))
        assertEquals("Ostiense", ZoneCatalog.identify("MAM zona Ostiense", "Lazio"))
        assertEquals(
            "Milano",
            ZoneCatalog.identify("Evento della zona Milnao", "Lombardia", allowFuzzy = true),
        )
    }

    @Test
    fun identify_restrictsCandidatesToSelectedRegion() {
        assertNull(ZoneCatalog.identify("Evento Ostiense", "Lombardia"))
        assertTrue(ZoneCatalog.zonesForRegion("Lazio").contains("Ostiense"))
        assertFalse(ZoneCatalog.zonesForRegion("Lazio").contains("Milano"))
        assertEquals(listOf("Val d'Aosta"), ZoneCatalog.zonesForRegion("Valle d'Aosta"))
    }

    @Test
    fun catalog_rejectsNamesMissingFromSourceFile() {
        assertNull(ZoneCatalog.canonicalName("Langhe", "Piemonte"))
        assertNull(ZoneCatalog.canonicalName("Pesaro", "Marche"))
    }

    @Test
    fun fuzzyMatching_requiresExplicitPermission() {
        assertNull(ZoneCatalog.identify("Formazione capi", "Emilia-Romagna"))
        assertEquals("Carpi", ZoneCatalog.canonicalName("Carpi", "Emilia-Romagna"))
    }
}
