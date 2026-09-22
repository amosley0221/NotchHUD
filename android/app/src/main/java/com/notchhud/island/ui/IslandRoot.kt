package com.notchhud.island.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notchhud.island.core.Activity
import com.notchhud.island.core.CutoutGeometry
import com.notchhud.island.core.IslandState
import com.notchhud.island.core.IslandView
import com.notchhud.island.core.SettingsSnapshot
import com.notchhud.island.core.Tokens
import com.notchhud.island.service.ServiceRuntime
import kotlinx.coroutines.delay

/**
 * The whole overlay. One black shape whose size and contents change with the
 * current view; the window wraps this composable, so anything outside it is not
 * part of the overlay and keeps receiving touches normally.
 */
@Composable
fun IslandRoot(
    onTap: () -> Unit,
    onHold: () -> Unit,
    onCollapse: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onAgentDecision: (String, Boolean) -> Unit,
    onCallAnswer: () -> Unit,
    onCallDecline: () -> Unit,
    onDismissInbox: (String) -> Unit,
    onClearInbox: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onToggleQuiet: () -> Unit,
    onMeasured: () -> Unit,
) {
    // Collected, not read: a plain field would be invisible to Compose and the
    // island would not redraw when the theme or island size changes.
    val settings by ServiceRuntime.settings.collectAsState()

    val view by IslandState.view.collectAsState()
    val locked by IslandState.locked.collectAsState()
    val unlocking by IslandState.unlocking.collectAsState()
    val cutout by IslandState.cutout.collectAsState()
    val bounceTick by IslandState.bounceTick.collectAsState()
    val quiet by IslandState.quiet.collectAsState()
    val call by IslandState.call.collectAsState()

    // Every input the priority resolver reads, so the pill recomposes when any of
    // them moves rather than only on an explicit bounce.
    val agents by IslandState.agents.collectAsState()
    val media by IslandState.media.collectAsState()
    val transient by IslandState.transient.collectAsState()
    val game by IslandState.game.collectAsState()
    val plays by IslandState.plays.collectAsState()
    val timer by IslandState.timer.collectAsState()

    val activity = remember(
        call, transient, agents, timer, media, game, plays, quiet,
        settings.theme, settings.sportsMode,
    ) {
        IslandState.resolveActivity(settings.theme, settings.sportsMode)
    }

    val density = LocalDensity.current
    val cutoutDp: Dp = with(density) { cutout.width.toDp() }
    val inner = !cutout.folded

    // An incoming call opens Detail on its own, lock screen included.
    LaunchedEffect(call) {
        if (call != null && IslandState.view.value == IslandView.COMPACT) {
            IslandState.setView(IslandView.DETAIL)
        }
    }

    // Bounce on every event: 1 → 1.08 → .97 → 1 over ~600 ms.
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(bounceTick) {
        if (bounceTick == 0L) return@LaunchedEffect
        bounce.snapTo(1f)
        bounce.animateTo(1.08f, tween(160))
        bounce.animateTo(0.97f, tween(180))
        bounce.animateTo(1f, tween(260))
    }

    val railColor by animateColorAsState(
        targetValue = when {
            locked && !unlocking -> Tokens.Idle
            unlocking -> settings.theme.done
            quiet && activity.kind == Activity.Kind.IDLE -> Tokens.Quiet
            else -> activity.color
        },
        animationSpec = tween(Tokens.FadeMs),
        label = "rail",
    )

    // "Show on lock screen" off means exactly that. The window wraps its content,
    // so collapsing to nothing leaves no pill and no touch target; an incoming call
    // still comes through, which is the one thing worth waking the island for.
    if (locked && !settings.showOnLock && call == null) {
        Box(Modifier.size(0.dp))
        return
    }

    val shape = RoundedCornerShape(
        if (view == IslandView.COMPACT) Tokens.PillRadius else Tokens.PanelRadius
    )

    val targetWidth: Dp = when (view) {
        IslandView.COMPACT -> compactWidth(activity, locked, cutoutDp, settings, inner)
        IslandView.DETAIL -> if (inner) 340.dp else 272.dp
        IslandView.EXPANDED -> if (inner) 400.dp else 284.dp
    }
    val width by animateDpAsState(targetWidth, tween(Tokens.WidthMs), label = "width")

    // Press-scale cue while the user holds, before the hold threshold trips.
    val press = remember { Animatable(1f) }

    // The gesture and the measured size belong to the outer box: its padding is
    // transparent but still takes touches, which is what makes a pill barely wider
    // than the camera possible to hold without slipping off it.
    Box(
        Modifier
            .onSizeChanged { onMeasured() }
            .pointerInput(settings.holdMs, settings.tapShowsDetail) {
                detectTapGestures(
                    onPress = {
                        press.animateTo(1.04f, tween(settings.holdMs))
                        press.animateTo(1f, tween(120))
                    },
                    onTap = { onTap() },
                    onLongPress = { onHold() },
                )
            }
            .padding(Tokens.TouchMargin)
    ) {
    Box(
        Modifier
            .widthIn(min = 80.dp)
            .width(width)
            .scale(bounce.value * press.value)
            .clip(shape)
            .background(Tokens.Pill)
    ) {
        Column(Modifier.fillMaxWidth()) {
            when (view) {
                IslandView.COMPACT -> CompactContent(
                    activity = activity,
                    locked = locked,
                    unlocking = unlocking,
                    settings = settings,
                    cutoutDp = cutoutDp,
                )
                IslandView.DETAIL -> DetailContent(
                    settings = settings,
                    cutoutDp = cutoutDp,
                    activity = activity,
                    onMediaPlayPause = onMediaPlayPause,
                    onMediaNext = onMediaNext,
                    onAgentDecision = onAgentDecision,
                    onCallAnswer = onCallAnswer,
                    onCallDecline = onCallDecline,
                )
                IslandView.EXPANDED -> ExpandedContent(
                    settings = settings,
                    cutoutDp = cutoutDp,
                    onAgentDecision = onAgentDecision,
                    onMediaPlayPause = onMediaPlayPause,
                    onMediaNext = onMediaNext,
                    onMediaPrevious = onMediaPrevious,
                    onDismissInbox = onDismissInbox,
                    onClearInbox = onClearInbox,
                    onOpenUrl = onOpenUrl,
                    onToggleQuiet = onToggleQuiet,
                    onCollapse = onCollapse,
                )
            }
            GlowRail(railColor)
        }
    }
    }

    // The unlock flourish resolves back to the normal pill by itself.
    LaunchedEffect(unlocking) {
        if (unlocking) {
            delay(700)
            IslandState.bounce()
            delay(300)
            IslandState.setUnlocking(false)
        }
    }
}

/**
 * Content-fitted width from the spec, then clamped. The cutout sits between the
 * wings, so its width is added on top of whatever the text needs.
 */
private fun compactWidth(
    activity: Activity,
    locked: Boolean,
    cutoutDp: Dp,
    settings: SettingsSnapshot,
    inner: Boolean,
): Dp {
    if (locked) return cutoutDp + 66.dp
    if (activity.kind == Activity.Kind.IDLE) return cutoutDp + 36.dp

    val leftChars = activity.left.length
    val rightChars = activity.right?.length ?: (if (activity.showBars) 4 else 0)
    // ~5.8 dp per character at 11.5 sp. Approximate on purpose: the clamp below is
    // what actually decides the size, and text ellipsizes inside its wing.
    val textDp = ((leftChars + rightChars) * 5.8f).dp
    val raw = textDp + cutoutDp + 10.dp + 24.dp + 16.dp

    val scale = settings.islandSize.scale
    val min = (if (inner) 150.dp else 140.dp) * scale
    val max = (if (inner) 230.dp else 200.dp) * scale
    return raw.coerceIn(min, max)
}

internal fun CutoutGeometry.isInner(): Boolean = !folded
