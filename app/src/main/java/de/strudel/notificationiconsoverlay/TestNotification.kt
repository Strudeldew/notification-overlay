package de.strudel.notificationiconsoverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Creates the short-lived notification used to verify icon selection, placement, and color. */
object TestNotification {
    const val NOTIFICATION_ID = 9001
    private const val CHANNEL_ID = "overlay_test"

    /**
     * Posts a test notification that expires after 30 seconds and opens [MainActivity] when tapped.
     *
     * The channel is created on every call because Android treats channel creation as idempotent.
     */
    fun show(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.test_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.test_channel_description)
            },
        )

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_test_notification)
            .setContentTitle(context.getString(R.string.test_notification_title))
            .setContentText(context.getString(R.string.test_notification_text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000L)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
