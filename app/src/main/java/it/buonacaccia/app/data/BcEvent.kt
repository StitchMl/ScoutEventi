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
    ZoneCatalog.normalize(zone)?.let { return it }
    ZoneCatalog.fromTitle(title)?.let { return it }
    val locationValue = location ?: return null
    val parenthesized = Regex("\\(([^)]+)\\)")
        .find(locationValue)
        ?.groupValues
        ?.getOrNull(1)
    val candidate = ZoneCatalog.normalize(parenthesized) ?: return null
    if (candidate.length <= 2 && candidate.all { it.isUpperCase() || it.isLetter() }) return null
    return candidate
}

object ZoneCatalog {
    private val separator = Regex("\\s+(?:[-\\u2013\\u2014|])\\s+|_")
    private val whitespace = Regex("\\s+")

    fun normalize(raw: String?): String? {
        val cleaned = raw
            ?.replace('\u00A0', ' ')
            ?.replace(whitespace, " ")
            ?.trim()
            ?.replaceFirst(Regex("(?i)^zona\\s+"), "")
            ?.split(separator, limit = 2)
            ?.firstOrNull()
            ?.trim(' ', ':', ';', ',', '.', '-', '(', ')')
            ?.takeIf { it.length >= 3 }
            ?: return null

        if (cleaned.equals("da definire", ignoreCase = true) ||
            cleaned.equals("non definita", ignoreCase = true)
        ) return null

        val readableCase = if (cleaned.any(Char::isLetter) &&
            cleaned.filter(Char::isLetter).all(Char::isUpperCase)
        ) {
            cleaned.lowercase().split(' ').joinToString(" ") { word ->
                word.replaceFirstChar(Char::titlecase)
            }
        } else {
            cleaned
        }
        val normalizedApostrophe = readableCase.replace(
            Regex("(?i)\\bd(['\\u2019])([a-zà-ÿ])"),
        ) { match -> "d${match.groupValues[1]}${match.groupValues[2].uppercase()}" }

        return normalizedApostrophe.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }

    fun fromTitle(title: String): String? {
        val match = Regex(
            "(?i)\\bzona\\s+(.+?)(?=\\s+(?:[-\\u2013\\u2014|])\\s+|_|$)",
        ).find(title) ?: return null
        return normalize(match.groupValues.getOrNull(1))
    }
}
