package de.strudel.notificationiconsoverlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import rikka.shizuku.Shizuku
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Owns the touch-through accessibility window drawn over the status-bar area.
 *
 * The service observes repository and preference changes, hides itself while SystemUI panels are
 * active, follows immersive status-bar visibility, and keeps automatic icon color synchronized
 * with the foreground window. Window Manager queries run on [appearanceExecutor]; view creation and
 * all [WindowManager] calls stay on the main thread. Screenshot sampling is never attempted unless
 * the user explicitly enables that fallback.
 */
class StatusBarOverlayService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener
{
  private lateinit var windowManager: WindowManager
  private lateinit var preferences: SharedPreferences
  private var overlayView: StatusBarIconView? = null
  private var systemUiPanelVisible = false
  private var statusBarVisible = true
  private var automaticIconColor = Color.WHITE
  private val mainHandler = Handler(Looper.getMainLooper())
  private val mainExecutor = Executor { command -> mainHandler.post(command) }
  // Serialize binder dumps so rapid accessibility events cannot overlap expensive shell reads.
  private val appearanceExecutor: ExecutorService = Executors.newSingleThreadExecutor()
  private val appearanceQueryInFlight = AtomicBoolean(false)
  private var appearanceRefreshPending = false
  private var screenshotInFlight = false
  private val refreshSystemUiState = Runnable()
  {
    val wasVisible = systemUiPanelVisible
    systemUiPanelVisible = isSystemUiPanelVisible()
    setStatusBarVisibility(currentStatusBarVisibility())
    if (wasVisible != systemUiPanelVisible) updateOverlay()
  }
  private val refreshAutomaticColor = Runnable { queryAutomaticColor() }
  private val shizukuBinderListener = Shizuku.OnBinderReceivedListener()
  {
    scheduleAutomaticColorRefresh(0L)
    StockNotificationIconController.reapplyIfEnabled(this)
  }
  private val shizukuDeadListener = Shizuku.OnBinderDeadListener()
  {
    scheduleAutomaticColorRefresh(AUTOMATIC_COLOR_DELAY_MS)
  }
  private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
    scheduleAutomaticColorRefresh(0L)
    StockNotificationIconController.reapplyIfEnabled(this)
  }

  private val notificationsChanged: () -> Unit =
  {
    mainHandler.post { updateOverlay() }
  }

  override fun onServiceConnected()
  {
    super.onServiceConnected()
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    statusBarVisible = currentStatusBarVisibility()
    preferences = OverlayConfig.preferences(this)
    preferences.registerOnSharedPreferenceChangeListener(this)
    NotificationIconRepository.addListener(notificationsChanged)
    automaticIconColor = OverlayConfig.manualIconColor(preferences)
    Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
    Shizuku.addBinderDeadListener(shizukuDeadListener)
    Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    updateOverlay()
    scheduleAutomaticColorRefresh(AUTOMATIC_COLOR_DELAY_MS)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?)
  {
    val eventClass = event?.className?.toString().orEmpty()
    val isShadeEvent = event?.packageName == SYSTEM_UI_PACKAGE &&
      SHADE_WINDOW_NAMES.any { eventClass.contains(it, ignoreCase = true) }
    if (isShadeEvent && !systemUiPanelVisible)
    {
      systemUiPanelVisible = true
      updateOverlay()
    }

    if (
      event?.packageName == SYSTEM_UI_PACKAGE ||
      event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
      event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    )
    {
      mainHandler.removeCallbacks(refreshSystemUiState)
      mainHandler.postDelayed(refreshSystemUiState, WINDOW_SETTLE_DELAY_MS)
    }

    if (
      event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
      event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    )
    {
      scheduleAutomaticColorRefresh(AUTOMATIC_COLOR_DELAY_MS)
    } else if (
      event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
    )
    {
      // Some apps switch status-bar appearance while scrolling without changing windows.
      scheduleAutomaticColorRefresh(AUTOMATIC_COLOR_SCROLL_DELAY_MS)
    }
  }

  override fun onInterrupt() = Unit

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?)
  {
    updateOverlay()
    if (
      key == OverlayConfig.KEY_ICON_COLOR_MODE ||
      key == OverlayConfig.KEY_SCREENSHOT_FALLBACK_ENABLED ||
      key == OverlayConfig.KEY_DARK_ICON_COLOR ||
      key == OverlayConfig.KEY_LIGHT_ICON_COLOR ||
      key == OverlayConfig.KEY_CUSTOM_DARK_ICON_COLOR_ENABLED ||
      key == OverlayConfig.KEY_CUSTOM_LIGHT_ICON_COLOR_ENABLED
    )
    {
      scheduleAutomaticColorRefresh(0L)
    }
    if (key == OverlayConfig.KEY_HIDE_STOCK_NOTIFICATION_ICONS)
    {
      StockNotificationIconController.setHidden(
        this,
        OverlayConfig.hideStockNotificationIcons(preferences),
      ) { result ->
        if (!result.successful)
        {
          Log.w(TAG, result.message ?: "Could not update stock notification icons.")
        }
      }
    }
  }

  override fun onDestroy()
  {
    if (::preferences.isInitialized)
    {
      preferences.unregisterOnSharedPreferenceChangeListener(this)
    }
    NotificationIconRepository.removeListener(notificationsChanged)
    mainHandler.removeCallbacks(refreshSystemUiState)
    mainHandler.removeCallbacks(refreshAutomaticColor)
    Shizuku.removeBinderReceivedListener(shizukuBinderListener)
    Shizuku.removeBinderDeadListener(shizukuDeadListener)
    Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    appearanceExecutor.shutdownNow()
    removeOverlay()
    super.onDestroy()
  }

  /**
   * Reconciles the attached overlay window with current settings, UI state, and notifications.
   *
   * This is the single presentation entry point. It removes the window when disabled, covered by
   * SystemUI, or empty; otherwise it filters and renders a fresh repository snapshot before
   * updating position and fullscreen visibility.
   */
  private fun updateOverlay()
  {
    if (!OverlayConfig.isEnabled(preferences) || systemUiPanelVisible)
    {
      removeOverlay()
      return
    }

    val icons = NotificationIconRepository.snapshot()
      .filter { OverlayConfig.showSilent(preferences) || !it.isSilent }
      .filter { OverlayConfig.showSystem(preferences) || !it.isSystem }
      .distinctBy { it.packageName }
      .take(OverlayConfig.maxIcons(preferences))
    if (icons.isEmpty())
    {
      removeOverlay()
      return
    }

    val view = overlayView ?: StatusBarIconView(this).also { newView ->
      overlayView = newView
      // Standard inset dispatch covers normal status-bar visibility changes.
      newView.setOnApplyWindowInsetsListener { _, insets ->
        setStatusBarVisibility(statusBarVisibility(insets))
        insets
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
      {
        // Follow animated system bars frame by frame on Android builds that dispatch the
        // animation to accessibility overlays.
        newView.setWindowInsetsAnimationCallback(
          object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE)
          {
            override fun onProgress(
              insets: WindowInsets,
              runningAnimations: MutableList<WindowInsetsAnimation>,
            ): WindowInsets
            {
              setStatusBarVisibility(statusBarVisibility(insets))
              return insets
            }

            override fun onEnd(animation: WindowInsetsAnimation)
            {
              setStatusBarVisibility(currentStatusBarVisibility())
            }
          },
        )
      }
      try
      {
        windowManager.addView(newView, createLayoutParams())
      } catch (error: RuntimeException)
      {
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
      iconColor = OverlayConfig.iconColor(preferences, automaticIconColor),
    )

    val params = view.layoutParams as? WindowManager.LayoutParams ?: createLayoutParams()
    params.height = statusBarHeight()
    params.x = dp(OverlayConfig.edgeInsetDp(preferences))
    params.gravity = Gravity.TOP or alignmentGravity()
    windowManager.updateViewLayout(view, params)
    view.alpha = if (statusBarVisible) 1f else 0f
  }

  /**
   * Creates non-focusable and non-touchable window parameters spanning the status-bar height.
   *
   * `TYPE_ACCESSIBILITY_OVERLAY` is supplied by this enabled accessibility service and does not
   * require the broader draw-over-other-apps permission.
   */
  private fun createLayoutParams() = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    statusBarHeight(),
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT,
  ).apply()
  {
    gravity = Gravity.TOP or alignmentGravity()
    x = dp(OverlayConfig.edgeInsetDp(preferences))
    title = getString(R.string.accessibility_service_label)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    {
      layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
    {
      layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
  }

  /** Safely detaches the current overlay, tolerating a window already removed by Android. */
  private fun removeOverlay()
  {
    overlayView?.let { view ->
      try
      {
        windowManager.removeView(view)
      } catch (_: IllegalArgumentException)
      {
        // The system may already have detached the accessibility window.
      }
    }
    overlayView = null
  }

  /** Returns the platform status-bar height, with a conservative 24 dp OEM fallback. */
  @Suppress("DiscouragedApi")
  @SuppressLint("InternalInsetResource")
  private fun statusBarHeight(): Int
  {
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId != 0) resources.getDimensionPixelSize(resourceId) else dp(24)
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

  private fun alignmentGravity(): Int =
    if (OverlayConfig.alignLeft(preferences)) Gravity.START else Gravity.END

  /**
   * Returns the best currently available status-bar visibility signal.
   *
   * A full-width SystemUI accessibility window is checked first because Sony exposes transient
   * swipe-to-reveal bars there while leaving this overlay's own [WindowInsets] state hidden.
   * Regular visibility transitions use the current Window Manager insets on Android 11+.
   */
  private fun currentStatusBarVisibility(): Boolean =
    if (isStatusBarAccessibilityWindowVisible())
    {
      true
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    {
      statusBarVisibility(windowManager.currentWindowMetrics.windowInsets)
    } else
    {
      // The attached view's first insets dispatch will provide the authoritative value.
      true
    }

  /**
   * Detects a visible status bar represented as a thin, full-width system accessibility window.
   *
   * The width and height checks avoid mistaking dialogs, privacy chips, or navigation overlays
   * for the status bar. This signal is especially important for transient immersive bars on Sony.
   */
  private fun isStatusBarAccessibilityWindowVisible(): Boolean
  {
    val displayWidth = resources.displayMetrics.widthPixels
    val maximumStatusBarHeight = statusBarHeight() * 2
    val bounds = Rect()
    return windows.any { window ->
      if (window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM)
      {
        return@any false
      }
      window.getBoundsInScreen(bounds)
      bounds.top == 0 &&
        bounds.height() in 1..maximumStatusBarHeight &&
        bounds.width() >= displayWidth * 9 / 10
    }
  }

  /** Reads status-bar visibility from insets, with a pre-Android 11 compatibility fallback. */
  @Suppress("DEPRECATION")
  private fun statusBarVisibility(insets: WindowInsets): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    {
      insets.isVisible(WindowInsets.Type.statusBars())
    } else
    {
      insets.systemWindowInsetTop > 0
    }

  /**
   * Updates whether icon pixels are drawn while leaving the accessibility window attached.
   *
   * Keeping an alpha-zero window attached lets it continue receiving inset and accessibility
   * transitions, so icons can return without recreating the window when immersive mode ends.
   */
  private fun setStatusBarVisibility(visible: Boolean)
  {
    if (statusBarVisible == visible) return
    statusBarVisible = visible

    // Keep the window attached so it continues receiving insets when immersive mode ends.
    overlayView?.alpha = if (visible) 1f else 0f
  }

  /**
   * Debounces an automatic-color query on the main thread.
   *
   * If a binder query is already running, [appearanceRefreshPending] records that one additional
   * pass is needed after it completes instead of starting concurrent Window Manager dumps.
   */
  private fun scheduleAutomaticColorRefresh(delayMillis: Long)
  {
    if (!::preferences.isInitialized || OverlayConfig.colorMode(preferences) != OverlayConfig.COLOR_MODE_AUTO)
    {
      return
    }
    mainHandler.removeCallbacks(refreshAutomaticColor)
    if (appearanceQueryInFlight.get()) appearanceRefreshPending = true
    mainHandler.postDelayed(refreshAutomaticColor, delayMillis)
  }

  /**
   * Reads status-bar appearance off the main thread and applies the result on the main thread.
   *
   * Shizuku is always tried first. An unknown result triggers screenshot sampling only when the
   * opt-in preference is enabled; otherwise the last known automatic color is retained.
   */
  private fun queryAutomaticColor()
  {
    if (
      OverlayConfig.colorMode(preferences) != OverlayConfig.COLOR_MODE_AUTO ||
      !appearanceQueryInFlight.compareAndSet(false, true)
    )
    {
      if (appearanceQueryInFlight.get()) appearanceRefreshPending = true
      return
    }

    appearanceExecutor.execute()
    {
      val darkIcons = ShizukuStatusBarReader.readDarkIcons(this)
      mainHandler.post()
      {
        appearanceQueryInFlight.set(false)
        if (OverlayConfig.colorMode(preferences) != OverlayConfig.COLOR_MODE_AUTO) return@post
        if (darkIcons != null)
        {
          applyAutomaticColor(
            if (darkIcons) OverlayConfig.darkIconColor(preferences)
            else OverlayConfig.lightIconColor(preferences),
          )
        } else if (OverlayConfig.screenshotFallbackEnabled(preferences))
        {
          sampleStatusBarBackground()
        }
        if (appearanceRefreshPending)
        {
          appearanceRefreshPending = false
          scheduleAutomaticColorRefresh(AUTOMATIC_COLOR_DELAY_MS)
        }
      }
    }
  }

  /** Stores and renders a changed automatic color without rebuilding for identical results. */
  private fun applyAutomaticColor(color: Int)
  {
    if (automaticIconColor == color) return
    automaticIconColor = color
    updateOverlay()
  }

  /**
   * Captures one accessibility screenshot and derives a contrasting status-bar icon color.
   *
   * This privacy-sensitive fallback is gated immediately before capture and again before applying
   * its result. Android supplies a display-sized hardware buffer; the app copies it only in memory,
   * samples status-bar pixels, and closes/recycles every buffer without saving the image.
   */
  private fun sampleStatusBarBackground()
  {
    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
      screenshotInFlight ||
      !OverlayConfig.screenshotFallbackEnabled(preferences)
    ) return
    screenshotInFlight = true
    try
    {
      takeScreenshot(
        Display.DEFAULT_DISPLAY,
        mainExecutor,
        object : AccessibilityService.TakeScreenshotCallback
        {
          override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult)
          {
            screenshotInFlight = false
            val hardwareBuffer = screenshot.hardwareBuffer
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
            val bitmap = try
            {
              hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
            } finally
            {
              hardwareBitmap?.recycle()
              hardwareBuffer.close()
            }
            if (bitmap != null && OverlayConfig.screenshotFallbackEnabled(preferences))
            {
              val color = sampledContrastingColor(bitmap)
              bitmap.recycle()
              applyAutomaticColor(color)
            } else
            {
              bitmap?.recycle()
            }
          }

          override fun onFailure(errorCode: Int)
          {
            screenshotInFlight = false
            Log.d(TAG, "Status-bar screenshot unavailable: $errorCode")
          }
        },
      )
    } catch (error: RuntimeException)
    {
      screenshotInFlight = false
      Log.w(TAG, "Could not sample the status-bar background", error)
    }
  }

  /**
   * Returns black for a light sampled status bar and white for a dark sampled status bar.
   *
   * A median across a small grid is used instead of a single pixel so clocks, cutouts, notification
   * icons, and small gradients are unlikely to flip the result.
   */
  private fun sampledContrastingColor(bitmap: Bitmap): Int
  {
    val sampleHeight = minOf(statusBarHeight(), bitmap.height).coerceAtLeast(1)
    val luminances = ArrayList<Double>(SCREENSHOT_SAMPLE_COLUMNS * SCREENSHOT_SAMPLE_ROWS)
    for (column in 1..SCREENSHOT_SAMPLE_COLUMNS)
    {
      val x = (bitmap.width * column / (SCREENSHOT_SAMPLE_COLUMNS + 1)).coerceIn(0, bitmap.width - 1)
      for (row in 1..SCREENSHOT_SAMPLE_ROWS)
      {
        val y = (sampleHeight * row / (SCREENSHOT_SAMPLE_ROWS + 1)).coerceIn(0, sampleHeight - 1)
        val pixel = bitmap.getPixel(x, y)
        luminances += 0.2126 * Color.red(pixel) +
          0.7152 * Color.green(pixel) +
          0.0722 * Color.blue(pixel)
      }
    }
    luminances.sort()
    val median = luminances[luminances.size / 2]
    return if (median >= LIGHT_BACKGROUND_LUMINANCE) {
      OverlayConfig.darkIconColor(preferences)
    } else {
      OverlayConfig.lightIconColor(preferences)
    }
  }

  /**
   * Returns whether the active/focused accessibility window appears to be a SystemUI panel.
   *
   * This service's own accessibility window is excluded to prevent it from hiding itself.
   */
  private fun isSystemUiPanelVisible(): Boolean = windows.any { window ->
    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)
    {
      return@any false
    }

    if (!window.isActive && !window.isFocused) return@any false
    val title = window.title?.toString().orEmpty()
    val looksLikeShade = SHADE_WINDOW_NAMES.any { title.contains(it, ignoreCase = true) }
    looksLikeShade || window.root?.packageName?.toString() == SYSTEM_UI_PACKAGE
  }

  companion object
  {
    private const val TAG = "NotificationOverlay"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val WINDOW_SETTLE_DELAY_MS = 40L
    private const val AUTOMATIC_COLOR_DELAY_MS = 100L
    private const val AUTOMATIC_COLOR_SCROLL_DELAY_MS = 300L
    private const val SCREENSHOT_SAMPLE_COLUMNS = 11
    private const val SCREENSHOT_SAMPLE_ROWS = 5
    private const val LIGHT_BACKGROUND_LUMINANCE = 165.0
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
