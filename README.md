# Notification Icons Overlay

A small, no-root Android app that mirrors active notification `smallIcon` into a touch-through accessibility overlay at the top-right of the status bar. It is intended for Sony Xperia devices where SystemUI limits the number of visible notification icons.

> [!IMPORTANT]
> Most of this project's code was generated with AI assistance. It has been built and linted, but users and contributors should independently review and test it before relying on it.

## What it does

- Reads active notifications with `NotificationListenerService`.
- Sorts icons using Android's notification ranking.
- Shows at most one icon per app, using its highest-ranked eligible notification.
- Draws 4–15 icons in a `TYPE_ACCESSIBILITY_OVERLAY` above the status bar.
- Lets you adjust icon count, spacing, size, edge inset, and icon tint.
- Automatically matches dark/light status-bar icons through Shizuku.
- Hides SystemUI's original notification icons through Shizuku by default, without hiding the clock or system icons; this can be disabled when another tool already handles it.
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

## Releases and Obtainium

Signed release APKs are published automatically through GitHub Actions. Obtainium can track them directly from this repository:

```text
https://github.com/Strudeldew/notification-overlay
```

Paste that URL into **Add App** in Obtainium and keep **GitHub** as the source. Each stable GitHub release contains one universal APK named `notification-icons-overlay-VERSION.apk` and a matching SHA-256 checksum.

After installing, follow the [phone setup guide](docs/SETUP.md) to grant the required access and configure the overlay.
