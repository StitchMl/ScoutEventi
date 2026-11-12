package it.buonacaccia.app.data

import java.text.Normalizer
import java.util.Locale

/** Catalogo importato da ZoneAgesci.txt. Le parentesi contengono alias, non nomi di zona. */
object ZoneCatalog {
    data class Zone(val region: String, val name: String, val aliases: List<String>)

    private data class IndexedZone(
        val zone: Zone,
        val regionCode: String?,
        val flattenedNames: List<String>,
    )

    private val combiningMarks = Regex("\\p{M}+")
    private val nonAlphaNumeric = Regex("[^a-z0-9]+")
    private val whitespace = Regex("\\s+")

    private val regionNames = setOf(
        "BASILICATA", "CALABRIA", "CAMPANIA", "EMILIA-ROMAGNA", "FRIULI-VENEZIA-GIULIA",
        "LAZIO", "LIGURIA", "LOMBARDIA", "MARCHE", "PIEMONTE", "TOSCANA", "UMBRIA",
        "VAL D'AOSTA", "VENETO", "PUGLIA", "SARDEGNA", "SICILIA",
    )

    private val source = """
BASILICATA
Lucania
Quattro Fiumi

CALABRIA
Costa dei Gelsomini
Fata Morgana
Reventino
Riviera dei Cedri
Terra del Bergamotto
Tre Colli

CAMPANIA
Caserta
Faito
Felix
Hirpinia
Liternum
Napoli
Poseidonia
Salerno
Samnium
Vesuvio
Volturno

EMILIA-ROMAGNA
Bologna
Carpi
Ferrara
Forlì
Imola
Modena
Modena Pedemontana
Parma
Piacenza
Ravenna
Reggio Emilia
Rimini

FRIULI-VENEZIA-GIULIA
Tagliamento

LAZIO
Auriga
Cassiopea
Castelli
Roma Centro Urbis (Centro Urbis, Andromeda)
La Fenice
Ostiense
Pleiadi
Pontina
Tuscia

LIGURIA
Altavia
Diamante
Ingauna
Ponente
Levante (Val Bisagno)

LOMBARDIA
Bergamo
Brescia
Brianza
Como-Lecco-Sondrio (SoLCo)
Cremona-Lodi
Mantova
Milano
Milano nord
Pavia
Promise
Sebino
Ticino-Olona
Varese

MARCHE
Ancona
Faleria
Macerata

PIEMONTE
Biella-Vercelli
Novara
Torino
Vini

TOSCANA
Arezzo
Costa Etrusca
Firenze
Livorno
Lucca-Massa-Carrara (Lu.Ma.Ca, Lumaca)
Pisa
Pistoia
Prato
Siena

UMBRIA
Etruria
Valli Francescane

VAL D'AOSTA
Val d'Aosta

VENETO
Belluno
Castelfranco
Mestre
Padova Collemare
Padova del Brenta
Rovigo
Scorzè
Treviso
Venezia e Isole
Verona Custoza
Verona Est
Verona Monte Baldo
Vicenza Berica
Vicenza Piccole Dolomiti
Vicenza Tre Valli
Vittorio Veneto

PUGLIA
Lecce Jonica
Messapia
Taranto

SARDEGNA
Cagliari
Iglesias
Nuoro
Oristano
Sassari

SICILIA
Aretusea
Calatina
Castelli Nisseni
Conca d'Oro
Concordia
Castelli Erei
Laghi (dei Laghi)
Eleuterio
Elimi
Erea
Etna Alto Simeto
Etnea
Etnea delle Ginestre
Etnea Liotru
Dei Fenici (Fenici)
Galatea
Iblea
Lilibeo
Megarese
Monrealese
Nebrodi e Madonie
Netina
Torri
Stretto (dello Stretto)
    """.trimIndent()

