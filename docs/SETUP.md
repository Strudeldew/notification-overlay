# Phone setup

Notification Icons Overlay needs notification access and an accessibility service to read notification icons and draw them over the status bar.

## Configure the app

1. Install the APK and open **Notification Icons Overlay**.
2. Open **Notification access** and enable **Notification icon reader**.
3. Open **Accessibility settings** and enable **Status bar icon overlay**.
4. For automatic icon color, install and start [Shizuku](https://shizuku.rikka.app/guide/setup/), then grant this app access from its setup screen. Without Shizuku, select a manual icon color or explicitly opt in to **Allow screenshot fallback**.
5. Hide the unwanted stock status icons with [Essentials](https://github.com/sameerasw/essentials), then tune count, spacing, size, inset, and icon color in this app.

## Accessibility access

The accessibility service requests interactive-window access so it can check whether SystemUI owns the active window and hide the overlay over the notification shade or Quick Settings. It does not traverse view trees, read text, or interact with controls.

In automatic color mode, the app asks Window Manager for the foreground window's system-bar appearance through Shizuku.

## Screenshot fallback

**Allow screenshot fallback** is off by default. If the user explicitly enables it and Shizuku is unavailable or its Window Manager output is not recognized, Android's accessibility API captures the display.

The app reads only pixels in the status-bar area, immediately releases the in-memory image, does not save it, and has no internet permission. Secure windows may block capture.

Android can suppress accessibility overlays on secure system screens, and OEM power management may require excluding the app from battery optimization if either service is stopped.
