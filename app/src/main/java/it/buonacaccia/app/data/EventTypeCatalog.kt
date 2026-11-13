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
    private const val CIVIL_PROTECTION = "Protezione civile"
    private const val CFM_EG = "CFM E/G"
    private const val CFM_LC = "CFM L/C"
    private const val CFM_RS = "CFM R/S"
    private const val CAPI_GRUPPO = "Capi Gruppo"

    val notificationCodes: Set<String> = linkedSetOf(
        "AltroReg", "AltroRS", "AssReg", "AssZona", "CapiSq", "CCG", "CFA",
        CFM_EG, CFM_LC, CFM_RS, "CFT", "ConsReg", "EPPPI", "MF-AA",
        "MF-AB", "MF-AM E/G", "MF-AM L/C", "MF-AM R/S", "MF-CG", "MF-CT",
        "MF-EVC", "MF-Form", "MF-FS", "MF-INTER", "MF-PAS", "MF-PC", "MF-PT",
        "MF-QA", "MF-TE", "MF-TS", "MF-VPC", "MF-VPCFI", "Part", "PO", "ROSS",
        "RTT", "StartAssit", "WebiComp",
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
        simple("ALTRO_DI_ZONA", "Altro evento di Zona", FAMILY_OTHER, "Zona", "Altro di zona"),
        entry("ALTRO_E_G", "Altre attività E/G", "Altre attività per esploratori e guide", "Ragazzi", "E/G", setOf("Altro E/G", "E/G")),
        entry("ALTRO_L_C", "Altre attività L/C", "Altre attività per lupetti e coccinelle", "Ragazzi", "L/C", setOf("Altro L/C", "L/C")),
        simple("ALTRO_NAZIONALE", "Altro evento nazionale", FAMILY_OTHER, "Nazionale", "Altro nazionale"),
        simple("ALTROREG", "Altro evento regionale", FAMILY_OTHER, "Regione", "AltroReg", "Altro regionale"),
        entry("ALTRORS", "Altre attività R/S", "Altre attività per rover e scolte", "Ragazzi", "R/S", setOf("AltroRS", "Altro R/S", "R/S")),
        simple("AREA_TRAINING_TEAM", "Area Training Team", FAMILY_TRAINERS, "Area", "Area Training Team"),
        simple("ASSEMBLEA_DI_ZONA", "Assemblea di Zona", FAMILY_GOVERNANCE, "Zona", "Assemblea di zona", "AssZona"),
        simple("ASSREG", "Assemblea regionale", FAMILY_GOVERNANCE, "Regione", "AssReg", "Assemblea regionale"),
        entry("CAEX", "CAEX", "Campo per capi provenienti da esperienze extra-associative; espansione da verificare", FAMILY_LEADERS, "Trasversale", setOf("CAEX")),
        entry("CAM_E_G", "CAM E/G", "Campo di aggiornamento metodologico E/G", FAMILY_LEADERS, "E/G", setOf("CAM E/G")),
        entry("CAM_L_C", "CAM L/C", "Campo di aggiornamento metodologico L/C", FAMILY_LEADERS, "L/C", setOf("CAM L/C")),
        entry("CAM_R_S", "CAM R/S", "Campo di aggiornamento metodologico R/S", FAMILY_LEADERS, "R/S", setOf("CAM R/S")),
        simple("CAMPI_R_S_ESTERO", "Campi R/S estero", "Ragazzi", "R/S", "Campi R/S estero"),
        simple("CANDIDATURE_COMPETENZA", "Candidatura a campo di competenza", "Ragazzi", "E/G", "Candidature Competenza"),
        simple("CAPI_IN_E_G", "Eventi per capi E/G", FAMILY_LEADERS, "E/G", "Capi in E/G"),
        simple("CAPI_IN_L_C", "Eventi per capi L/C", FAMILY_LEADERS, "L/C", "Capi in L/C"),
        simple("CAPI_IN_R_S", "Eventi per capi R/S", FAMILY_LEADERS, "R/S", "Capi in R/S"),
        entry("CAPISQ", "Capi Squadriglia", "Campo o evento per capi squadriglia", "Ragazzi", "E/G", setOf("CapiSq", "Capi Squadriglia", "CSq", "C.Sq.")),
        simple("CCG", "Campo capi Gruppo", FAMILY_LEADERS, CAPI_GRUPPO, "CCG"),
        entry("CFA", "CFA", "Campo di formazione associativa", FAMILY_LEADERS, "Trasversale", setOf("CFA")),
        entry("CFM_E_G", CFM_EG, "Campo di formazione metodologica E/G", FAMILY_LEADERS, "E/G", setOf(CFM_EG)),
        entry("CFM_L_C", CFM_LC, "Campo di formazione metodologica L/C", FAMILY_LEADERS, "L/C", setOf(CFM_LC)),
        entry("CFM_R_S", CFM_RS, "Campo di formazione metodologica R/S", FAMILY_LEADERS, "R/S", setOf(CFM_RS)),
        entry("CFT", "CFT", "Campo di formazione tirocinanti", FAMILY_LEADERS, "Trasversale", setOf("CFT")),
        simple("COMPETENZA", "Campo di competenza", "Ragazzi", "E/G", "Competenza"),
        simple("CONSIGLIO_GENERALE", "Consiglio generale", FAMILY_GOVERNANCE, "Nazionale", "Consiglio generale"),
        simple("CONSIGLIO_NAZIONALE", "Consiglio nazionale", FAMILY_GOVERNANCE, "Nazionale", "Consiglio nazionale"),
        simple("CONSIGLIO_REGIONALE", "Consiglio regionale", FAMILY_GOVERNANCE, "Regione", "Consiglio regionale", "ConsReg"),
        simple("CONVEGNO_REGIONALE", "Convegno regionale", FAMILY_GOVERNANCE, "Regione", "Convegno regionale"),
        entry("EPPPI", "EPPPI", "Eventi di progressione personale a partecipazione individuale", "Ragazzi", "R/S", setOf("EPPPI", "EPI")),
        simple("EVENTI_FEDE", "Eventi di fede", "Formazione e spiritualità", "Fede", "Eventi Fede"),
        simple("EVENTI_PER_FORMATORI", "Eventi per formatori", FAMILY_TRAINERS, "Formatori", "Eventi per Formatori"),
        entry("EVENTO_START_AIUTI", "Evento Start Aiuti", "Evento per aiuti dei campi scuola; denominazione tecnica BuonaCaccia", FAMILY_TRAINERS, "Formatori", setOf("Evento Start Aiuti")),
        entry("EVENTO_START_ASSISTANT", "Evento Start Assistant", "Evento per assistant dei campi scuola; denominazione tecnica BuonaCaccia", FAMILY_TRAINERS, "Formatori", setOf("Evento Start Assistant", "StartAssit")),
        entry("FORMAZIONE_CAPI", FAMILY_LEADERS, "Categoria generale di formazione capi", FAMILY_LEADERS, "Trasversale", setOf("Formazione Capi")),
        simple("FORMAZIONE_PERMANENTE", "Formazione permanente", FAMILY_LEADERS, "Trasversale", "Formazione Permanente"),
        simple("GUIDONCINI_VERDI", "Guidoncini verdi", "Ragazzi", "E/G", "Guidoncini verdi"),
        simple("INCONTRO_CAPI_CAMPO", "Incontro capi campo", FAMILY_TRAINERS, "Formatori", "Incontro Capi Campo"),
        simple("LABORATORI", "Laboratori", "Ragazzi", "R/S / trasversale", "Laboratori"),
        simple("MAESTRO_DI_COMPETENZA", "Maestro di competenza", "Ragazzi", "E/G", "Maestro di competenza"),
        simple("MF_APPROFONDIM_ASSOCIATIVO", "MF - Approfondimento associativo", FAMILY_MODULES, "Trasversale", "MF- Approfondim. associativo", "MF-AA"),
        simple("MF_PRIMO_SOCCORSO", "MF - Primo soccorso", FAMILY_MODULES, "Trasversale", "MF- Primo soccorso"),
        entry("MF_AGGIORNAM_METODOLOGICO_E_G", "MAM E/G", "Modulo di aggiornamento metodologico E/G", FAMILY_MODULES, "E/G", setOf("MF-Aggiornam. metodologico E/G", "MF-AM E/G", "MAM E/G")),
        entry("MF_AGGIORNAM_METODOLOGICO_L_C", "MAM L/C", "Modulo di aggiornamento metodologico L/C", FAMILY_MODULES, "L/C", setOf("MF-Aggiornam. metodologico L/C", "MF-AM L/C", "MAM L/C")),
        entry("MF_AGGIORNAM_METODOLOGICO_R_S", "MAM R/S", "Modulo di aggiornamento metodologico R/S", FAMILY_MODULES, "R/S", setOf("MF-Aggiornam. metodologico R/S", "MF-AM R/S", "MAM R/S")),
        simple("MF_APPROFONDIMENTO_BIBLICO", "MF - Approfondimento biblico", FAMILY_MODULES, "Fede", "MF-Approfondimento Biblico", "MF-AB"),
        entry("MF_ASS_ANIMATORI_DI_GRUPPO", "MF - Assistenti/animatori di gruppo", "Interpretazione del codice da verificare nel dettaglio dell’evento", FAMILY_MODULES, "Gruppo", setOf("MF-Ass./Animatori di gruppo")),
        simple("MF_CAPI_GRUPPO", "MF - Capi Gruppo", FAMILY_MODULES, CAPI_GRUPPO, "MF-Capi Gruppo", "MF-CG"),
        simple("MF_COMPETENZE_TECNICHE", "MF - Competenze tecniche", FAMILY_MODULES, "Trasversale", "MF-Competenze tecniche", "MF-CT"),
        simple("MF_FEDE_SPIRITUALITA", "MF - Fede e spiritualità", FAMILY_MODULES, "Fede", "MF-Fede/Spiritualità", "MF-Fede", "MF-FS"),
        entry("MF_HACCP", "MF - HACCP", "Modulo su igiene e sicurezza alimentare", FAMILY_MODULES, "Sicurezza", setOf("MF-HACCP")),
        simple("MF_INTERBRANCA", "MF - Interbranca", FAMILY_MODULES, "Trasversale", "MF-Interbranca", "MF-INTER"),
        simple("MF_PERCORSI_ASS_VI_SPECIFICI", "MF - Percorsi associativi specifici", FAMILY_MODULES, "Trasversale", "MF-Percorsi ass.vi specifici", "MF-PAS"),
        simple("MF_PERCORSO_DI_TIROCINIO", "MF - Percorso di tirocinio", FAMILY_MODULES, "Tirocinanti", "MF-Percorso di tirocinio", "MF-PT", "PT"),
        simple("MF_PREVENZIONE_INCENDI", "MF - Prevenzione incendi", FAMILY_MODULES, "Sicurezza", "MF-Prevenzione incendi"),
        simple("MF_PROTEZIONE_CIVILE", "MF - Protezione civile", FAMILY_MODULES, CIVIL_PROTECTION, "MF-Protezione civile", "MF-PC"),
        simple("MF_QUADRI_ASSOCIATIVI", "MF - Quadri associativi", FAMILY_MODULES, "Quadri", "MF-Quadri associativi", "MF-QA"),
        simple("MF_TEMATICHE_EDUCATIVE", "MF - Tematiche educative", FAMILY_MODULES, "Trasversale", "MF-Tematiche educative", "MF-TE"),
        simple("MF_TERZO_SETTORE", "MF - Terzo settore", FAMILY_MODULES, "Trasversale", "MF-Terzo settore", "MF-TS"),
        simple("MF_TUTELA_MINORI", "MF - Tutela dei minori", FAMILY_MODULES, "Tutela", "MF-Tutela minori"),
        simple("MF_VITA_CRISTIANA", "MF - Vita cristiana", FAMILY_MODULES, "Fede", "MF-Vita Cristiana", "MF-EVC"),
        simple("MF_VOLONTARI_PC", "MF - Volontari di protezione civile", FAMILY_MODULES, CIVIL_PROTECTION, "MF-Volontari PC", "MF-VPC"),
        entry("MF_VOLONTARI_PC_FORM_ADDESTRATORI_D_LGS_81_08", "MF - PC: formazione addestratori", "Formazione addestratori ai sensi del D.Lgs. 81/2008", FAMILY_MODULES, CIVIL_PROTECTION, setOf("MF-Volontari PC-Form. addestratori(D.LGS 81/08)")),
        entry("MF_VOLONTARI_PC_FORMATI_INFORMATI_D_LGS_81_08", "MF - PC: formati e informati", "Formazione e informazione ai sensi del D.Lgs. 81/2008", FAMILY_MODULES, CIVIL_PROTECTION, setOf("MF-Volontari PC-Formati informati(D.LGS 81/08)", "MF-VPCFI")),
        entry("MODULO_FORMATIVO", "Modulo formativo", "Categoria generale; sottotipo non specificato", FAMILY_MODULES, "Trasversale", setOf("Modulo Formativo", "MF")),
        simple("MODULO_PER_FORMATORI", "Modulo per formatori", FAMILY_MODULES, "Formatori", "Modulo per formatori", "MF-Form"),
        simple("NATIONAL_TRAINING_TEAM", "National Training Team", FAMILY_TRAINERS, "Nazionale", "National Training Team", "NTT"),
        entry("PART", "Partenti", "Eventi per partenti", "Ragazzi", "R/S", setOf("Part", "Partenti")),
        simple("PICCOLE_TRACCE", "Piccole Tracce", "Ragazzi", "L/C", "Piccole Tracce"),
        simple("PO", "Piccole Orme", "Ragazzi", "L/C", "PO", "Piccole Orme"),
        simple("REGIONAL_TRAINING_TEAM", "Regional Training Team", FAMILY_TRAINERS, "Regione", "Regional Training Team", "RTT"),
        entry("ROSS", "ROSS", "Route di orientamento alla scelta di servizio", "Ragazzi", "R/S", setOf("ROSS")),
        entry("SPECIALE", "Evento speciale", "Categoria generica BuonaCaccia", FAMILY_OTHER, "Trasversale", setOf("Speciale")),
        entry("SPECIALITA", "Specialità", "Evento o campo di specialità", "Ragazzi", "E/G", setOf("Specialità")),
        simple("SPECIALITA_SQUADRIGLIA", "Specialità di squadriglia", "Ragazzi", "E/G", "Specialità Squadriglia"),
        simple("WORKSHOP_CAPI_CAMPO", "Workshop capi campo", FAMILY_TRAINERS, "Formatori", "Workshop Capi Campo"),
        entry("WEBICOMP", "WebiComp", "Evento online sulle competenze", FAMILY_LEADERS, "Online / nazionale", setOf("WebiComp", "WEBICOMP", "Web i Comp")),
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

