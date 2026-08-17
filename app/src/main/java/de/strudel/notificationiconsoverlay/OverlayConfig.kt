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
    const val KEY_ALIGN_LEFT = "align_left"
    const val KEY_SHOW_SILENT = "show_silent"
    const val KEY_SHOW_SYSTEM = "show_system"

    const val DEFAULT_MAX_ICONS = 7
    const val DEFAULT_SPACING_DP = 2
    const val DEFAULT_ICON_SIZE_DP = 18
    const val DEFAULT_EDGE_INSET_DP = 4

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(prefs: SharedPreferences) = prefs.getBoolean(KEY_ENABLED, true)
    fun maxIcons(prefs: SharedPreferences) = prefs.getInt(KEY_MAX_ICONS, DEFAULT_MAX_ICONS).coerceIn(4, 15)
    fun spacingDp(prefs: SharedPreferences) = prefs.getInt(KEY_SPACING_DP, DEFAULT_SPACING_DP).coerceIn(0, 12)
    fun iconSizeDp(prefs: SharedPreferences) = prefs.getInt(KEY_ICON_SIZE_DP, DEFAULT_ICON_SIZE_DP).coerceIn(10, 24)
    fun edgeInsetDp(prefs: SharedPreferences) = prefs.getInt(KEY_EDGE_INSET_DP, DEFAULT_EDGE_INSET_DP).coerceIn(0, 64)
    fun iconColor(prefs: SharedPreferences) = if (prefs.getBoolean(KEY_DARK_ICONS, false)) Color.BLACK else Color.WHITE
    fun alignLeft(prefs: SharedPreferences) = prefs.getBoolean(KEY_ALIGN_LEFT, false)
    fun showSilent(prefs: SharedPreferences) = prefs.getBoolean(KEY_SHOW_SILENT, true)
    fun showSystem(prefs: SharedPreferences) = prefs.getBoolean(KEY_SHOW_SYSTEM, true)
}
