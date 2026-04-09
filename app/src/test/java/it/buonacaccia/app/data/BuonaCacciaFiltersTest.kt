package it.buonacaccia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuonaCacciaFiltersTest {

    @Test
    fun regionCodeOf_supportsCanonicalAndNormalizedNames() {
        assertEquals("G", BuonaCacciaRegions.codeOf("Friuli-Venezia Giulia"))
        assertEquals("F", BuonaCacciaRegions.codeOf("Emilia Romagna"))
        assertEquals("T", BuonaCacciaRegions.codeOf("Trentino-Alto Adige"))
        assertEquals("V", BuonaCacciaRegions.codeOf("Val d'Aosta"))
    }

    @Test
    fun regionCodeOf_returnsNullForUnknownNames() {
        assertNull(BuonaCacciaRegions.codeOf(null))
        assertNull(BuonaCacciaRegions.codeOf(""))
        assertNull(BuonaCacciaRegions.codeOf("Atlantide"))
    }

    @Test
    fun filterToQueryParams_usesOfficialBuonaCacciaKeys() {
        val filter = BuonaCacciaFilter(
            regionCode = "H",
            categoryCode = "1010102",
            sort = BuonaCacciaSort.BY_TITLE
        )

        assertEquals(
            mapOf("RID" to "H", "CID" to "1010102", "SID" to "T"),
            filter.toQueryParams()
        )
    }

    @Test
    fun categoryCodeOf_mapsBroadBranchCodes() {
        assertEquals("1000000", BuonaCacciaCategories.codeOf(Branch.LC))
        assertEquals("2000000", BuonaCacciaCategories.codeOf(Branch.EG))
        assertEquals("3000000", BuonaCacciaCategories.codeOf(Branch.RS))
        assertEquals("4000000", BuonaCacciaCategories.codeOf(Branch.CAPI))
        assertNull(BuonaCacciaCategories.codeOf(null))
    }

    @Test
    fun scopeFilterOf_combinesRegionAndBranch() {
        val filter = BuonaCacciaScopes.filterOf(
            regionName = "Lazio",
            branch = Branch.LC,
            sort = BuonaCacciaSort.BY_DATE
        )

        assertEquals(
            mapOf("RID" to "H", "CID" to "1000000", "SID" to "D"),
            filter?.toQueryParams()
        )
    }

    @Test
    fun regionFiltersOf_deduplicatesAndSkipsUnknown() {
        val filters = BuonaCacciaScopes.regionFiltersOf(
            listOf("Lazio", "Lazio", "Emilia-Romagna", "Atlantide")
        )

        assertEquals(
            listOf(
                mapOf("RID" to "H", "SID" to "D"),
                mapOf("RID" to "F", "SID" to "D")
            ),
            filters.map { it.toQueryParams() }
        )
    }

    @Test
    fun unmappedRegionsOf_returnsOnlyUnknownRegions() {
        assertEquals(
            linkedSetOf("Atlantide"),
            BuonaCacciaScopes.unmappedRegionsOf(listOf("Lazio", "Atlantide", "Emilia Romagna"))
        )
    }

    @Test
    fun fallbackFiltersForQueryParams_rebuildsFullDatasetByRegions() {
        val filters = BuonaCacciaScopes.fallbackFiltersForQueryParams(emptyMap())

        assertEquals(21, filters?.size)
        assertEquals(
            mapOf("RID" to "A", "SID" to "D"),
            filters?.firstOrNull()?.toQueryParams()
        )
        assertEquals(
            mapOf("RID" to "Z", "SID" to "D"),
            filters?.lastOrNull()?.toQueryParams()
        )
    }

    @Test
    fun fallbackFiltersForQueryParams_keepsCategoryAndSkipsAlreadyRegionalizedQueries() {
        val categoryFallback = BuonaCacciaScopes.fallbackFiltersForQueryParams(
            mapOf("CID" to "3000000", "SID" to "T")
        )

        assertEquals(21, categoryFallback?.size)
        assertEquals(
            mapOf("RID" to "A", "CID" to "3000000", "SID" to "T"),
            categoryFallback?.firstOrNull()?.toQueryParams()
        )

        val directRegionQuery = BuonaCacciaScopes.fallbackFiltersForQueryParams(
            mapOf("RID" to "H", "CID" to "3000000", "SID" to "D")
        )

        assertNull(directRegionQuery)
    }
}
