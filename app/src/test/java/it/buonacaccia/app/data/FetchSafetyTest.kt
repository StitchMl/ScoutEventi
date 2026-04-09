package it.buonacaccia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FetchSafetyTest {

    @Test
    fun minimumExpectedCountForFullDataset_isDisabledForSmallReferenceSnapshots() {
        assertNull(FetchSafety.minimumExpectedCountForFullDataset(0))
        assertNull(FetchSafety.minimumExpectedCountForFullDataset(12))
        assertNull(FetchSafety.minimumExpectedCountForFullDataset(23))
    }

    @Test
    fun minimumExpectedCountForFullDataset_scalesWithReferenceSnapshot() {
        assertEquals(10, FetchSafety.minimumExpectedCountForFullDataset(24))
        assertEquals(10, FetchSafety.minimumExpectedCountForFullDataset(30))
        assertEquals(11, FetchSafety.minimumExpectedCountForFullDataset(31))
        assertEquals(20, FetchSafety.minimumExpectedCountForFullDataset(60))
    }
}
