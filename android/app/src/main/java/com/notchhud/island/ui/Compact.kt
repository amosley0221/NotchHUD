package com.notchhud.island.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notchhud.island.core.Activity
import com.notchhud.island.core.SettingsSnapshot
import com.notchhud.island.core.Tokens

/**
 * The idle / live-activity pill. Content sits in two in-flow wings either side of
 * a spacer that is exactly as wide as the camera cutout, so text never runs under
 * the lens no matter where the lens is.
 */
@Composable
fun CompactContent(
    activity: Activity,
    locked: Boolean,
    unlocking: Boolean,
    settings: SettingsSnapshot,
    cutoutDp: Dp,
) {
    val height = settings.islandSize.pillHeightDp.dp

    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (locked || unlocking) {
            LockWing(unlocking = unlocking, doneColor = settings.theme.done)
            CutoutSpacer(cutoutDp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                val showContent = !settings.hideContentOnLock && activity.left.isNotBlank()
                if (unlocking) {
                    UnlockDot(settings.theme.done)
                } else if (showContent) {
                    WingText(activity.left, Tokens.TextSecondary)
                } else {
                    Box(Modifier.size(6.dp).background(Color(0x59FFFFFF), CircleShape))
                }
            }
            return@Row
        }

        // Left wing: dot + primary label. Fixed-ish share so it never collapses.
        Row(
            Modifier.weight(1f, fill = true),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            StatusDot(
                color = activity.color,
                pulsing = activity.kind != Activity.Kind.IDLE,
            )
            if (activity.left.isNotBlank()) {
                HSpace(6)
                WingText(activity.left, Tokens.TextPrimary, FontWeight.W500)
            }
        }

        CutoutSpacer(cutoutDp)

        // Right wing: secondary label, equalizer, or a meter for system events.
        Row(
            Modifier.weight(1f, fill = true),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            when {
                activity.meter != null -> Meter(activity.meter, activity.color, width = 64.dp)
                activity.showBars -> {
                    activity.right?.takeIf { it.isNotBlank() }?.let {
                        WingText(it, Tokens.TextSecondary, modifier = Modifier.weight(1f, fill = false))
                        HSpace(6)
                    }
                    Equalizer(activity.color)
                }
                else -> activity.right?.takeIf { it.isNotBlank() }?.let {
                    WingText(it, Tokens.TextSecondary)
                }
            }
        }
    }
}

/** The cutout is real estate we must leave black — spacer width = lens + 12 dp. */
@Composable
private fun CutoutSpacer(cutoutDp: Dp) {
    if (cutoutDp > 0.dp) Spacer(Modifier.width(cutoutDp + 12.dp))
    else Spacer(Modifier.width(8.dp))
}

/**
 * Lock glyph: an 11 × 8 dp body with an 8 × 7 dp shackle. On unlock the shackle
 * rotates −14° about its bottom-left and lifts 3 dp over 500 ms.
 */
@Composable
private fun LockWing(unlocking: Boolean, doneColor: Color) {
    val progress by animateFloatAsState(
        targetValue = if (unlocking) 1f else 0f,
        animationSpec = tween(Tokens.ShackleMs),
        label = "shackle",
    )
    val tint = if (unlocking) doneColor else Color.White

    Canvas(Modifier.size(width = 16.dp, height = 18.dp)) {
        val px = size.minDimension / 16f
        val bodyW = 11 * px
        val bodyH = 8 * px
        val bodyLeft = (size.width - bodyW) / 2f
        val bodyTop = size.height - bodyH - 2 * px

        val shackleW = 8 * px
        val shackleH = 7 * px
        val shackleLeft = (size.width - shackleW) / 2f
        val lift = 3 * px * progress

        rotate(degrees = -14f * progress, pivot = androidx.compose.ui.geometry.Offset(shackleLeft, bodyTop)) {
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(shackleLeft, bodyTop - shackleH - lift),
                size = androidx.compose.ui.geometry.Size(shackleW, shackleH * 2),
                style = Stroke(width = 2 * px, cap = StrokeCap.Round),
            )
        }

        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyW, bodyH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2 * px, 2 * px),
        )
    }
}

@Composable
private fun UnlockDot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
}
