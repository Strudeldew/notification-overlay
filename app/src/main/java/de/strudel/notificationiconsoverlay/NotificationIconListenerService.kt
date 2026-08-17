package de.strudel.notificationiconsoverlay

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Receives notification lifecycle and ranking callbacks from Android.
 *
 * This service owns no presentation state. It converts the current system notification snapshot
 * into [NotificationIcon] records in [NotificationIconRepository], which lets the accessibility
 * overlay remain independent from the notification-listener lifecycle.
 */
class NotificationIconListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        refresh()
    }

    override fun onListenerDisconnected() {
        NotificationIconRepository.clear()
        requestRebind(componentName)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        refresh(rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        refresh(rankingMap)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        refresh(rankingMap)
    }

    /**
     * Rebuilds the repository from Android's complete active-notification snapshot.
     *
     * A full rebuild avoids races between individual post/remove callbacks and ranking updates.
     * [updatedRanking] is preferred because callback-provided ranking data may be newer than
     * [currentRanking].
     */
    private fun refresh(updatedRanking: RankingMap? = null) {
        try {
            val notifications = activeNotifications ?: emptyArray()
            NotificationIconRepository.replace(
                notifications = notifications,
                rankingMap = updatedRanking ?: currentRanking,
                ownPackageName = packageName,
                isSystemPackage = ::isSystemPackage,
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification access is not currently available", error)
            NotificationIconRepository.clear()
        }
    }

    /** Returns whether [packageName] belongs to a system or updated-system application. */
    private fun isSystemPackage(packageName: String): Boolean = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private val componentName
        get() = android.content.ComponentName(this, NotificationIconListenerService::class.java)

    companion object {
        private const val TAG = "NotificationOverlay"
    }
}
