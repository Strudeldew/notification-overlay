package de.strudel.notificationiconsoverlay

import org.junit.Assert.assertFalse
import org.junit.Test

/** Guards privacy-sensitive configuration defaults. */
class OverlayConfigTest {
    @Test
    fun screenshotFallbackIsOptInByDefault() {
        assertFalse(OverlayConfig.DEFAULT_SCREENSHOT_FALLBACK_ENABLED)
    }
}
