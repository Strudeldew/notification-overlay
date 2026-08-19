# Phone setup

Notification Icons Overlay needs notification access and an accessibility service to read notification icons and draw them over the status bar.

## Configure the app

1. Install the APK and open **Notification Icons Overlay**.
2. Open **Notification access** and enable **Notification icon reader**.
3. Open **Accessibility settings** and enable **Status bar icon overlay**.
4. Install and start [Shizuku](https://shizuku.rikka.app/guide/setup/), then grant this app access from its setup screen. Shizuku enables automatic icon color and stock notification-icon hiding. Without it, select a manual icon color or explicitly opt in to **Allow screenshot fallback**.
5. Keep **Hide stock notification icons** enabled unless another tool already hides them, then tune count, spacing, size, inset, and icon color in this app.

## Stock notification icons

**Hide stock notification icons** is on by default because the overlay would otherwise duplicate Android's original notification icons. The app asks Android's status-bar service through Shizuku to hide only those original notification icons. The clock, Wi-Fi, mobile signal, battery, and notification shade remain available.

Turn the option off if the stock notification icons are already hidden by another tool.

Turning the option off restores the original icons. The app also reapplies the enabled setting when Shizuku or the accessibility service reconnects, because Android may clear the status-bar request after a reboot or SystemUI restart.

Disable this option before uninstalling the app. If the app has already been removed while its stock-icon setting was active, reboot the phone or run `adb shell cmd statusbar send-disable-flag none` to restore the icons.

## Accessibility access

The accessibility service requests interactive-window access so it can check whether SystemUI owns the active window and hide the overlay over the notification shade or Quick Settings. It does not traverse view trees, read text, or interact with controls.

In automatic color mode, the app asks Window Manager for the foreground window's system-bar appearance through Shizuku. The same Shizuku grant is used for the optional stock notification-icon control.

## Screenshot fallback

**Allow screenshot fallback** is off by default. If the user explicitly enables it and Shizuku is unavailable or its Window Manager output is not recognized, Android's accessibility API captures the display.

The app reads only pixels in the status-bar area, immediately releases the in-memory image, does not save it, and has no internet permission. Secure windows may block capture.

Android can suppress accessibility overlays on secure system screens, and OEM power management may require excluding the app from battery optimization if either service is stopped.
