package de.strudel.notificationiconsoverlay

import android.graphics.drawable.Icon
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArraySet

data class NotificationIcon(
    val key: String,
    val packageName: String,
    val icon: Icon,
    val rank: Int,
    val isSilent: Boolean,
    val isSystem: Boolean,
)

object NotificationIconRepository {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile
    private var currentIcons: List<NotificationIcon> = emptyList()

    fun snapshot(): List<NotificationIcon> = currentIcons

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun replace(
        notifications: Array<StatusBarNotification>,
        rankingMap: NotificationListenerService.RankingMap,
        ownPackageName: String,
        isSystemPackage: (String) -> Boolean,
    ) {
        currentIcons = notifications.mapNotNull { item ->
            val smallIcon = item.notification.smallIcon ?: return@mapNotNull null
            if (item.packageName == ownPackageName && item.id != TestNotification.NOTIFICATION_ID) {
                return@mapNotNull null
            }

            val ranking = NotificationListenerService.Ranking()
            val hasRanking = rankingMap.getRanking(item.key, ranking)
            val rank = if (hasRanking) ranking.rank else Int.MAX_VALUE
            val isSilent = hasRanking &&
                (ranking.isAmbient || ranking.importance < NotificationManager.IMPORTANCE_DEFAULT)
            NotificationIcon(
                key = item.key,
                packageName = item.packageName,
                icon = smallIcon,
                rank = rank,
                isSilent = isSilent,
                isSystem = isSystemPackage(item.packageName),
            )
        }.sortedWith(compareBy<NotificationIcon> { it.rank }.thenByDescending { icon ->
            notifications.firstOrNull { it.key == icon.key }?.postTime ?: 0L
        })

        listeners.forEach { it.invoke() }
    }

    fun clear() {
        currentIcons = emptyList()
        listeners.forEach { it.invoke() }
    }
}
