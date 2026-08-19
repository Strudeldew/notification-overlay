package de.strudel.notificationiconsoverlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards privacy-sensitive configuration defaults. */
class OverlayConfigTest {
    @Test
    fun screenshotFallbackIsOptInByDefault() {
        assertFalse(OverlayConfig.DEFAULT_SCREENSHOT_FALLBACK_ENABLED)
    }

    @Test
    fun stockNotificationIconsAreHiddenByDefault() {
        assertTrue(OverlayConfig.DEFAULT_HIDE_STOCK_NOTIFICATION_ICONS)
    }
}
