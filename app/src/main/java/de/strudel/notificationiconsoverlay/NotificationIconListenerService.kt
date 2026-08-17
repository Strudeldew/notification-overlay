package de.strudel.notificationiconsoverlay

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

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
