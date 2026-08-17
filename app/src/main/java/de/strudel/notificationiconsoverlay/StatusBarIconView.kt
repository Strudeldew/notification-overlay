package de.strudel.notificationiconsoverlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

class StatusBarIconView(context: Context) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }

    fun render(
        icons: List<NotificationIcon>,
        iconSizePx: Int,
        spacingPx: Int,
        iconColor: Int,
    ) {
        removeAllViews()

        icons.forEachIndexed { index, item ->
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                imageTintList = ColorStateList.valueOf(iconColor)
                contentDescription = item.packageName
                setImageDrawable(loadDrawable(item))
            }
            val params = LayoutParams(iconSizePx, iconSizePx).apply {
                if (index > 0) marginStart = spacingPx
            }
            addView(image, params)
        }
    }

    private fun loadDrawable(item: NotificationIcon): Drawable? = try {
        item.icon.loadDrawable(context)?.mutate()
    } catch (error: RuntimeException) {
        Log.w(TAG, "Could not load notification icon from ${item.packageName}", error)
        null
    }

    companion object {
        private const val TAG = "NotificationOverlay"
    }
}
