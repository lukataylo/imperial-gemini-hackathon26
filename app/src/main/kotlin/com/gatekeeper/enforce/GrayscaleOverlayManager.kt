package com.gatekeeper.enforce

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager

class GrayscaleOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var overlayView: View? = null
    private var isOverlayAttached = false
    private var daltonizerActive = false

    fun isGrayscaleActive(): Boolean = isOverlayAttached || daltonizerActive

    /**
     * True system-wide grayscale via the display daltonizer — the same mechanism
     * Digital Wellbeing's Bedtime mode uses. Needs WRITE_SECURE_SETTINGS, which is
     * only grantable over adb:
     *   adb shell pm grant com.gatekeeper.debug android.permission.WRITE_SECURE_SETTINGS
     *
     * A transparent overlay CANNOT desaturate the windows beneath it, so the overlay
     * path below is a visible dim, not real grayscale. This is the real one.
     */
    private fun tryEnableDaltonizer(): Boolean = try {
        val cr = context.contentResolver
        Settings.Secure.putInt(cr, "accessibility_display_daltonizer_enabled", 1)
        Settings.Secure.putInt(cr, "accessibility_display_daltonizer", 0) // 0 = monochromacy
        daltonizerActive = true
        true
    } catch (_: Exception) {
        false
    }

    private fun disableDaltonizer() {
        if (!daltonizerActive) return
        try {
            Settings.Secure.putInt(context.contentResolver,
                "accessibility_display_daltonizer_enabled", 0)
        } catch (_: Exception) {}
        daltonizerActive = false
    }

    fun enableGrayscale() {
        if (isGrayscaleActive()) return
        if (tryEnableDaltonizer()) return
        if (!Settings.canDrawOverlays(context)) return

        try {
            val view = GrayscaleFilterView(context)
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            overlayView = view
            isOverlayAttached = true
            mainHandler.post {
                try {
                    windowManager.addView(view, layoutParams)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun disableGrayscale() {
        disableDaltonizer()
        if (!isOverlayAttached || overlayView == null) return

        val view = overlayView
        overlayView = null
        isOverlayAttached = false
        // removeView must run on the main thread; the expiry timer calls this from
        // Dispatchers.Default, which previously threw and was silently swallowed.
        mainHandler.post {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
        }
    }

    private class GrayscaleFilterView(context: Context) : View(context) {
        private val paint = Paint().apply {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            colorFilter = ColorMatrixColorFilter(matrix)
        }

        init {
            setLayerType(LAYER_TYPE_HARDWARE, paint)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
        }
    }
}
