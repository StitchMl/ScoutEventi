package it.buonacaccia.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun newerSemanticVersionIsDetected() {
        assertTrue(AppUpdateManager.isNewerVersion("6.2.0", "6.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v7.0.0", "6.9.9"))
    }

    @Test
    fun equalOrOlderVersionIsRejected() {
        assertFalse(AppUpdateManager.isNewerVersion("6.2.0", "6.2.0"))
        assertFalse(AppUpdateManager.isNewerVersion("6.1.9", "6.2.0"))
    }
}
