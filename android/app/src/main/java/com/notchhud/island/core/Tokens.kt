package com.notchhud.island.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens from docs/android/SPEC.md. These are declared final by the spec,
 * so they live in one place and nothing else invents a colour or a radius.
 */
object Tokens {
    // Surfaces
    val Pill = Color(0xFF000000)
    val Card = Color(0x0DFFFFFF)          // rgba(255,255,255,.05)
    val Control = Color(0x1AFFFFFF)       // .10
    val ControlActive = Color(0x24FFFFFF) // .14
    val Track = Color(0x2EFFFFFF)         // .18
    val Hairline = Color(0x0DFFFFFF)      // .05

    // Text
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFD8D8DE)
    val TextTertiary = Color(0xFF9A9AA4)
    val TextQuaternary = Color(0xFF8A8A94)
    val TextMuted = Color(0xFF7A7A84)

    // Fixed semantic colours (theme-independent)
    val Quiet = Color(0xFF8A5CF6)
    val LiveSports = Color(0xFFFF6B6B)
    val Teams = Color(0xFF6264A7)
    val Messages = Color(0xFF34C759)
    val Idle = Color(0xFF8A8A94)

    // Geometry
    val PillHeight = 34.dp
    val PillHeightMinimal = 31.dp
    val PillRadius = 17.dp
    val PanelRadius = 22.dp
    val CardRadius = 12.dp
    val ButtonRadius = 8.dp
    val DotSize = 8.dp

    /// Transparent margin around the pill that still takes touches. The idle pill
    /// is barely wider than the camera, which is a hard target to long-press
    /// without drifting off it and cancelling the gesture.
    val TouchMargin = 12.dp

    // Motion (ms)
    const val WidthMs = 320
    const val FadeMs = 220
    const val BounceMs = 600
    const val PressScaleMs = 400
    const val ShackleMs = 500
    const val PulseMs = 1600
    const val BarsMs = 1000

    // Type
    val Size9_5 = 9.5.sp
    val Size10 = 10.sp
    val Size10_5 = 10.5.sp
    val Size11 = 11.sp
    val Size11_5 = 11.5.sp
    val Size12 = 12.sp
    val Size13 = 13.sp
    val Size18 = 18.sp
    val Size26 = 26.sp
    val Size34 = 34.sp
    val Size56 = 56.sp
}

/** The four "light language" palettes. Same hex values as the macOS app. */
enum class LightTheme(
    val label: String,
    val running: Color,
    val permission: Color,
    val done: Color,
    val error: Color,
    val accent: Color,
) {
    AURORA("Aurora", Color(0xFF3D8BFF), Color(0xFFF59A3A), Color(0xFF34D27A), Color(0xFFFF5A5A), Color(0xFF7FB2FF)),
    EMBER("Ember", Color(0xFFFF7A3D), Color(0xFFFFD23D), Color(0xFF7EE08A), Color(0xFFFF4D6D), Color(0xFFFFB38A)),
    MONO("Mono", Color(0xFFFFFFFF), Color(0xFFC9C9C9), Color(0xFF8F8F8F), Color(0xFFFF5A5A), Color(0xFFFFFFFF)),
    CANDY("Candy", Color(0xFFC77DFF), Color(0xFFFF8FAB), Color(0xFF5CE1E6), Color(0xFFFF5A5A), Color(0xFFE0AAFF));

    fun forAgentState(state: AgentState): Color = when (state) {
        AgentState.RUNNING -> running
        AgentState.PERMISSION -> permission
        AgentState.DONE -> done
        AgentState.ERROR -> error
    }
}

enum class IslandSize(val label: String, val scale: Float, val pillHeightDp: Float) {
    MINIMAL("Minimal", 0.8f, 31f),
    COMPACT("Compact", 1.0f, 34f),
    ROOMY("Roomy", 1.2f, 34f),
}
