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
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var preferences: SharedPreferences
    private lateinit var notificationStatus: TextView
    private lateinit var accessibilityStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = OverlayConfig.preferences(this)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun buildContent(): View {
        val content = LinearLayout(this).apply {
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
        content.addView((accessibilityStatus.parent as View).withMargins(top = 12, bottom = 24))

        content.addView(sectionTitle("Overlay"))
        val enabled = Switch(this).apply {
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

        content.addView(Switch(this).apply {
            text = getString(R.string.align_overlay_left)
            textSize = 16f
            setTextColor(Color.WHITE)
            isChecked = OverlayConfig.alignLeft(preferences)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                preferences.edit().putBoolean(OverlayConfig.KEY_ALIGN_LEFT, checked).apply()
            }
        })

        content.addView(Switch(this).apply {
            text = getString(R.string.show_silent_notifications)
            textSize = 16f
            setTextColor(Color.WHITE)
            isChecked = OverlayConfig.showSilent(preferences)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                preferences.edit().putBoolean(OverlayConfig.KEY_SHOW_SILENT, checked).apply()
            }
        })

        content.addView(Switch(this).apply {
            text = getString(R.string.show_system_notifications)
            textSize = 16f
            setTextColor(Color.WHITE)
            isChecked = OverlayConfig.showSystem(preferences)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                preferences.edit().putBoolean(OverlayConfig.KEY_SHOW_SYSTEM, checked).apply()
            }
        })

        content.addView(Button(this).apply {
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
            "Choose the tone that matches Sony's current status-bar icons.",
            14f,
            color(R.color.text_secondary),
        ))
        val tone = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(radio("Light", !preferences.getBoolean(OverlayConfig.KEY_DARK_ICONS, false), false))
            addView(radio("Dark", preferences.getBoolean(OverlayConfig.KEY_DARK_ICONS, false), true))
            setOnCheckedChangeListener { _, checkedId ->
                val checked = findViewById<RadioButton>(checkedId)
                preferences.edit().putBoolean(OverlayConfig.KEY_DARK_ICONS, checked.tag == true).apply()
            }
        }
        content.addView(tone)

        content.addView(text(
            "The accessibility service checks which window is active so it can hide over the notification shade and Quick Settings. It does not inspect text or controls. The app has no internet permission and ignores its own notifications except for the test.",
            13f,
            color(R.color.text_secondary),
        ).withMargins(top = 24))

        return ScrollView(this).apply {
            setBackgroundColor(color(R.color.page_background))
            addView(content)
        }
    }

    private fun permissionCard(
        title: String,
        explanation: String,
        buttonText: String,
        action: () -> Unit,
    ): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundResource(R.drawable.card_background)
            addView(text(title, 18f, Color.WHITE, Typeface.BOLD))
            addView(text(explanation, 14f, color(R.color.text_secondary)).withMargins(top = 5))
        }
        val status = text("Checking…", 14f, color(R.color.text_secondary), Typeface.BOLD)
        card.addView(status.withMargins(top = 12))
        card.addView(Button(this).apply {
            text = buttonText
            isAllCaps = false
            setOnClickListener { action() }
        }.withMargins(top = 8))
        return status
    }

    private fun slider(
        label: String,
        min: Int,
        max: Int,
        initial: Int,
        suffix: String,
        onChanged: (Int) -> Unit,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val valueText = text(getString(R.string.slider_value, label, initial, suffix), 15f, Color.WHITE)
        row.addView(valueText.withMargins(top = 12))
        row.addView(SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = initial
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    valueText.text = getString(R.string.slider_value, label, value, suffix)
                    if (fromUser) onChanged(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        return row
    }

    private fun radio(label: String, checked: Boolean, dark: Boolean) = RadioButton(this).apply {
        id = View.generateViewId()
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        isChecked = checked
        tag = dark
    }

    private fun refreshPermissionStatus() {
        val notificationComponent = ComponentName(this, NotificationIconListenerService::class.java)
        val notificationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(notificationComponent)
        } else {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                ?.split(':')
                ?.mapNotNull(ComponentName::unflattenFromString)
                ?.contains(notificationComponent) == true
        }
        showPermissionStatus(notificationStatus, notificationEnabled)
        showPermissionStatus(accessibilityStatus, isAccessibilityServiceEnabled())
    }

    private fun generateTestNotification() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
            return
        }
        TestNotification.show(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == REQUEST_POST_NOTIFICATIONS &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            TestNotification.show(this)
        }
    }

    private fun showPermissionStatus(view: TextView, enabled: Boolean) {
        view.text = if (enabled) "Enabled" else "Not enabled"
        view.setTextColor(if (enabled) Color.rgb(116, 220, 153) else Color.rgb(255, 176, 102))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val expected = ComponentName(this, StatusBarOverlayService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == expected }
    }

    private fun sectionTitle(value: String) = text(value, 20f, color(R.color.accent), Typeface.BOLD)

    private fun text(value: String, size: Float, textColor: Int, style: Int = Typeface.NORMAL) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(textColor)
            typeface = Typeface.create(typeface, style)
        }

    private fun color(resource: Int): Int = getColor(resource)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun <T : View> T.withMargins(
        start: Int = 0,
        top: Int = 0,
        end: Int = 0,
        bottom: Int = 0,
    ): T {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(dp(start), dp(top), dp(end), dp(bottom)) }
        return this
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 100
    }
}
