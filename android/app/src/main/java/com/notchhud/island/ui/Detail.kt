package com.notchhud.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notchhud.island.core.Activity
import com.notchhud.island.core.AgentState
import com.notchhud.island.core.IslandState
import com.notchhud.island.core.SettingsSnapshot
import com.notchhud.island.core.Tokens

/**
 * Single tap. One row: a 40 dp tile, a title/subtitle stack, and at most two pill
 * buttons — Accept/Decline for a call, Approve/Deny for an agent, transport for
 * media. Deliberately not a panel; the panel is what hold is for.
 */
@Composable
fun DetailContent(
    settings: SettingsSnapshot,
    cutoutDp: Dp,
    activity: Activity,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onAgentDecision: (String, Boolean) -> Unit,
    onCallAnswer: () -> Unit,
    onCallDecline: () -> Unit,
) {
    val call by IslandState.call.collectAsState()
    val media by IslandState.media.collectAsState()
    val agents by IslandState.agents.collectAsState()
    val timer by IslandState.timer.collectAsState()
    val pendingAgent = agents.firstOrNull { it.state == AgentState.PERMISSION }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        // Clear the lens before any content starts.
        Spacer(Modifier.height(settings.islandSize.pillHeightDp.dp - 10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                call != null -> {
                    Tile(initials(call!!.name), settings.theme.done)
                    HSpace(10)
                    TitleStack(call!!.name, call!!.source, Modifier.weight(1f))
                    PillButton("Decline", Color(0xFFFF5A5A), Color.White, onCallDecline)
                    HSpace(6)
                    PillButton("Accept", Color(0xFF34D27A), Color(0xFF04200F), onCallAnswer)
                }

                pendingAgent != null -> {
                    Tile("✦", settings.theme.permission)
                    HSpace(10)
                    TitleStack(
                        pendingAgent.name,
                        pendingAgent.ask ?: pendingAgent.task,
                        Modifier.weight(1f),
                    )
                    PillButton("Deny", Tokens.Control, Color.White) { onAgentDecision(pendingAgent.id, false) }
                    HSpace(6)
                    PillButton("Approve", Color.White, Color(0xFF111111)) { onAgentDecision(pendingAgent.id, true) }
                }

                timer?.running == true -> {
                    Tile("⏱", settings.theme.accent)
                    HSpace(10)
                    TitleStack(
                        timer!!.label,
                        IslandState.formatClock(timer!!.secondsLeft),
                        Modifier.weight(1f),
                    )
                    PillButton("Stop", Tokens.Control, Color.White) { IslandState.setTimer(null) }
                }

                media != null -> {
                    val art = media!!.artwork
                    if (art != null) {
                        androidx.compose.foundation.Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Tile("♫", settings.theme.accent)
                    }
                    HSpace(10)
                    Column(Modifier.weight(1f)) {
                        Text(
                            media!!.title,
                            color = Tokens.TextPrimary,
                            fontSize = Tokens.Size13,
                            fontWeight = FontWeight.W600,
                            fontFamily = Fonts.sans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            media!!.artist.ifBlank { media!!.app },
                            color = Tokens.TextTertiary,
                            fontSize = Tokens.Size11,
                            fontFamily = Fonts.sans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        VSpace(4)
                        Meter(media!!.progress, settings.theme.accent, width = 120.dp)
                    }
                    PillButton(if (media!!.playing) "❙❙" else "▶", Tokens.Control, Color.White, onMediaPlayPause)
                    HSpace(6)
                    PillButton("››", Tokens.Control, Color.White, onMediaNext)
                }

                else -> {
                    Tile("•", activity.color)
                    HSpace(10)
                    TitleStack(
                        activity.left.ifBlank { "Nothing live" },
                        activity.right ?: "Tap and hold for the full panel",
                        Modifier.weight(1f),
                    )
                }
            }
        }
        VSpace(6)
    }
}

@Composable
private fun Tile(glyph: String, color: Color) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = color, fontSize = Tokens.Size18, fontFamily = Fonts.sans)
    }
}

@Composable
private fun TitleStack(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        Text(
            title,
            color = Tokens.TextPrimary,
            fontSize = Tokens.Size13,
            fontWeight = FontWeight.W600,
            fontFamily = Fonts.sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = Tokens.TextTertiary,
            fontSize = Tokens.Size11,
            fontFamily = Fonts.sans,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PillButton(label: String, background: Color, content: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = content,
            fontSize = Tokens.Size11_5,
            fontWeight = FontWeight.W600,
            fontFamily = Fonts.sans,
            maxLines = 1,
        )
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
