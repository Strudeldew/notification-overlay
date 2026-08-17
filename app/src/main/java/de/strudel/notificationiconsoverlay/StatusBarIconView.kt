package de.strudel.notificationiconsoverlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Gravity
import android.view.View
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
            val image = BitmapIconView(context, loadTintedBitmap(item, iconSizePx, iconColor)).apply {
                contentDescription = item.packageName
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

    private fun loadTintedBitmap(item: NotificationIcon, size: Int, color: Int): Bitmap? {
        val drawable = loadDrawable(item) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))

        // ImageView/drawable tint APIs are ignored by some Sony notification drawables.
        // Recolor the already-rasterized alpha mask so the compositor receives final pixels.
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        // Pure black is treated as transparent by the status-bar accessibility surface on
        // some Sony builds. Use Android's near-black system-icon tone instead.
        val effectiveColor = if (color == Color.BLACK) DARK_ICON_COLOR else color
        val rgb = effectiveColor and 0x00ffffff
        pixels.indices.forEach { index ->
            pixels[index] = (Color.alpha(pixels[index]) shl 24) or rgb
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private class BitmapIconView(context: Context, private val bitmap: Bitmap?) : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }

        override fun onDetachedFromWindow() {
            bitmap?.recycle()
            super.onDetachedFromWindow()
        }
    }

    companion object {
        private const val TAG = "NotificationOverlay"
        private const val DARK_ICON_COLOR = 0xff202124.toInt()
    }
}
