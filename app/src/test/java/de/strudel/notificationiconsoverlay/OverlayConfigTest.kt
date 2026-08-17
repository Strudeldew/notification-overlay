package de.strudel.notificationiconsoverlay

import org.junit.Assert.assertFalse
import org.junit.Test

class OverlayConfigTest {
    @Test
    fun screenshotFallbackIsOptInByDefault() {
        assertFalse(OverlayConfig.DEFAULT_SCREENSHOT_FALLBACK_ENABLED)
    }
}
