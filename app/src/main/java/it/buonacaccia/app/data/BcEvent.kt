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

enum class EventCategory(val label: String) {
    WEBICOMP("WebiComp"),
    MAM("MAM"),
    MODULI_FORMATIVI("Moduli formativi"),
    ALTRI("Altri");

    companion object {
        fun classify(type: String?, title: String): EventCategory {
            val value = listOfNotNull(type, title).joinToString(" ").lowercase()
            return when {
                "webicomp" in value || "web i comp" in value -> WEBICOMP
                Regex("\\bmam\\b").containsMatchIn(value) ||
                    "mf-am" in value || "aggiornam. metodologico" in value ||
                    "aggiornamento metodologico" in value -> MAM
                Regex("\\bmf(?:-|\\b)").containsMatchIn(value) ||
                    "modulo formativo" in value || "moduli formativi" in value -> MODULI_FORMATIVI
                else -> ALTRI
            }
        }
    }
}

fun BcEvent.category(): EventCategory = EventCategory.classify(type, title)

object EventAcronymGlossary {
    private val definitions = linkedMapOf(
        "L/C" to "Lupetti e Coccinelle",
        "E/G" to "Esploratori e Guide",
        "R/S" to "Rover e Scolte",
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
        "Fo.Ca." to "Formazione Capi",
        "Co.Ca." to "Comunità Capi",
        "II.RR." to "Incaricati Regionali",
    )

    fun entriesFor(events: Iterable<BcEvent>): List<Pair<String, String>> {
        val text = events.joinToString(" ") { listOfNotNull(it.type, it.title).joinToString(" ") }
        return definitions.filterKeys { acronym ->
            Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(acronym)}(?![\\p{L}\\p{N}])")
                .containsMatchIn(text)
        }.toList()
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
