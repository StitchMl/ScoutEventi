package it.buonacaccia.app.data

import java.time.LocalDate

data class BcEvent(
    val id: String?,
    val type: String?,
    val title: String,
    val region: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val fee: String?,
    val location: String?,
    val enrolled: String?,
    val status: String?,
    val detailUrl: String,
    val statusColor: String? = null,
    val branch: Branch? = null,
    val subsOpenDate: LocalDate? = null,
    val subsCloseDate: LocalDate? = null,
    val zone: String? = null,
)

object EventAcronymGlossary {
    private val typeMeanings = mapOf(
        "AltroReg" to "Altro evento regionale",
        "AltroRS" to "Altre attività R/S",
        "AssReg" to "Assemblea regionale",
        "AssZona" to "Assemblea di Zona",
        "CapiSq" to "Evento per capi squadriglia E/G",
        "CCG" to "Campo per Capi Gruppo",
        "CFA" to "Campo di Formazione Associativa",
        "CFM E/G" to "Campo di Formazione Metodologica E/G",
        "CFM L/C" to "Campo di Formazione Metodologica L/C",
        "CFM R/S" to "Campo di Formazione Metodologica R/S",
        "CFT" to "Campo di Formazione Tirocinanti",
        "ConsReg" to "Consiglio regionale",
        "EPPPI" to "Evento di Progressione Personale a Partecipazione Individuale",
        "MF-AA" to "Modulo formativo di approfondimento associativo",
        "MF-AB" to "Modulo formativo di approfondimento biblico",
        "MF-AM E/G" to "Modulo di aggiornamento metodologico E/G",
        "MF-AM L/C" to "Modulo di aggiornamento metodologico L/C",
        "MF-AM R/S" to "Modulo di aggiornamento metodologico R/S",
        "MF-CG" to "Modulo formativo per Capi Gruppo",
        "MF-CT" to "Modulo formativo sulle competenze tecniche",
        "MF-EVC" to "Modulo formativo sulla vita cristiana",
        "MF-Form" to "Modulo formativo per formatori",
        "MF-FS" to "Modulo formativo su fede e spiritualità",
        "MF-INTER" to "Modulo formativo interbranca",
        "MF-PAS" to "Modulo su percorsi associativi specifici",
        "MF-PC" to "Modulo formativo di protezione civile",
        "MF-PT" to "Modulo formativo sul percorso di tirocinio",
        "MF-QA" to "Modulo formativo per quadri associativi",
        "MF-TE" to "Modulo formativo su tematiche educative",
        "MF-TS" to "Modulo formativo sul Terzo settore",
        "MF-VPC" to "Modulo per volontari di protezione civile",
        "MF-VPCFI" to "Modulo per volontari PC formati e informati, D.Lgs. 81/08",
        "Part" to "Evento per partenti R/S",
        "PO" to "Piccole Orme",
        "ROSS" to "Route di Orientamento alla Scelta di Servizio",
        "RTT" to "Regional Training Team",
        "StartAssit" to "Evento Start Assistant per formatori",
    )

    fun entriesFor(events: Iterable<BcEvent>): List<Pair<String, String>> {
        val eventList = events.toList()
        return eventList
            .mapNotNull { it.type?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy(String::lowercase)
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .map { type -> type to (typeMeanings[type] ?: inferMeaning(type, eventList)) }
    }

    private fun inferMeaning(type: String, events: List<BcEvent>): String {
        val definition = EventTypeCatalog.resolve(type)
        if (definition.known) return definition.description
        return events.firstOrNull { it.type.equals(type, ignoreCase = true) }
            ?.title
            ?.substringBefore(" - ")
            ?.takeIf { it.length > type.length }
            ?: "Tipologia BuonaCaccia $type"
    }
}

fun BcEvent.guessZone(): String? {
    ZoneCatalog.canonicalName(zone, region)?.let { return it }
    ZoneCatalog.identify(
        text = title,
        region = region,
        allowFuzzy = Regex("(?i)\\bzona\\b").containsMatchIn(title),
    )?.let { return it }
    return ZoneCatalog.identify(location.orEmpty(), region)
}
