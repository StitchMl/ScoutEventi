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
    private val definitions = linkedMapOf(
        "L/C" to "Lupetti e Coccinelle",
        "E/G" to "Esploratori e Guide",
        "R/S" to "Rover e Scolte",
        "AE" to "Assistente Ecclesiastico",
        "BC" to "BuonaCaccia",
        "CdA" to "Consiglio degli Anziani",
        "CdL" to "Consiglio della Legge",
        "CP" to "Capo Pattuglia",
        "CSq" to "Capo Squadriglia",
        "EPI" to "Evento a Partecipazione Individuale",
        "CFT" to "Campo di Formazione Tirocinanti",
        "CFM" to "Campo di Formazione Metodologica",
        "CFA" to "Campo di Formazione Associativa",
        "CCG" to "Campo per Capi Gruppo",
        "MF" to "Modulo Formativo",
        "MAM" to "Modulo di Aggiornamento Metodologico",
        "AM" to "Aggiornamento Metodologico",
        "PT" to "Percorso di Tirocinio",
        "INTER" to "Interbranca",
        "AA" to "Approfondimento Associativo",
        "FS" to "Fede e Spiritualità",
        "CG" to "Capi Gruppo",
        "QA" to "Quadri Associativi",
        "TE" to "Tematiche Educative",
        "RTT" to "Regional Training Team",
        "PO" to "Piccole Orme",
        "FA" to "sigla usata dall’organizzatore; espansione non pubblicata su BuonaCaccia",
        "EMI" to "sigla territoriale usata nei titoli; espansione non pubblicata su BuonaCaccia",
        "ROSS" to "Route d’Orientamento alla Scelta di Servizio",
        "EPPPI" to "Eventi di Progressione Personale a Partecipazione Individuale",
        "PC" to "Protezione Civile",
        "CAEX" to "campo per capi da esperienze extra-associative; espansione da verificare",
        "CAM" to "Campo di Aggiornamento Metodologico",
        "HACCP" to "igiene e sicurezza alimentare",
        "IABZ" to "incaricato alla branca di Zona",
        "IABR" to "incaricato alla branca regionale",
        "IABN" to "incaricato alla branca nazionale",
        "RdZ" to "Responsabile di Zona",
        "IdZ" to "Incaricato di Zona",
        "IdR" to "Incaricato regionale",
        "PdC" to "Progetto del Capo",
        "Fo.Ca." to "Formazione Capi",
        "Co.Ca." to "Comunità Capi",
        "II.RR." to "Incaricati Regionali",
    )

    fun entriesFor(events: Iterable<BcEvent>): List<Pair<String, String>> {
        val text = events.joinToString(" ") { listOfNotNull(it.type, it.title).joinToString(" ") }
        val documented = definitions.filterKeys { acronym ->
            Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(acronym)}(?![\\p{L}\\p{N}])")
                .containsMatchIn(text)
        }.toList()
        val documentedKeys = documented.map { it.first.lowercase() }.toSet()
        val unknown = ACRONYM.findAll(text)
            .map { it.value.trim('.', '/', '-') }
            .filter { it.length <= 6 || it.any { char -> char in "./-" } }
            .filter { it.lowercase() !in documentedKeys && it !in IGNORED_WORDS }
            .distinct()
            .sorted()
            .map { it to "Sigla non documentata nel catalogo: verifica il dettaglio dell’evento" }
            .toList()
        return documented + unknown
    }

    private val ACRONYM = Regex("(?<![\\p{L}\\p{N}])[A-ZÀ-ÖØ-Þ][A-ZÀ-ÖØ-Þ0-9./-]{1,11}(?![\\p{L}\\p{N}])")
    private val IGNORED_WORDS = setOf(
        "AGESCI", "ALTRO", "CAMPO", "CAPI", "EVENTO", "FORMAZIONE", "MODULO", "REGIONALE",
    )
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
