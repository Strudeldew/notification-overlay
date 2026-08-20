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

## Sony Xperia Specific color guide

These results were measured on a Sony Xperia XQ-EC54 running Android 16, firmware `69.2.A.4.110` (security patch `2026-08-01`). They describe colors drawn into this app's accessibility-overlay bitmap; other Xperia models or firmware may behave differently.

The Xperia compositor changes some requested RGB values after the app has rendered them:

| Requested color group | Observed Xperia result |
| --- | --- |
| Exact dark grays from `#000000` through `#7F7F7F` | Forced to white. Tested values included `#000000`, `#010101`, `#101010`, `#202020`, `#676767`, and `#7F7F7F`. |
| Exact grays from `#808080` through `#FFFFFF` | Rendered at the requested gray level. `#FFFFFF` naturally remains white. |
| Near-neutral off-grays | Rendered correctly, including `#000001`, `#010102`, `#202021`, `#202124`, `#676768`, `#7F7F80`, `#808081`, and `#A0A0A1`. |
| Dark saturated colors whose largest channel is below `0x80` | Commonly brightened to white or a pale tint instead of staying dark. This was reproduced with single-channel values from `0x02` through `0x7F` and mixed colors such as `#7F0100`, `#7F4000`, `#40607F`, `#127F40`, and `#7F007F`. |
| Chromatic colors with at least one channel at or above `0x80` | Rendered correctly in the tested palette. Examples include `#800100`, `#804000`, `#406080`, `#128040`, `#800080`, and the full-intensity RGB/CMY colors. |

No tested full-alpha color became transparent. Exact black was rendered white; near-black off-grays such as `#000001` and `#020201` remained visibly black. This means a one-channel offset is a practical, visually imperceptible workaround for dark grayscale values:

- use `#000001` instead of `#000000`;
- use `#202124` for a near-black system-icon tone;
- use `#676768` instead of `#676767`.

These are measured examples, not a guaranteed formula for every RGB value. When choosing an untested dark chromatic color, verify it on the target Xperia firmware.

## Screenshot fallback

**Allow screenshot fallback** is off by default. If the user explicitly enables it and Shizuku is unavailable or its Window Manager output is not recognized, Android's accessibility API captures the display.

The app reads only pixels in the status-bar area, immediately releases the in-memory image, does not save it, and has no internet permission. Secure windows may block capture.

Android can suppress accessibility overlays on secure system screens, and OEM power management may require excluding the app from battery optimization if either service is stopped.
