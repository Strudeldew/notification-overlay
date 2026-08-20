package de.strudel.notificationiconsoverlay

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.NotificationManager
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

/**
 * Displays the app's setup checklist and all user-configurable overlay options.
 *
 * The screen is built programmatically to keep this small utility independent of a view-binding
 * framework. Preference changes are observed by [StatusBarOverlayService], so controls can write
 * directly to [OverlayConfig] without explicitly restarting the service.
 */
class MainActivity : Activity()
{
  private lateinit var preferences: SharedPreferences
  private lateinit var notificationStatus: TextView
  private lateinit var accessibilityStatus: TextView
  private lateinit var shizukuStatus: TextView

  private val shizukuBinderListener = Shizuku.OnBinderReceivedListener()
  {
    if (::shizukuStatus.isInitialized) refreshShizukuStatus()
    StockNotificationIconController.reapplyIfEnabled(this)
  }
  private val shizukuDeadListener = Shizuku.OnBinderDeadListener()
  {
    if (::shizukuStatus.isInitialized) refreshShizukuStatus()
  }
  private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
    if (requestCode == REQUEST_SHIZUKU)
    {
      refreshShizukuStatus()
      StockNotificationIconController.reapplyIfEnabled(this)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?)
  {
    super.onCreate(savedInstanceState)
    preferences = OverlayConfig.preferences(this)
    Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
    Shizuku.addBinderDeadListener(shizukuDeadListener)
    Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    setContentView(buildContent())
  }

  override fun onDestroy()
  {
    Shizuku.removeBinderReceivedListener(shizukuBinderListener)
    Shizuku.removeBinderDeadListener(shizukuDeadListener)
    Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    super.onDestroy()
  }

  override fun onResume()
  {
    super.onResume()
    refreshPermissionStatus()
  }

  /** Builds the complete scrollable settings screen for the current preference values. */
  private fun buildContent(): View
  {
    val content = LinearLayout(this).apply()
    {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(20), dp(28), dp(20), dp(36))
    }

    content.addView(text("Notification icons", 30f, Color.WHITE, Typeface.BOLD))
    content.addView(text(
      "Put more active notification icons in the top-right of the status bar.",
      16f,
      color(R.color.text_secondary),
    ).withMargins(top = 8, bottom = 24))

    notificationStatus = permissionCard(
      title = "1. Notification access",
      explanation = "Needed to read each notification's small status icon.",
      buttonText = "Open notification access",
    ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    content.addView(notificationStatus.parent as View)

    accessibilityStatus = permissionCard(
      title = "2. Accessibility overlay",
      explanation = "Needed only to place a touch-through icon row above the status bar.",
      buttonText = "Open accessibility settings",
    ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    content.addView((accessibilityStatus.parent as View).withMargins(top = 12))

    shizukuStatus = permissionCard(
      title = "3. Shizuku integration",
      explanation = "Optional. Enables automatic color and hiding Android's original notification icons.",
      buttonText = "Grant Shizuku access",
    ) { requestShizukuPermission() }
    content.addView((shizukuStatus.parent as View).withMargins(top = 12, bottom = 24))

    content.addView(sectionTitle("Overlay"))
    val enabled = Switch(this).apply()
    {
      text = getString(R.string.show_overlay)
      textSize = 16f
      setTextColor(Color.WHITE)
      isChecked = OverlayConfig.isEnabled(preferences)
      setPadding(0, dp(8), 0, dp(8))
      setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
        preferences.edit().putBoolean(OverlayConfig.KEY_ENABLED, checked).apply()
      }
    }
    content.addView(enabled)

    var changingStockIconToggleProgrammatically = false
    val hideStockNotificationIcons = Switch(this).apply()
    {
      text = getString(R.string.hide_stock_notification_icons)
      textSize = 16f
      setTextColor(Color.WHITE)
      isChecked = OverlayConfig.hideStockNotificationIcons(preferences)
      setPadding(0, dp(8), 0, dp(8))
      setOnCheckedChangeListener { button: CompoundButton, checked: Boolean ->
        if (changingStockIconToggleProgrammatically) return@setOnCheckedChangeListener
        button.isEnabled = false
        StockNotificationIconController.setHidden(this@MainActivity, checked) { result ->
          button.isEnabled = true
          if (result.successful)
          {
            preferences.edit()
              .putBoolean(OverlayConfig.KEY_HIDE_STOCK_NOTIFICATION_ICONS, checked)
              .apply()
          } else
          {
            changingStockIconToggleProgrammatically = true
            button.isChecked = !checked
            changingStockIconToggleProgrammatically = false
            Toast.makeText(
              this@MainActivity,
              result.message ?: "Could not change the stock notification icons.",
              Toast.LENGTH_LONG,
            ).show()
          }
        }
      }
    }
    content.addView(hideStockNotificationIcons)
    content.addView(text(
      "Uses Shizuku to hide Android's original notification icons while keeping Wi-Fi, " +
        "battery, clock, and the notification shade available. Turn this off if another " +
        "tool already hides them.",
      13f,
      color(R.color.text_secondary),
    ).withMargins(bottom = 8))

    content.addView(Switch(this).apply()
    {
      text = getString(R.string.align_overlay_left)
      textSize = 16f
      setTextColor(Color.WHITE)
      isChecked = OverlayConfig.alignLeft(preferences)
      setPadding(0, dp(8), 0, dp(8))
      setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
        preferences.edit().putBoolean(OverlayConfig.KEY_ALIGN_LEFT, checked).apply()
      }
    })