    val zones: List<Zone> = parse(source)
    private val indexedZones: List<IndexedZone> = zones.map { zone ->
        IndexedZone(
            zone = zone,
            regionCode = BuonaCacciaRegions.codeOf(zone.region),
            flattenedNames = (listOf(zone.name) + zone.aliases).mapNotNull(::flatten),
        )
    }
    private val indexedZonesByRegion = indexedZones.groupBy(IndexedZone::regionCode)
    private val names = zones.map(Zone::name)

    fun allNames(): List<String> = names

    fun zonesForRegion(region: String?): List<String> {
        val code = BuonaCacciaRegions.codeOf(region)
        return if (code == null) names else indexedZonesByRegion[code].orEmpty().map { it.zone.name }
    }

    fun zonesForRegions(regions: Set<String>): List<String> {
        if (regions.isEmpty()) return allNames()
        val codes = regions.mapNotNull(BuonaCacciaRegions::codeOf).toSet()
        return codes.flatMap { code -> indexedZonesByRegion[code].orEmpty() }
            .map { it.zone.name }
    }

    fun canonicalName(raw: String?, region: String? = null): String? =
        identify(raw.orEmpty(), region, allowFuzzy = true)

    fun identify(text: String, region: String? = null, allowFuzzy: Boolean = false): String? {
        val flattenedText = flatten(text) ?: return null
        val regionCode = BuonaCacciaRegions.codeOf(region)
        val candidates = if (regionCode == null) indexedZones else indexedZonesByRegion[regionCode].orEmpty()
        return candidates
            .mapNotNull { zone ->
                matchScore(flattenedText, zone, allowFuzzy)?.let { score -> zone to score }
            }
            .minWithOrNull(
                compareBy<Pair<IndexedZone, Int>> { it.second }
                    .thenByDescending { it.first.zone.name.length },
            )
            ?.first
            ?.zone
            ?.name
    }

    private fun matchScore(text: String, zone: IndexedZone, allowFuzzy: Boolean): Int? =
        zone.flattenedNames.mapNotNull { target ->
            if (containsPhrase(text, target)) return@mapNotNull 0
            if (allowFuzzy) fuzzyDistance(text, target) else null
        }.minOrNull()

    private fun containsPhrase(text: String, target: String): Boolean =
        " $target " in " $text "

    private fun fuzzyDistance(text: String, target: String): Int? {
        val targetWords = target.split(' ')
        val words = text.split(' ')
        val allowed = when {
            target.length >= 6 -> 2
            target.length >= 5 -> 1
            else -> 0
        }
        if (allowed == 0 || words.size < targetWords.size) return null
        return words.windowed(targetWords.size)
            .map { levenshtein(it.joinToString(" "), target, allowed) }
            .filter { it <= allowed }
            .minOrNull()
    }

    private fun levenshtein(left: String, right: String, limit: Int): Int {
        if (kotlin.math.abs(left.length - right.length) > limit) return limit + 1
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == right[rightIndex]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun flatten(value: String?): String? {
        val plain = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return Normalizer.normalize(plain, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(nonAlphaNumeric, " ")
            .trim()
            .replace(whitespace, " ")
            .takeIf(String::isNotEmpty)
    }

    private fun parse(raw: String): List<Zone> {
        var region: String? = null
        return raw.lineSequence().map(String::trim).filter(String::isNotEmpty).mapNotNull { line ->
            if (line in regionNames) {
                region = displayRegion(line)
                return@mapNotNull null
            }
            val currentRegion = region ?: return@mapNotNull null
            val name = line.substringBefore(" (").trim()
            val aliases = line.substringAfter(" (", "").removeSuffix(")")
                .split(',').map(String::trim).filter(String::isNotEmpty)
            Zone(currentRegion, name, aliases)
        }.toList()
    }

    private fun displayRegion(raw: String): String = when (raw) {
        "EMILIA-ROMAGNA" -> "Emilia-Romagna"
        "FRIULI-VENEZIA-GIULIA" -> "Friuli-Venezia Giulia"
        "VAL D'AOSTA" -> "Val d'Aosta"
        else -> raw.lowercase(Locale.ITALY).replaceFirstChar(Char::titlecase)
    }
}
