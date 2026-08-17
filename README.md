# Notification Icons Overlay

A small, no-root Android app that mirrors active notification `smallIcon`s into a touch-through accessibility overlay at the top-right of the status bar. It is intended for Sony Xperia devices where SystemUI limits the number of visible notification icons and the stock icons can be hidden separately with Xperia Essentials.

## What it does

- Reads active notifications with `NotificationListenerService`.
- Sorts icons using Android's notification ranking.
- Shows at most one icon per app, using its highest-ranked eligible notification.
- Draws 4–10 icons in a `TYPE_ACCESSIBILITY_OVERLAY` above the status bar.
- Lets you adjust icon count, spacing, size, right inset, and light/dark tint.
- Can align the icon row to either the left or right edge.
- Can include or exclude notifications Android ranks as silent.
- Can include or exclude notifications posted by system apps.
- Automatically hides the overlay while the notification shade or Quick Settings is active.
- Includes a 30-second test notification for checking placement and tint.
- Ignores this app's own notifications except for the explicit test notification, and declares no internet permission.
- Keeps the overlay non-focusable and non-touchable, so status-bar gestures pass through.

## Build

Open the root directory in Android Studio, let Gradle sync, and build the `app` configuration. From a terminal:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Set up on the phone

1. Install the APK and open **Notification Icons Overlay**.
2. Open **Notification access** and enable **Notification icon reader**.
3. Open **Accessibility settings** and enable **Status bar icon overlay**.
4. Hide the unwanted stock status icons with Xperia Essentials, then tune count, spacing, size, inset, and icon color in this app.

The accessibility service requests interactive-window access so it can check whether SystemUI owns the active window and hide the overlay over the notification shade or Quick Settings. It does not traverse view trees, read text, or interact with controls. Android can suppress accessibility overlays on secure system screens, and OEM power management may require excluding the app from battery optimization if either service is stopped.
