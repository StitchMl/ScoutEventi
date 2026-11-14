package it.buonacaccia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTypeCatalogTest {
    @Test
    fun catalogMatchesTheDefinitiveMapping() {
        assertEquals(37, EventTypeCatalog.knownTypes.size)
        assertTrue(EventTypeCatalog.knownTypes.all { it.key.isNotBlank() && it.aliases.isNotEmpty() })
        assertEquals(37, EventTypeCatalog.notificationCodes.size)
        assertTrue("Spec" in EventTypeCatalog.notificationCodes)
        assertTrue("SpecSq" in EventTypeCatalog.notificationCodes)
        assertTrue("Comp" in EventTypeCatalog.notificationCodes)
        assertTrue("MF-AE/AG" in EventTypeCatalog.notificationCodes)
        assertTrue(EventTypeCatalog.notificationCodes.all { EventTypeCatalog.resolve(it).known })
    }

    @Test
    fun notificationCodeUsesOneStableIdentityForAliases() {
        assertEquals("Spec", EventTypeCatalog.notificationCode("Specialità"))
        assertEquals("Spec", EventTypeCatalog.notificationCode("Spec"))
        assertEquals("EPPPI", EventTypeCatalog.notificationCode("EPI"))
        assertEquals("CapiSq", EventTypeCatalog.notificationCode("CSq"))
        assertEquals("Form", EventTypeCatalog.notificationCode("MF-Form"))
    }

    @Test
    fun everyDefinitiveOriginalTypeUsesItsMappedCode() {
        val mapping = linkedMapOf(
            "Area Training Team" to "ATT",
            "Assemblea regionale" to "AssReg",
            "Candidature Competenza" to "CComp",
            "Capi Squadriglia" to "CapiSq",
            "CCG" to "CCG",
            "CFA" to "CFA",
            "CFM E/G" to "CFM E/G",
            "CFM L/C" to "CFM L/C",
            "CFM R/S" to "CFM R/S",
            "CFT" to "CFT",
            "Competenza" to "Comp",
            "E/G" to "E/G",
            "EPPPI" to "EPPPI",
            "Evento Start Aiuti" to "StartAiuti",
            "Evento Start Assistant" to "StartAssit",
            "L/C" to "L/C",
            "MF- Primo soccorso" to "MF-PS",
            "MF-Aggiornam. metodologico E/G" to "MF-AM E/G",
            "MF-Aggiornam. metodologico L/C" to "MF-AM L/C",
            "MF-Aggiornam. metodologico R/S" to "MF-AM R/S",
            "MF-Ass./Animatori di gruppo" to "MF-AE/AG",
            "MF-Capi Gruppo" to "MF-CG",
            "MF-Interbranca" to "MF-INTER",
            "MF-Percorsi ass.vi specifici" to "MF-PAS",
            "MF-Percorso di tirocinio" to "MF-PT",
            "MF-Tematiche educative" to "MF-TE",
            "MF-Terzo settore" to "MF-TS",
            "Modulo per formatori" to "Form",
            "National Training Team" to "NTT",
            "Partenti" to "Part",
            "Piccole Orme" to "PO",
            "R/S" to "R/S",
            "Regional Training Team" to "RTT",
            "ROSS" to "ROSS",
            "Speciale" to "Speciale",
            "Specialità" to "Spec",
            "Specialità Squadriglia" to "SpecSq",
        )

        assertEquals(EventTypeCatalog.notificationCodes, mapping.values.toSet())
        mapping.forEach { (original, code) ->
            assertEquals(code, EventTypeCatalog.notificationCode(original))
        }
    }

    @Test
    fun definitiveCodesResolveToStableKeys() {
        assertEquals("EVENTI_E_G", EventTypeCatalog.resolve("E/G").key)
        assertEquals("MF_AGGIORNAM_METODOLOGICO_E_G", EventTypeCatalog.resolve("MF-AM E/G").key)
        assertEquals("MF_PERCORSO_DI_TIROCINIO", EventTypeCatalog.resolve("MF-PT").key)
        assertEquals("SPECIALITA", EventTypeCatalog.resolve("Spec").key)
        assertEquals("SPECIALITA_SQUADRIGLIA", EventTypeCatalog.resolve("SpecSq").key)
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
    fun displayCodeUsesTheDefinitiveAcronym() {
        val event = BcEvent(
            id = "1",
            title = "Evento",
            detailUrl = "https://example.test",
            type = "Specialità",
            region = null,
            startDate = null,
            endDate = null,
            fee = null,
            location = null,
            enrolled = null,
            status = null,
        )

        assertEquals("Spec", EventTypeCatalog.displayCode("Specialità", listOf(event)))
        assertEquals("Evento o campo di specialità", EventTypeCatalog.explanation("Spec"))
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
