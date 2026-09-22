package com.notchhud.island.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notchhud.island.core.Tokens

/** 8 dp status dot with a matching glow; pulses while something is actually active. */
@Composable
fun StatusDot(color: Color, pulsing: Boolean, size: androidx.compose.ui.unit.Dp = Tokens.DotSize) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = if (pulsing) 0.55f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Tokens.PulseMs / 2),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Box(
        Modifier
            .size(size)
            .alpha(if (pulsing) alpha else 1f)
            .background(color, CircleShape)
    )
}

/** Three bars, 5→14 dp, 1 s each, staggered — the "media is playing" tell. */
@Composable
fun Equalizer(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val h by transition.animateFloat(
                initialValue = 5f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(Tokens.BarsMs, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .background(color, RoundedCornerShape(1.5.dp))
            )
        }
    }
}

/** The 2 dp rail along the bottom of the pill: transparent → colour → transparent. */
@Composable
fun GlowRail(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, color, Color.Transparent)
                )
            )
    )
}

@Composable
fun Meter(progress: Float, color: Color, width: androidx.compose.ui.unit.Dp = 110.dp) {
    Box(
        Modifier
            .width(width)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Tokens.Track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Tokens.TextQuaternary,
        fontSize = Tokens.Size10_5,
        fontWeight = FontWeight.W500,
        fontFamily = Fonts.sans,
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(Tokens.CardRadius))
            .background(Tokens.Card)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) { content() }
}

@Composable
fun WingText(
    text: String,
    color: Color,
    weight: FontWeight = FontWeight.W500,
    size: androidx.compose.ui.unit.TextUnit = Tokens.Size11_5,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = if (mono) Fonts.mono else Fonts.sans,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun VSpace(dp: Int) = Spacer(Modifier.height(dp.dp))

@Composable
fun HSpace(dp: Int) = Spacer(Modifier.width(dp.dp))
