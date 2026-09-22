package com.notchhud.island.service

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.notchhud.island.core.CutoutGeometry

/**
 * Reads the real camera cutout on every configuration change.
 *
 * The Fold puts the punch-hole top-centre on the cover screen and near the top of
 * the right half on the inner screen, so this must never be cached across a fold
 * event and must never be hard-coded. When a device has no cutout at all we fall
 * back to top-centre with zero width, which draws a plain pill.
 */
object CutoutReader {

    fun read(context: Context): CutoutGeometry {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val (screenW, screenH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            @Suppress("DEPRECATION")
            val d = wm.defaultDisplay
            @Suppress("DEPRECATION")
            val p = android.graphics.Point().also { d.getRealSize(it) }
            p.x to p.y
        }

        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.windowInsets.displayCutout
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.cutout
        }

        // Only the rect along the top edge is ours; a corner or waterfall cutout on
        // the side is not something the island should try to wrap.
        val rect = cutout?.boundingRects?.firstOrNull { it.top <= screenH / 4 }

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
}
