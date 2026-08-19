package de.strudel.notificationiconsoverlay

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Executes the narrowly scoped status-bar command as Shizuku's shell user.
 *
 * This class is instantiated in a separate Shizuku user-service process. Its name and public
 * constructor are retained by ProGuard because Shizuku creates it reflectively.
 */
class StatusBarControlUserService : IStatusBarControlService.Stub() {
    /** Stops the non-daemon user-service process when Shizuku asks it to shut down. */
    override fun destroy() = exitProcess(0)

    /**
     * Hides or restores only SystemUI's original notification icons.
     *
     * @return an empty string after a successful command, otherwise a diagnostic message.
     */
    override fun setNotificationIconsHidden(hidden: Boolean): String = try {
        val process = ProcessBuilder(*StatusBarCommand.notificationIcons(hidden))
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            "The Android status-bar command timed out."
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (process.exitValue() == 0) "" else output.ifBlank {
                "The Android status-bar command failed with exit code ${process.exitValue()}."
            }
        }
    } catch (error: Exception) {
        Log.e(TAG, "Could not update stock notification icon visibility", error)
        error.message ?: "The Android status-bar command failed."
    }

    private companion object {
        const val TAG = "StockStatusBarIcons"
        const val COMMAND_TIMEOUT_SECONDS = 5L
    }
}
