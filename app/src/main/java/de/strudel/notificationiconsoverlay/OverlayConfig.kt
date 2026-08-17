package de.strudel.notificationiconsoverlay

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object OverlayConfig {
    private const val PREFS = "overlay_config"
    const val KEY_ENABLED = "enabled"
    const val KEY_MAX_ICONS = "max_icons"
    const val KEY_SPACING_DP = "spacing_dp"
    const val KEY_ICON_SIZE_DP = "icon_size_dp"
    const val KEY_EDGE_INSET_DP = "edge_inset_dp"
    const val KEY_DARK_ICONS = "dark_icons"
    const val KEY_ICON_COLOR_MODE = "icon_color_mode"
    const val KEY_SCREENSHOT_FALLBACK_ENABLED = "screenshot_fallback_enabled"
    const val KEY_ALIGN_LEFT = "align_left"
    const val KEY_SHOW_SILENT = "show_silent"
    const val KEY_SHOW_SYSTEM = "show_system"

    const val DEFAULT_MAX_ICONS = 7
    const val DEFAULT_SPACING_DP = 2
    const val DEFAULT_ICON_SIZE_DP = 18
    const val DEFAULT_EDGE_INSET_DP = 4
    const val DEFAULT_SCREENSHOT_FALLBACK_ENABLED = false

    const val COLOR_MODE_AUTO = "auto"
    const val COLOR_MODE_LIGHT = "light"
    const val COLOR_MODE_DARK = "dark"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(prefs: SharedPreferences) = prefs.getBoolean(KEY_ENABLED, true)
    fun maxIcons(prefs: SharedPreferences) = prefs.getInt(KEY_MAX_ICONS, DEFAULT_MAX_ICONS).coerceIn(4, 15)
    fun spacingDp(prefs: SharedPreferences) = prefs.getInt(KEY_SPACING_DP, DEFAULT_SPACING_DP).coerceIn(0, 12)
    fun iconSizeDp(prefs: SharedPreferences) = prefs.getInt(KEY_ICON_SIZE_DP, DEFAULT_ICON_SIZE_DP).coerceIn(10, 24)
    fun edgeInsetDp(prefs: SharedPreferences) = prefs.getInt(KEY_EDGE_INSET_DP, DEFAULT_EDGE_INSET_DP).coerceIn(0, 64)
    fun colorMode(prefs: SharedPreferences): String {
        val stored = prefs.getString(KEY_ICON_COLOR_MODE, null)
        if (stored in setOf(COLOR_MODE_AUTO, COLOR_MODE_LIGHT, COLOR_MODE_DARK)) return stored!!

        // Preserve an explicit choice made by users of versions that only offered Light/Dark.
        return if (prefs.contains(KEY_DARK_ICONS)) {
            if (prefs.getBoolean(KEY_DARK_ICONS, false)) COLOR_MODE_DARK else COLOR_MODE_LIGHT
        } else {
            COLOR_MODE_AUTO
        }
    }

    fun manualIconColor(prefs: SharedPreferences) =
        if (colorMode(prefs) == COLOR_MODE_DARK) Color.BLACK else Color.WHITE

    fun iconColor(prefs: SharedPreferences, automaticColor: Int) = when (colorMode(prefs)) {
        COLOR_MODE_DARK -> Color.BLACK
        COLOR_MODE_LIGHT -> Color.WHITE
        else -> automaticColor
    }
    fun screenshotFallbackEnabled(prefs: SharedPreferences) =
        prefs.getBoolean(KEY_SCREENSHOT_FALLBACK_ENABLED, DEFAULT_SCREENSHOT_FALLBACK_ENABLED)
    fun alignLeft(prefs: SharedPreferences) = prefs.getBoolean(KEY_ALIGN_LEFT, true)
    fun showSilent(prefs: SharedPreferences) = prefs.getBoolean(KEY_SHOW_SILENT, true)
    fun showSystem(prefs: SharedPreferences) = prefs.getBoolean(KEY_SHOW_SYSTEM, true)
}
