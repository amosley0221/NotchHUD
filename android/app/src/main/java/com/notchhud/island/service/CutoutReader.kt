package com.notchhud.island.service

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.notchhud.island.core.CutoutGeometry

/**
 * Reads the real camera cutout on every configuration change.
 *
 * The Fold puts the punch-hole top-centre on the cover screen and near the top of
 * the right half on the inner screen, so this must never be cached across a fold
 * event and must never be hard-coded.
 *
 * Note which WindowManager gets passed in: [WindowManager.getCurrentWindowMetrics]
 * is only valid on a *visual* context — an Activity, or one built with
 * `createWindowContext`. A plain Service context throws
 * `UnsupportedOperationException`, so the overlay service builds a window context
 * and hands us that one's WindowManager.
 */
object CutoutReader {

    fun read(context: Context, windowManager: WindowManager): CutoutGeometry {
        val metrics = runCatching { screenSize(context, windowManager) }
            .getOrElse { fallbackSize(context) }

        val cutout = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.windowInsets.displayCutout
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.cutout
            }
        }.getOrNull()

        val (screenW, screenH) = metrics

        // The largest cutout rect, wherever it sits. This used to be filtered to the
        // top quarter of the screen, which is wrong on a Fold: unfolded and held
        // sideways the punch-hole is against a side edge, the filter rejected it,
        // and the island fell back to top-centre — nowhere near the camera.
        val rect = cutout?.boundingRects?.maxByOrNull { it.width().toLong() * it.height().toLong() }

        // A Fold is "open" when the window is close to square; the cover screen is
        // a tall 23:9 strip. Cheaper and more reliable across OEM skins than
        // subscribing to FoldingFeature just to answer this one question.
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
            // No cutout, or we could not read one: centre a plain pill at the top.
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

    private fun screenSize(context: Context, windowManager: WindowManager): Pair<Int, Int> =
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

    /** Last resort if the window metrics are unavailable — never crash over geometry. */
    private fun fallbackSize(context: Context): Pair<Int, Int> {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }
}
