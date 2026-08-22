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
    private var overlayView: View? = null
    private var isOverlayAttached = false

    fun isGrayscaleActive(): Boolean = isOverlayAttached

    fun enableGrayscale() {
        if (isOverlayAttached) return
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

            windowManager.addView(view, layoutParams)
            overlayView = view
            isOverlayAttached = true
        } catch (_: Exception) {}
    }

    fun disableGrayscale() {
        if (!isOverlayAttached || overlayView == null) return

        try {
            windowManager.removeView(overlayView)
        } catch (_: Exception) {}
        overlayView = null
        isOverlayAttached = false
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
