package com.notchhud.island.service

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.notchhud.island.core.CutoutGeometry

/**
 * Reads the real camera cutout.
 *
 * Two things make this harder than it looks on a Fold.
 *
 * First, [WindowManager.getCurrentWindowMetrics] is only valid on a *visual*
 * context, and a Service is not one — it throws. A window context built on an
 * explicit display is.
 *
 * Second, and the reason the island used to stay stuck in the middle of the inner
 * screen: that window context must be built **fresh on every read**. A context
 * made once at startup reports the geometry of the screen it was created for, so
 * after unfolding it kept describing the cover screen — where the punch-hole
 * really is top-centre. Nothing here is cached.
 */
object CutoutReader {

    /** Preferred path: ask the attached overlay view, which is on the live display. */
    fun read(context: Context, view: View?): CutoutGeometry {
        val fromView = view?.let { readFromView(it) }
        if (fromView != null && fromView.hasCutout) return fromView
        return read(context)
    }

    fun read(context: Context): CutoutGeometry {
        val windowContext = windowContext(context)
        val windowManager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val (screenW, screenH) = runCatching { screenSize(windowManager) }
            .getOrElse {
                val metrics = context.resources.displayMetrics
                metrics.widthPixels to metrics.heightPixels
            }

        val cutout = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.windowInsets.displayCutout
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.cutout
            }
        }.getOrNull()

        return geometry(
            rect = cutout?.boundingRects?.maxByOrNull { it.width().toLong() * it.height().toLong() },
            screenW = screenW,
            screenH = screenH,
        )
    }

    private fun readFromView(view: View): CutoutGeometry? {
        val insets = view.rootWindowInsets ?: return null
        val cutout = insets.displayCutout ?: return null
        val rect = cutout.boundingRects.maxByOrNull { it.width().toLong() * it.height().toLong() }
            ?: return null

        val metrics = view.context.resources.displayMetrics
        return geometry(rect, metrics.widthPixels, metrics.heightPixels)
    }

    private fun geometry(rect: android.graphics.Rect?, screenW: Int, screenH: Int): CutoutGeometry {
        // A Fold is "open" when the window is close to square; the cover screen is
        // a tall strip. Cheaper and steadier across OEM skins than subscribing to
        // FoldingFeature just to answer this one question.
        val aspect = if (screenH > 0) screenW.toFloat() / screenH.toFloat() else 0.5f
        val folded = aspect < 0.62f

        return if (rect != null) {
            CutoutGeometry(
                centerX = rect.centerX(),
                centerY = rect.centerY(),
                width = rect.width(),
                screenWidth = screenW,
                screenHeight = screenH,
                folded = folded,
            )
        } else {
            CutoutGeometry(
                centerX = screenW / 2,
                centerY = 0,
                width = 0,
                screenWidth = screenW,
                screenHeight = screenH,
                folded = folded,
            )
        }
    }

    /**
     * A fresh window context, every time. `createWindowContext(type, options)`
     * infers its display by calling `getDisplay()` on the receiver, which a Service
     * does not have, so the display is named explicitly first.
     */
    private fun windowContext(context: Context): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return context
        return runCatching {
            val displays = context.getSystemService(DisplayManager::class.java)
            val display = displays.getDisplay(Display.DEFAULT_DISPLAY)
            context.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        }.getOrDefault(context)
    }

    private fun screenSize(windowManager: WindowManager): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            val point = android.graphics.Point().also { display.getRealSize(it) }
            point.x to point.y
        }
}
