package it.buonacaccia.app.data

import java.text.Normalizer
import java.util.Locale

data class EventTypeDefinition(
    val key: String,
    val label: String,
    val description: String,
    val family: String,
    val scope: String,
    val known: Boolean = true,
    val aliases: Set<String> = emptySet(),
)

/** Catalogo statico ricavato dallo storico BuonaCaccia fornito (2011-2027). */
object EventTypeCatalog {
    private const val FAMILY_OTHER = "Altro e generico"
    private const val FAMILY_TRAINERS = "Formazione formatori"
    private const val FAMILY_GOVERNANCE = "Assemblee e organi"
    private const val FAMILY_LEADERS = "Formazione capi"
    private const val FAMILY_MODULES = "Moduli formativi"
    private const val CFM_EG = "CFM E/G"
    private const val CFM_LC = "CFM L/C"
    private const val CFM_RS = "CFM R/S"
    private const val CAPI_GRUPPO = "Capi Gruppo"

    val notificationCodes: Set<String> = linkedSetOf(
        "ATT", "AssReg", "CComp", "CapiSq", "CCG", "CFA", CFM_EG, CFM_LC,
        CFM_RS, "CFT", "Comp", "E/G", "EPPPI", "StartAiuti", "StartAssit",
        "L/C", "MF-PS", "MF-AM E/G", "MF-AM L/C", "MF-AM R/S", "MF-AE/AG",
        "MF-CG", "MF-INTER", "MF-PAS", "MF-PT", "MF-TE", "MF-TS", "Form",
        "NTT", "Part", "PO", "R/S", "RTT", "ROSS", "Speciale", "Spec", "SpecSq",
    )

    private fun entry(
        key: String,
        label: String,
        description: String = label,
        family: String,
        scope: String,
        aliases: Set<String>,
    ) = EventTypeDefinition(
        key,
        label,
        description,
        family,
        scope,
        aliases = aliases,
    )

    private fun simple(
        key: String,
        label: String,
        family: String,
        scope: String,
        vararg aliases: String,
    ) = EventTypeDefinition(key, label, label, family, scope, aliases = aliases.toSet())

