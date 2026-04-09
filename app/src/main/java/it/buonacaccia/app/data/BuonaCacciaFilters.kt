package it.buonacaccia.app.data

import java.util.Locale

enum class BuonaCacciaSort(val code: String) {
    BY_TYPE("C"),
    BY_DATE("D"),
    BY_TITLE("T");

    companion object {
        fun fromCode(code: String?): BuonaCacciaSort =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: BY_DATE
    }
}

data class BuonaCacciaFilter(
    val regionCode: String? = null,
    val categoryCode: String? = null,
    val sort: BuonaCacciaSort = BuonaCacciaSort.BY_DATE
) {
    fun toQueryParams(): Map<String, String> = buildMap {
        regionCode?.let { put("RID", it) }
        categoryCode?.let { put("CID", it) }
        put("SID", sort.code)
    }
}

object BuonaCacciaRegions {
    private val regionCatalog = listOf(
        RegionEntry(code = "A", canonicalName = "Nazionale", aliases = listOf("nazionale")),
        RegionEntry(code = "B", canonicalName = "Abruzzo", aliases = listOf("abruzzo")),
        RegionEntry(code = "C", canonicalName = "Basilicata", aliases = listOf("basilicata")),
        RegionEntry(code = "D", canonicalName = "Calabria", aliases = listOf("calabria")),
        RegionEntry(code = "E", canonicalName = "Campania", aliases = listOf("campania")),
        RegionEntry(
            code = "F",
            canonicalName = "Emilia-Romagna",
            aliases = listOf("emilia romagna", "emiro")
        ),
        RegionEntry(
            code = "G",
            canonicalName = "Friuli-Venezia Giulia",
            aliases = listOf("friuli venezia giulia", "fvg")
        ),
        RegionEntry(code = "H", canonicalName = "Lazio", aliases = listOf("lazio")),
        RegionEntry(code = "I", canonicalName = "Liguria", aliases = listOf("liguria")),
        RegionEntry(code = "L", canonicalName = "Lombardia", aliases = listOf("lombardia")),
        RegionEntry(code = "M", canonicalName = "Marche", aliases = listOf("marche")),
        RegionEntry(code = "N", canonicalName = "Molise", aliases = listOf("molise")),
        RegionEntry(code = "O", canonicalName = "Piemonte", aliases = listOf("piemonte")),
        RegionEntry(code = "P", canonicalName = "Puglia", aliases = listOf("puglia")),
        RegionEntry(code = "Q", canonicalName = "Sardegna", aliases = listOf("sardegna")),
        RegionEntry(code = "R", canonicalName = "Sicilia", aliases = listOf("sicilia")),
        RegionEntry(code = "S", canonicalName = "Toscana", aliases = listOf("toscana")),
        RegionEntry(
            code = "T",
            canonicalName = "Trentino-Alto Adige",
            aliases = listOf("trentino alto adige", "trentino", "alto adige", "taa")
        ),
        RegionEntry(code = "U", canonicalName = "Umbria", aliases = listOf("umbria")),
        RegionEntry(
            code = "V",
            canonicalName = "Valle d'Aosta",
            aliases = listOf("valle d aosta", "val d aosta", "vda", "valdaosta")
        ),
        RegionEntry(code = "Z", canonicalName = "Veneto", aliases = listOf("veneto"))
    )
    private val entriesByNormalizedName = regionCatalog
        .flatMap { entry -> entry.aliases.map { alias -> normalize(alias)!! to entry } }
        .toMap()

    fun codeOf(regionName: String?): String? {
        val normalized = normalize(regionName) ?: return null
        return entriesByNormalizedName[normalized]?.code
    }

    fun canonicalNameOf(regionName: String?): String? {
        val normalized = normalize(regionName) ?: return null
        return entriesByNormalizedName[normalized]?.canonicalName
    }

    fun firstCanonicalNameIn(text: String?): String? {
        val normalizedText = normalize(text) ?: return null
        val paddedText = " $normalizedText "
        return entriesByNormalizedName.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { (alias, _) -> paddedText.contains(" $alias ") }
            ?.value
            ?.canonicalName
    }

    fun filterOf(
        regionName: String?,
        sort: BuonaCacciaSort = BuonaCacciaSort.BY_DATE
    ): BuonaCacciaFilter? {
        val code = codeOf(regionName) ?: return null
        return BuonaCacciaFilter(regionCode = code, sort = sort)
    }

    fun allFilters(sort: BuonaCacciaSort = BuonaCacciaSort.BY_DATE): List<BuonaCacciaFilter> =
        regionCatalog.map { entry ->
            BuonaCacciaFilter(regionCode = entry.code, sort = sort)
        }

    private fun normalize(regionName: String?): String? {
        val trimmed = regionName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace("valdaosta", "valle d aosta")
            .replace(Regex("\\s+"), " ")
            .replace("val d aosta", "valle d aosta")
            .trim()
    }

    private data class RegionEntry(
        val code: String,
        val canonicalName: String,
        val aliases: List<String>
    )
}

object BuonaCacciaCategories {
    private val codesByBranch = mapOf(
        Branch.LC to "1000000",
        Branch.EG to "2000000",
        Branch.RS to "3000000",
        Branch.CAPI to "4000000"
    )

    fun codeOf(branch: Branch?): String? = branch?.let { codesByBranch[it] }
}

object BuonaCacciaScopes {
    fun filterOf(
        regionName: String? = null,
        branch: Branch? = null,
        sort: BuonaCacciaSort = BuonaCacciaSort.BY_DATE
    ): BuonaCacciaFilter? {
        val regionCode = BuonaCacciaRegions.codeOf(regionName)
        val categoryCode = BuonaCacciaCategories.codeOf(branch)

        if (regionCode == null && categoryCode == null) {
            return null
        }

        return BuonaCacciaFilter(
            regionCode = regionCode,
            categoryCode = categoryCode,
            sort = sort
        )
    }

    fun regionFiltersOf(
        regionNames: Iterable<String?>,
        sort: BuonaCacciaSort = BuonaCacciaSort.BY_DATE
    ): List<BuonaCacciaFilter> {
        val seenRegionCodes = LinkedHashSet<String>()
        val filters = mutableListOf<BuonaCacciaFilter>()

        regionNames.forEach { regionName ->
            val filter = BuonaCacciaRegions.filterOf(regionName, sort) ?: return@forEach
            val regionCode = filter.regionCode ?: return@forEach
            if (seenRegionCodes.add(regionCode)) {
                filters += filter
            }
        }

        return filters
    }

    fun unmappedRegionsOf(regionNames: Iterable<String?>): Set<String> =
        regionNames
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .filter { BuonaCacciaRegions.codeOf(it) == null }
            .toCollection(linkedSetOf())

    fun fallbackFiltersForQueryParams(queryParams: Map<String, String>): List<BuonaCacciaFilter>? {
        val normalizedKeys = queryParams.mapKeys { it.key.uppercase(Locale.ROOT) }
        val regionCode = normalizedKeys["RID"]?.trim()?.ifBlank { null }
        val categoryCode = normalizedKeys["CID"]?.trim()?.ifBlank { null }
        val sort = BuonaCacciaSort.fromCode(normalizedKeys["SID"])

        if (regionCode != null) {
            return null
        }

        val regionFilters = BuonaCacciaRegions.allFilters(sort)
        if (categoryCode == null) {
            return regionFilters
        }

        return regionFilters.map { base ->
            base.copy(categoryCode = categoryCode)
        }
    }
}