    content.addView(Switch(this).apply()
    {
      text = getString(R.string.show_silent_notifications)
      textSize = 16f
      setTextColor(Color.WHITE)
      isChecked = OverlayConfig.showSilent(preferences)
      setPadding(0, dp(8), 0, dp(8))
      setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
        preferences.edit().putBoolean(OverlayConfig.KEY_SHOW_SILENT, checked).apply()
      }
    })

    content.addView(Switch(this).apply()
    {
      text = getString(R.string.show_system_notifications)
      textSize = 16f
      setTextColor(Color.WHITE)
      isChecked = OverlayConfig.showSystem(preferences)
      setPadding(0, dp(8), 0, dp(8))
      setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
        preferences.edit().putBoolean(OverlayConfig.KEY_SHOW_SYSTEM, checked).apply()
      }
    })

    content.addView(Button(this).apply()
    {
      text = getString(R.string.generate_test_notification)
      isAllCaps = false
      setOnClickListener { generateTestNotification() }
    }.withMargins(top = 8, bottom = 8))

    content.addView(slider(
      label = "Maximum icons",
      min = 4,
      max = 15,
      initial = OverlayConfig.maxIcons(preferences),
      suffix = "",
    ) { preferences.edit().putInt(OverlayConfig.KEY_MAX_ICONS, it).apply() })

    content.addView(slider(
      label = "Icon spacing",
      min = 0,
      max = 12,
      initial = OverlayConfig.spacingDp(preferences),
      suffix = " dp",
    ) { preferences.edit().putInt(OverlayConfig.KEY_SPACING_DP, it).apply() })

    content.addView(slider(
      label = "Icon size",
      min = 10,
      max = 24,
      initial = OverlayConfig.iconSizeDp(preferences),
      suffix = " dp",
    ) { preferences.edit().putInt(OverlayConfig.KEY_ICON_SIZE_DP, it).apply() })

    content.addView(slider(
      label = (if (OverlayConfig.alignLeft(preferences)) "Left" else "Right") + " edge inset",
      min = 0,
      max = 64,
      initial = OverlayConfig.edgeInsetDp(preferences),
      suffix = " dp",
    ) { preferences.edit().putInt(OverlayConfig.KEY_EDGE_INSET_DP, it).apply() })

    content.addView(sectionTitle("Icon color").withMargins(top = 20))
    content.addView(text(
      "Automatic uses Shizuku. Screenshot fallback is optional and off by default.",
      14f,
      color(R.color.text_secondary),
    ))
    val selectedColorMode = OverlayConfig.colorMode(preferences)
    val tone = RadioGroup(this).apply()
    {
      orientation = RadioGroup.VERTICAL
      addView(radio("Automatic", selectedColorMode == OverlayConfig.COLOR_MODE_AUTO, OverlayConfig.COLOR_MODE_AUTO))
      addView(radio("Light icons", selectedColorMode == OverlayConfig.COLOR_MODE_LIGHT, OverlayConfig.COLOR_MODE_LIGHT))
      addView(radio("Dark icons", selectedColorMode == OverlayConfig.COLOR_MODE_DARK, OverlayConfig.COLOR_MODE_DARK))
      setOnCheckedChangeListener { _, checkedId ->
        val checked = findViewById<RadioButton>(checkedId)
        preferences.edit().putString(OverlayConfig.KEY_ICON_COLOR_MODE, checked.tag as String).apply()
      }
    }
    content.addView(tone)

    content.addView(Switch(this).apply()
    {
      text = getString(R.string.allow_screenshot_fallback)
      textSize = 16f
      setTextColor(Color.WHITE)
      isChecked = OverlayConfig.screenshotFallbackEnabled(preferences)
      setPadding(0, dp(8), 0, dp(8))
      setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
        preferences.edit()
          .putBoolean(OverlayConfig.KEY_SCREENSHOT_FALLBACK_ENABLED, checked)
          .apply()
      }
    })
    content.addView(text(
      "Opt in to an accessibility screenshot when Shizuku cannot determine the color. " +
        "Android captures the display; the app reads only status-bar pixels, keeps no image, and sends nothing.",
      13f,
      color(R.color.text_secondary),
    ))

    content.addView(sectionTitle("Dark icon color").withMargins(top = 20))
    val darkColorEdit = hexColorEditText(
      hint = "#000000",
      initial = OverlayConfig.darkIconColor(preferences),
      visible = OverlayConfig.customDarkIconColorEnabled(preferences),
      key = OverlayConfig.KEY_DARK_ICON_COLOR,
    )
    content.addView(Switch(this).apply()
    {
      text = "Custom dark icon color"
      textSize = 16f
      setTextColor(Color.WHITE)
      isChecked = OverlayConfig.customDarkIconColorEnabled(preferences)
      setPadding(0, dp(8), 0, dp(8))
      setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
        preferences.edit().putBoolean(OverlayConfig.KEY_CUSTOM_DARK_ICON_COLOR_ENABLED, checked).apply()
        if (checked)
        {
          darkColorEdit.visibility = View.VISIBLE
        } else
        {
          preferences.edit().putInt(OverlayConfig.KEY_DARK_ICON_COLOR, Color.BLACK).apply()
          darkColorEdit.visibility = View.GONE
        }
      }
    })
    content.addView(text(
      "Used for status bar icons when the background is light or dark icon mode is selected.",
      13f,
      color(R.color.text_secondary),
    ))
    content.addView(darkColorEdit.withMargins(top = 8))

    content.addView(sectionTitle("Light icon color").withMargins(top = 20))
    val lightColorEdit = hexColorEditText(
      hint = "#FFFFFF",
      initial = OverlayConfig.lightIconColor(preferences),
      visible = OverlayConfig.customLightIconColorEnabled(preferences),
      key = OverlayConfig.KEY_LIGHT_ICON_COLOR,
    )
    content.addView(Switch(this).apply()
    {
      text = "Custom light icon color"
      textSize = 16f
      setTextColor(Color.WHITE)
      isChecked = OverlayConfig.customLightIconColorEnabled(preferences)
      setPadding(0, dp(8), 0, dp(8))
      setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
        preferences.edit().putBoolean(OverlayConfig.KEY_CUSTOM_LIGHT_ICON_COLOR_ENABLED, checked).apply()
        if (checked)
        {
          lightColorEdit.visibility = View.VISIBLE
        } else
        {
          preferences.edit().putInt(OverlayConfig.KEY_LIGHT_ICON_COLOR, Color.WHITE).apply()
          lightColorEdit.visibility = View.GONE
        }
      }
    })
    content.addView(text(
      "Used for status bar icons when the background is dark or light icon mode is selected.",
      13f,
      color(R.color.text_secondary),
    ))
    content.addView(lightColorEdit.withMargins(top = 8))

    content.addView(text(
      "The accessibility service checks which window is active so it can hide over the notification shade and Quick Settings. Shizuku provides automatic color and optional stock notification-icon control. Without Shizuku, choose a manual color or explicitly enable screenshot fallback. The app has no internet permission and ignores its own notifications except for the test.",
      13f,
      color(R.color.text_secondary),
    ).withMargins(top = 24))

    return ScrollView(this).apply()
    {
      setBackgroundColor(color(R.color.page_background))
      addView(content)
    }
  }

  /** Builds a hex-color input that persists valid `#RRGGBB` values to [key]. */
  private fun hexColorEditText(hint: String, initial: Int, visible: Boolean, key: String) =
    EditText(this).apply()
    {
      this.hint = hint
      setTextColor(Color.WHITE)
      setHintTextColor(color(R.color.text_secondary))
      setBackgroundResource(R.drawable.edit_text_background)
      setPadding(dp(8), dp(8), dp(8), dp(8))
      setText(String.format("#%06X", 0xFFFFFF and initial))
      visibility = if (visible) View.VISIBLE else View.GONE
      addTextChangedListener(object : TextWatcher
      {
        override fun afterTextChanged(s: Editable?)
        {
          val colorString = s.toString()
          if (colorString.matches(Regex("^#[A-Fa-f0-9]{6}$")))
          {
            preferences.edit().putInt(key, Color.parseColor(colorString)).apply()
          }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
      })
    }

  /**
   * Builds one setup card and returns its status label.
   *
   * The caller adds `status.parent` to the page and retains the returned label so permission
   * state can be refreshed from [onResume].
   *
   * @param title heading shown at the top of the card.
   * @param explanation short reason why the permission or integration is useful.
   * @param buttonText label for the action button.
   * @param action invoked when the action button is pressed.
   * @return the mutable status label contained by the newly created card.
   */
  private fun permissionCard(
    title: String,
    explanation: String,
    buttonText: String,
    action: () -> Unit,
  ): TextView
  {
    val card = LinearLayout(this).apply()
    {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(16), dp(16), dp(16), dp(16))
      setBackgroundResource(R.drawable.card_background)
      addView(text(title, 18f, Color.WHITE, Typeface.BOLD))
      addView(text(explanation, 14f, color(R.color.text_secondary)).withMargins(top = 5))
    }
    val status = text("Checking…", 14f, color(R.color.text_secondary), Typeface.BOLD)
    card.addView(status.withMargins(top = 12))
    card.addView(Button(this).apply()
    {
      text = buttonText
      isAllCaps = false
      setOnClickListener { action() }
    }.withMargins(top = 8))
    return status
  }

  /**
   * Creates a labelled integer slider whose callback runs only for user-originated changes.
   *
   * @param suffix unit appended to the displayed numeric value, such as `" dp"`.
   * @param onChanged receives each value selected by the user.
   */
  private fun slider(
    label: String,
    min: Int,
    max: Int,
    initial: Int,
    suffix: String,
    onChanged: (Int) -> Unit,
  ): View
  {
    val row = LinearLayout(this).apply()
    {
      orientation = LinearLayout.VERTICAL
    }
    val valueText = text(getString(R.string.slider_value, label, initial, suffix), 15f, Color.WHITE)
    row.addView(valueText.withMargins(top = 12))
    row.addView(SeekBar(this).apply()
    {
      this.min = min
      this.max = max
      progress = initial
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener
      {
        override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean)
        {
          valueText.text = getString(R.string.slider_value, label, value, suffix)
          if (fromUser) onChanged(value)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
      })
    })
    return row
  }

  private fun radio(label: String, checked: Boolean, mode: String) = RadioButton(this).apply()
  {
    id = View.generateViewId()
    text = label
    textSize = 15f
    setTextColor(Color.WHITE)
    isChecked = checked
    tag = mode
  }

  /** Refreshes notification, accessibility, and Shizuku state after returning from system UI. */
  private fun refreshPermissionStatus()
  {
    val notificationComponent = ComponentName(this, NotificationIconListenerService::class.java)
    val notificationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
    {
      getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(notificationComponent)
    } else
    {
      Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        ?.split(':')
        ?.mapNotNull(ComponentName::unflattenFromString)
        ?.contains(notificationComponent) == true
    }
    showPermissionStatus(notificationStatus, notificationEnabled)
    showPermissionStatus(accessibilityStatus, isAccessibilityServiceEnabled())
    refreshShizukuStatus()
  }

  /**
   * Requests Shizuku access when possible and otherwise explains the required user action.
   *
   * Shizuku calls can throw while its binder is starting or dying, so every availability check
   * is intentionally contained in this guarded block.
   */
  private fun requestShizukuPermission()
  {
    try
    {
      when
      {
        !Shizuku.pingBinder() -> Toast.makeText(
          this,
          "Start Shizuku first, then return to this app.",
          Toast.LENGTH_LONG,
        ).show()
        Shizuku.isPreV11() -> Toast.makeText(this, "This Shizuku version is too old.", Toast.LENGTH_LONG).show()
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> refreshShizukuStatus()
        Shizuku.shouldShowRequestPermissionRationale() -> Toast.makeText(
          this,
          "Permission was denied. Allow this app from Shizuku's app list.",
          Toast.LENGTH_LONG,
        ).show()
        else -> Shizuku.requestPermission(REQUEST_SHIZUKU)
      }
    } catch (_: RuntimeException)
    {
      Toast.makeText(this, "Shizuku is not available.", Toast.LENGTH_LONG).show()
    }
  }

  /** Reads the current Shizuku binder/permission state and updates the setup card. */
  private fun refreshShizukuStatus()
  {
    val status = try
    {
      when
      {
        !Shizuku.pingBinder() -> "Not running"
        Shizuku.isPreV11() -> "Unsupported Shizuku version"
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "Enabled"
        else -> "Permission not granted"
      }
    } catch (_: RuntimeException)
    {
      "Not available"
    }
    val enabled = status == "Enabled"
    shizukuStatus.text = status
    shizukuStatus.setTextColor(if (enabled) Color.rgb(116, 220, 153) else Color.rgb(255, 176, 102))
  }

  /** Requests Android 13+ notification permission when needed, then posts the test icon. */
  private fun generateTestNotification()
  {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    )
    {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
      return
    }
    TestNotification.show(this)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
  )
  {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (
      requestCode == REQUEST_POST_NOTIFICATIONS &&
      grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
    )
    {
      TestNotification.show(this)
    }
  }

  private fun showPermissionStatus(view: TextView, enabled: Boolean)
  {
    view.text = if (enabled) "Enabled" else "Not enabled"
    view.setTextColor(if (enabled) Color.rgb(116, 220, 153) else Color.rgb(255, 176, 102))
  }

  /** Returns whether Android currently reports [StatusBarOverlayService] as enabled. */
  private fun isAccessibilityServiceEnabled(): Boolean
  {
    val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val expected = ComponentName(this, StatusBarOverlayService::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
      .any { ComponentName.unflattenFromString(it.id) == expected }
  }

  private fun sectionTitle(value: String) = text(value, 20f, color(R.color.accent), Typeface.BOLD)

  private fun text(value: String, size: Float, textColor: Int, style: Int = Typeface.NORMAL) =
    TextView(this).apply()
    {
      text = value
      textSize = size
      setTextColor(textColor)
      typeface = Typeface.create(typeface, style)
    }

  private fun color(resource: Int): Int = getColor(resource)
  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

  /** Applies density-independent margins and returns the same view for fluent construction. */
  private fun <T : View> T.withMargins(
    start: Int = 0,
    top: Int = 0,
    end: Int = 0,
    bottom: Int = 0,
  ): T
  {
    layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { setMargins(dp(start), dp(top), dp(end), dp(bottom)) }
    return this
  }

  companion object
  {
    private const val REQUEST_POST_NOTIFICATIONS = 100
    private const val REQUEST_SHIZUKU = 101
  }
}