    val knownTypes: List<EventTypeDefinition> = listOf(
        simple("AREA_TRAINING_TEAM", "Area Training Team", FAMILY_TRAINERS, "Area", "ATT", "Area Training Team"),
        simple("ASSREG", "Assemblea regionale", FAMILY_GOVERNANCE, "Regione", "AssReg", "Assemblea regionale"),
        simple("CANDIDATURE_COMPETENZA", "Candidature Competenza", "Ragazzi", "E/G", "CComp", "Candidature Competenza"),
        entry("CAPISQ", "Capi Squadriglia", "Campo o evento per capi squadriglia", "Ragazzi", "E/G", setOf("CapiSq", "Capi Squadriglia")),
        simple("CCG", "Campo capi Gruppo", FAMILY_LEADERS, CAPI_GRUPPO, "CCG"),
        entry("CFA", "CFA", "Campo di formazione associativa", FAMILY_LEADERS, "Trasversale", setOf("CFA")),
        entry("CFM_E_G", CFM_EG, "Campo di formazione metodologica E/G", FAMILY_LEADERS, "E/G", setOf(CFM_EG)),
        entry("CFM_L_C", CFM_LC, "Campo di formazione metodologica L/C", FAMILY_LEADERS, "L/C", setOf(CFM_LC)),
        entry("CFM_R_S", CFM_RS, "Campo di formazione metodologica R/S", FAMILY_LEADERS, "R/S", setOf(CFM_RS)),
        entry("CFT", "CFT", "Campo di formazione tirocinanti", FAMILY_LEADERS, "Trasversale", setOf("CFT")),
        simple("COMPETENZA", "Competenza", "Ragazzi", "E/G", "Comp", "Competenza"),
        simple("EVENTI_E_G", "E/G", "Ragazzi", "E/G", "E/G"),
        entry("EPPPI", "EPPPI", "Eventi di progressione personale a partecipazione individuale", "Ragazzi", "R/S", setOf("EPPPI")),
        entry("EVENTO_START_AIUTI", "Evento Start Aiuti", "Evento per aiuti dei campi scuola", FAMILY_TRAINERS, "Formatori", setOf("StartAiuti", "Evento Start Aiuti")),
        entry("EVENTO_START_ASSISTANT", "Evento Start Assistant", "Evento per assistant dei campi scuola", FAMILY_TRAINERS, "Formatori", setOf("StartAssit", "Evento Start Assistant")),
        simple("EVENTI_L_C", "L/C", "Ragazzi", "L/C", "L/C"),
        simple("MF_PRIMO_SOCCORSO", "MF - Primo soccorso", FAMILY_MODULES, "Trasversale", "MF-PS", "MF- Primo soccorso"),
        entry("MF_AGGIORNAM_METODOLOGICO_E_G", "MAM E/G", "Modulo di aggiornamento metodologico E/G", FAMILY_MODULES, "E/G", setOf("MF-AM E/G", "MF-Aggiornam. metodologico E/G", "MAM E/G")),
        entry("MF_AGGIORNAM_METODOLOGICO_L_C", "MAM L/C", "Modulo di aggiornamento metodologico L/C", FAMILY_MODULES, "L/C", setOf("MF-AM L/C", "MF-Aggiornam. metodologico L/C", "MAM L/C")),
        entry("MF_AGGIORNAM_METODOLOGICO_R_S", "MAM R/S", "Modulo di aggiornamento metodologico R/S", FAMILY_MODULES, "R/S", setOf("MF-AM R/S", "MF-Aggiornam. metodologico R/S", "MAM R/S")),
        entry("MF_ASS_ANIMATORI_DI_GRUPPO", "MF - Assistenti/animatori di gruppo", "Modulo per assistenti e animatori di gruppo", FAMILY_MODULES, "Gruppo", setOf("MF-AE/AG", "MF-Ass./Animatori di gruppo")),
        simple("MF_CAPI_GRUPPO", "MF - Capi Gruppo", FAMILY_MODULES, CAPI_GRUPPO, "MF-CG", "MF-Capi Gruppo"),
        simple("MF_INTERBRANCA", "MF - Interbranca", FAMILY_MODULES, "Trasversale", "MF-INTER", "MF-Interbranca"),
        simple("MF_PERCORSI_ASS_VI_SPECIFICI", "MF - Percorsi associativi specifici", FAMILY_MODULES, "Trasversale", "MF-PAS", "MF-Percorsi ass.vi specifici"),
        simple("MF_PERCORSO_DI_TIROCINIO", "MF - Percorso di tirocinio", FAMILY_MODULES, "Tirocinanti", "MF-PT", "MF-Percorso di tirocinio"),
        simple("MF_TEMATICHE_EDUCATIVE", "MF - Tematiche educative", FAMILY_MODULES, "Trasversale", "MF-TE", "MF-Tematiche educative"),
        simple("MF_TERZO_SETTORE", "MF - Terzo settore", FAMILY_MODULES, "Trasversale", "MF-TS", "MF-Terzo settore"),
        simple("MODULO_PER_FORMATORI", "Modulo per formatori", FAMILY_MODULES, "Formatori", "Form", "Modulo per formatori"),
        simple("NATIONAL_TRAINING_TEAM", "National Training Team", FAMILY_TRAINERS, "Nazionale", "NTT", "National Training Team"),
        entry("PART", "Partenti", "Eventi per partenti", "Ragazzi", "R/S", setOf("Part", "Partenti")),
        simple("PO", "Piccole Orme", "Ragazzi", "L/C", "PO", "Piccole Orme"),
        simple("EVENTI_R_S", "R/S", "Ragazzi", "R/S", "R/S"),
        simple("REGIONAL_TRAINING_TEAM", "Regional Training Team", FAMILY_TRAINERS, "Regione", "RTT", "Regional Training Team"),
        entry("ROSS", "ROSS", "Route di orientamento alla scelta di servizio", "Ragazzi", "R/S", setOf("ROSS")),
        entry("SPECIALE", "Speciale", "Categoria generica BuonaCaccia", FAMILY_OTHER, "Trasversale", setOf("Speciale")),
        entry("SPECIALITA", "Specialità", "Evento o campo di specialità", "Ragazzi", "E/G", setOf("Spec", "Specialità")),
        simple("SPECIALITA_SQUADRIGLIA", "Specialità Squadriglia", "Ragazzi", "E/G", "SpecSq", "Specialità Squadriglia"),
    )

