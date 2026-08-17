# Notification Icons Overlay

A small, no-root Android app that mirrors active notification `smallIcon` into a touch-through accessibility overlay at the top-right of the status bar. It is intended for Sony Xperia devices where SystemUI limits the number of visible notification icons and the stock icons can be hidden separately with [Essentials](https://github.com/sameerasw/essentials).

> [!IMPORTANT]
> Most of this project's code was generated with AI assistance. It has been built and linted, but users and contributors should independently review and test it before relying on it.

## What it does

- Reads active notifications with `NotificationListenerService`.
- Sorts icons using Android's notification ranking.
- Shows at most one icon per app, using its highest-ranked eligible notification.
- Draws 4–15 icons in a `TYPE_ACCESSIBILITY_OVERLAY` above the status bar.
- Lets you adjust icon count, spacing, size, edge inset, and icon tint.
- Automatically matches dark/light status-bar icons through Shizuku.
- Offers an optional screenshot fallback that is disabled by default and requires explicit opt-in.
- Can align the icon row to either the left or right edge.
- Can include or exclude notifications Android ranks as silent.
- Can include or exclude notifications posted by system apps.
- Automatically hides the overlay while the notification shade or Quick Settings is active.
- Follows status-bar visibility during immersive video playback, including transient swipe-to-reveal bars.
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
4. For automatic icon color, install and start [Shizuku](https://shizuku.rikka.app/guide/setup/), then grant this app access from its setup screen. Without Shizuku, select a manual icon color or explicitly opt in to **Allow screenshot fallback**.
5. Hide the unwanted stock status icons with [Essentials](https://github.com/sameerasw/essentials), then tune count, spacing, size, inset, and icon color in this app.

The accessibility service requests interactive-window access so it can check whether SystemUI owns the active window and hide the overlay over the notification shade or Quick Settings. It does not traverse view trees, read text, or interact with controls. In automatic color mode, the app asks Window Manager for the foreground window's system-bar appearance through Shizuku.

**Allow screenshot fallback** is off by default. If the user explicitly enables it and Shizuku is unavailable or its Window Manager output is not recognized, Android's accessibility API captures the display. The app reads only pixels in the status-bar area, immediately releases the in-memory image, does not save it, and has no internet permission. Secure windows may block capture. Android can suppress accessibility overlays on secure system screens, and OEM power management may require excluding the app from battery optimization if either service is stopped.
