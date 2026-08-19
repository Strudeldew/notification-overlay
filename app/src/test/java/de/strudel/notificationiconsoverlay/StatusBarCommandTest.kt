package de.strudel.notificationiconsoverlay

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** Verifies the fixed, non-shell-interpreted arguments sent by the Shizuku user service. */
class StatusBarCommandTest {
    @Test
    fun hidesOnlyNotificationIcons() {
        assertArrayEquals(
            arrayOf("cmd", "statusbar", "send-disable-flag", "notification-icons"),
            StatusBarCommand.notificationIcons(hidden = true),
        )
    }

    @Test
    fun restoresStatusBarDisableFlags() {
        assertArrayEquals(
            arrayOf("cmd", "statusbar", "send-disable-flag", "none"),
            StatusBarCommand.notificationIcons(hidden = false),
        )
    }
}