    private val entriesByAlias = buildMap {
        knownTypes.forEach { definition ->
            (definition.aliases + definition.key + definition.label).forEach { alias ->
                put(normalize(alias), definition)
            }
        }
    }

    fun resolve(rawType: String?): EventTypeDefinition {
        val raw = rawType?.trim().orEmpty()
        entriesByAlias[normalize(raw)]?.let { return it }
        val display = raw.ifBlank { "Senza tipologia" }
        return EventTypeDefinition(
            key = "UNKNOWN:${normalize(display)}",
            label = display,
            description = "Tipologia nuova o non ancora catalogata. Consulta il dettaglio dell’evento.",
            family = "Nuova tipologia",
            scope = "Da determinare",
            known = false,
            aliases = setOf(display),
        )
    }

    fun definitionsFor(events: Iterable<BcEvent>): List<EventTypeDefinition> = events
        .map { resolve(it.type) }
        .distinctBy(EventTypeDefinition::key)
        .sortedWith(compareBy<EventTypeDefinition> { !it.known }.thenBy { it.label.lowercase() })

    fun matches(event: BcEvent, key: String): Boolean = resolve(event.type).key == key

    fun canonicalLabel(value: String): String = resolve(value).let { definition ->
        if (definition.known) definition.label else value.trim()
    }

    fun displayCode(value: String, events: Iterable<BcEvent>): String {
        val definition = resolve(value)
        if (definition.known) return preferredCode(definition)
        val key = definition.key
        return events.asSequence()
            .mapNotNull { it.type?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull { resolve(it).key == key }
            ?: preferredCode(definition)
    }

    fun preferredCode(definition: EventTypeDefinition): String {
        if (!definition.known) return definition.label
        val aliases = definition.aliases.filter(String::isNotBlank)
        val candidates = if (definition.family == FAMILY_MODULES) {
            aliases.filter { it.startsWith("MF", ignoreCase = true) }.ifEmpty { aliases }
        } else {
            aliases
        }
        return candidates.minWithOrNull(compareBy<String> { it.length }.thenBy { it.lowercase() })
            ?: definition.label
    }

    fun notificationCode(value: String): String {
        val definition = resolve(value)
        return if (definition.known) preferredCode(definition) else value.trim()
    }

    fun explanation(value: String, events: Iterable<BcEvent> = emptyList()): String {
        val definition = resolve(value)
        if (definition.known) {
            return definition.description
                .replace(Regex("^MF\\s*-\\s*", RegexOption.IGNORE_CASE), "Modulo formativo – ")
        }

        inferredTitle(value, events)?.let { return "Significato ipotizzato dal titolo: $it" }
        val expanded = value.split(Regex("[^A-Za-z0-9/]+"))
            .mapNotNull { knownAcronyms[it.uppercase(Locale.ROOT)] }
            .distinct()
        return if (expanded.isNotEmpty()) {
            "Significato ipotizzato dalle sigle: ${expanded.joinToString(" · ")}"
        } else {
            definition.description
        }
    }

    private fun inferredTitle(value: String, events: Iterable<BcEvent>): String? = events
        .firstOrNull { it.type.equals(value, ignoreCase = true) }
        ?.title
        ?.substringBefore(" - ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals(value, ignoreCase = true) }
        ?.take(120)

    private val knownAcronyms = mapOf(
        "MF" to "Modulo formativo",
        "MAM" to "Modulo di aggiornamento metodologico",
        "CFM" to "Campo di formazione metodologica",
        "CFA" to "Campo di formazione associativa",
        "CFT" to "Campo di formazione tirocinanti",
        "PC" to "Protezione civile",
        "CG" to CAPI_GRUPPO,
        "QA" to "Quadri associativi",
        "E/G" to "Esploratori e Guide",
        "L/C" to "Lupetti e Coccinelle",
        "R/S" to "Rover e Scolte",
    )

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

