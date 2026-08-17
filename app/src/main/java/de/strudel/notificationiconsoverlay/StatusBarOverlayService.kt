package de.strudel.notificationiconsoverlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.roundToInt

class StatusBarOverlayService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: SharedPreferences
    private var overlayView: StatusBarIconView? = null
    private var systemUiPanelVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshSystemUiState = Runnable {
        val wasVisible = systemUiPanelVisible
        systemUiPanelVisible = isSystemUiPanelVisible()
        if (wasVisible != systemUiPanelVisible) updateOverlay()
    }

    private val notificationsChanged: () -> Unit = {
        mainHandler.post { updateOverlay() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = OverlayConfig.preferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this)
        NotificationIconRepository.addListener(notificationsChanged)
        updateOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventClass = event?.className?.toString().orEmpty()
        val isShadeEvent = event?.packageName == SYSTEM_UI_PACKAGE &&
            SHADE_WINDOW_NAMES.any { eventClass.contains(it, ignoreCase = true) }
        if (isShadeEvent && !systemUiPanelVisible) {
            systemUiPanelVisible = true
            updateOverlay()
        }

        if (
            event?.packageName == SYSTEM_UI_PACKAGE ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            mainHandler.removeCallbacks(refreshSystemUiState)
            mainHandler.postDelayed(refreshSystemUiState, WINDOW_SETTLE_DELAY_MS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updateOverlay()
    }

    override fun onDestroy() {
        if (::preferences.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(this)
        }
        NotificationIconRepository.removeListener(notificationsChanged)
        mainHandler.removeCallbacks(refreshSystemUiState)
        removeOverlay()
        super.onDestroy()
    }

    private fun updateOverlay() {
        if (!OverlayConfig.isEnabled(preferences) || systemUiPanelVisible) {
            removeOverlay()
            return
        }

        val icons = NotificationIconRepository.snapshot()
            .filter { OverlayConfig.showSilent(preferences) || !it.isSilent }
            .filter { OverlayConfig.showSystem(preferences) || !it.isSystem }
            .distinctBy { it.packageName }
            .take(OverlayConfig.maxIcons(preferences))
        if (icons.isEmpty()) {
            removeOverlay()
            return
        }

        val view = overlayView ?: StatusBarIconView(this).also { newView ->
            overlayView = newView
            try {
                windowManager.addView(newView, createLayoutParams())
            } catch (error: RuntimeException) {
                Log.e(TAG, "Could not attach accessibility overlay", error)
                overlayView = null
                return
            }
        }

        val iconSize = dp(OverlayConfig.iconSizeDp(preferences))
        view.render(
            icons = icons,
            iconSizePx = iconSize,
            spacingPx = dp(OverlayConfig.spacingDp(preferences)),
            iconColor = OverlayConfig.iconColor(preferences),
        )

        val params = view.layoutParams as? WindowManager.LayoutParams ?: createLayoutParams()
        params.height = statusBarHeight()
        params.x = dp(OverlayConfig.edgeInsetDp(preferences))
        params.gravity = Gravity.TOP or alignmentGravity()
        windowManager.updateViewLayout(view, params)
    }

    private fun createLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        statusBarHeight(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or alignmentGravity()
        x = dp(OverlayConfig.edgeInsetDp(preferences))
        title = getString(R.string.accessibility_service_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // The system may already have detached the accessibility window.
            }
        }
        overlayView = null
    }

    @Suppress("DiscouragedApi")
    @SuppressLint("InternalInsetResource")
    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId != 0) resources.getDimensionPixelSize(resourceId) else dp(24)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun alignmentGravity(): Int =
        if (OverlayConfig.alignLeft(preferences)) Gravity.START else Gravity.END

    private fun isSystemUiPanelVisible(): Boolean = windows.any { window ->
        if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
            return@any false
        }

        if (!window.isActive && !window.isFocused) return@any false
        val title = window.title?.toString().orEmpty()
        val looksLikeShade = SHADE_WINDOW_NAMES.any { title.contains(it, ignoreCase = true) }
        looksLikeShade || window.root?.packageName?.toString() == SYSTEM_UI_PACKAGE
    }

    companion object {
        private const val TAG = "NotificationOverlay"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val WINDOW_SETTLE_DELAY_MS = 40L
        private val SHADE_WINDOW_NAMES = listOf(
            "NotificationShade",
            "NotificationPanel",
            "Notification shade",
            "QuickSettings",
            "Quick settings",
            "QSPanel",
            "Schnelleinstellungen",
            "Benachrichtigungen",
        )
    }
}
