package de.strudel.notificationiconsoverlay

import android.graphics.drawable.Icon
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Immutable information required to render and filter one active notification icon.
 *
 * @property key Android's unique notification key.
 * @property packageName package that posted the notification.
 * @property icon notification `smallIcon` supplied by the posting app.
 * @property rank Android's current notification ranking; lower values have higher priority.
 * @property isSilent whether Android ranks the notification below normal alerting importance.
 * @property isSystem whether the posting package is installed as a system application.
 */
data class NotificationIcon(
  val key: String,
  val packageName: String,
  val icon: Icon,
  val rank: Int,
  val isSilent: Boolean,
  val isSystem: Boolean,
)

/**
 * Thread-safe handoff between [NotificationIconListenerService] and [StatusBarOverlayService].
 *
 * Writers replace the complete immutable list, readers take lock-free snapshots, and listeners
 * are stored in a [CopyOnWriteArraySet] because notification and accessibility services can call
 * from different framework-managed threads.
 */
object NotificationIconRepository
{
  private val listeners = CopyOnWriteArraySet<() -> Unit>()

  @Volatile
  private var currentIcons: List<NotificationIcon> = emptyList()

  /** Returns the latest immutable, priority-ordered icon snapshot. */
  fun snapshot(): List<NotificationIcon> = currentIcons

  /** Registers a callback invoked after the snapshot changes. */
  fun addListener(listener: () -> Unit)
  {
    listeners += listener
  }

  /** Removes a callback previously registered with [addListener]. */
  fun removeListener(listener: () -> Unit)
  {
    listeners -= listener
  }

  /**
   * Replaces the repository from the system's active notification and ranking snapshots.
   *
   * Notifications without a small icon are ignored. The app's own background notifications
   * are excluded to avoid recursively showing the overlay itself, while its explicit test
   * notification remains eligible. Results are ordered first by Android rank and then by newest
   * post time when ranks are equal.
   *
   * @param ownPackageName package name used to suppress this app's non-test notifications.
   * @param isSystemPackage callback used to classify packages for the system-app filter.
   */
  fun replace(
    notifications: Array<StatusBarNotification>,
    rankingMap: NotificationListenerService.RankingMap,
    ownPackageName: String,
    isSystemPackage: (String) -> Boolean,
  )
  {
    currentIcons = notifications.mapNotNull { item ->
      val smallIcon = item.notification.smallIcon ?: return@mapNotNull null
      if (item.packageName == ownPackageName && item.id != TestNotification.NOTIFICATION_ID)
      {
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

  /** Clears all icons and notifies observers, usually after notification access is lost. */
  fun clear()
  {
    currentIcons = emptyList()
    listeners.forEach { it.invoke() }
  }
}
