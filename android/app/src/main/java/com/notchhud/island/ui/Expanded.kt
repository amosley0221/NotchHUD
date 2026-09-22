package com.notchhud.island.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notchhud.island.core.Agent
import com.notchhud.island.core.AgentState
import com.notchhud.island.core.Bookmark
import com.notchhud.island.core.CalendarEvent
import com.notchhud.island.core.IslandState
import com.notchhud.island.core.IslandTab
import com.notchhud.island.core.SettingsSnapshot
import com.notchhud.island.core.SportsMode
import com.notchhud.island.core.Tokens
import com.notchhud.island.data.toDisplayTemp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hold ≥ holdMs. A tabbed panel — Overview, Agents, Inbox, Calendar, Weather,
 * Sports — plus the Quiet toggle. Every tab shows an honest empty state rather
 * than sample content when its source has nothing.
 */
@Composable
fun ExpandedContent(
    settings: SettingsSnapshot,
    cutoutDp: Dp,
    onAgentDecision: (String, Boolean) -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onDismissInbox: (String) -> Unit,
    onClearInbox: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onToggleQuiet: () -> Unit,
    onCollapse: () -> Unit,
) {
    val tab by IslandState.tab.collectAsState()
    val quiet by IslandState.quiet.collectAsState()
    val inbox by IslandState.inbox.collectAsState()
    val agents by IslandState.agents.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Spacer(Modifier.height(settings.islandSize.pillHeightDp.dp - 10.dp))

        TabRow(
            active = tab,
            agentCount = agents.count { it.state == AgentState.RUNNING || it.state == AgentState.PERMISSION },
            inboxCount = inbox.size,
            quiet = quiet,
            theme = settings,
            onSelect = { IslandState.setTab(it) },
            onToggleQuiet = onToggleQuiet,
            onCollapse = onCollapse,
        )

        VSpace(10)

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(androidx.compose.animation.core.tween(Tokens.FadeMs)) +
                slideInVertically(androidx.compose.animation.core.tween(Tokens.FadeMs)) { -6 },
            exit = fadeOut(),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (tab) {
                    IslandTab.OVERVIEW -> OverviewTab(settings, onAgentDecision, onMediaPlayPause, onMediaNext, onMediaPrevious, onOpenUrl)
                    IslandTab.AGENTS -> AgentsTab(settings, onAgentDecision)
                    IslandTab.INBOX -> InboxTab(onDismissInbox, onClearInbox)
                    IslandTab.CALENDAR -> CalendarTab(settings, onOpenUrl)
                    IslandTab.WEATHER -> WeatherTab(settings)
                    IslandTab.SPORTS -> SportsTab(settings)
                }
            }
        }
        VSpace(6)
    }
}

@Composable
private fun TabRow(
    active: IslandTab,
    agentCount: Int,
    inboxCount: Int,
    quiet: Boolean,
    theme: SettingsSnapshot,
    onSelect: (IslandTab) -> Unit,
    onToggleQuiet: () -> Unit,
    onCollapse: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IslandTab.entries.forEach { t ->
            val count = when (t) {
                IslandTab.AGENTS -> agentCount
                IslandTab.INBOX -> inboxCount
                else -> 0
            }
            TabButton(t, t == active, count) { onSelect(t) }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (quiet) Tokens.Quiet else Tokens.Card)
                .clickable(onClick = onToggleQuiet),
            contentAlignment = Alignment.Center,
        ) {
            Text("◐", color = Color.White, fontSize = Tokens.Size11, fontFamily = Fonts.sans)
        }
    }
}

@Composable
private fun TabButton(tab: IslandTab, active: Boolean, count: Int, onClick: () -> Unit) {
    val glyph = when (tab) {
        IslandTab.OVERVIEW -> "▦"
        IslandTab.AGENTS -> "✦"
        IslandTab.INBOX -> "✉"
        IslandTab.CALENDAR -> "▣"
        IslandTab.WEATHER -> "☀"
        IslandTab.SPORTS -> "◉"
    }
    Row(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) Tokens.ControlActive else Tokens.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = if (active) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            glyph,
            color = if (active) Color.White else Color(0xFFB8B8C2),
            fontSize = Tokens.Size11,
            fontFamily = Fonts.sans,
        )
        if (active) {
            Text(
                tab.label,
                color = Color.White,
                fontSize = Tokens.Size11,
                fontWeight = FontWeight.W500,
                fontFamily = Fonts.sans,
                maxLines = 1,
            )
        }
        if (count > 0) {
            Text(
                count.toString(),
                color = if (active) Color.White else Tokens.TextTertiary,
                fontSize = Tokens.Size10,
                fontFamily = Fonts.mono,
            )
        }
    }
}

// ------------------------------------------------------------------ Overview

@Composable
private fun OverviewTab(
    settings: SettingsSnapshot,
    onAgentDecision: (String, Boolean) -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val agents by IslandState.agents.collectAsState()
    val media by IslandState.media.collectAsState()
    val weather by IslandState.weather.collectAsState()
    val events by IslandState.events.collectAsState()
    val game by IslandState.game.collectAsState()
    val held by IslandState.heldCount.collectAsState()
    val pending = agents.firstOrNull { it.state == AgentState.PERMISSION }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (held > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Tokens.CardRadius))
                    .background(Tokens.Quiet.copy(alpha = 0.16f))
                    .clickable { IslandState.setTab(IslandTab.INBOX) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    "Quiet · $held notification${if (held == 1) "" else "s"} held",
                    color = Color(0xFFC4B5FD),
                    fontSize = Tokens.Size11_5,
                    fontFamily = Fonts.sans,
                )
            }
        }

        if (pending != null) ApprovalCard(pending, settings, onAgentDecision)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(
                "Agents",
                if (agents.isEmpty()) "No agents connected" else "${agents.size} connected",
                Modifier.weight(1f),
            ) { IslandState.setTab(IslandTab.AGENTS) }
            SummaryCard(
                "Weather",
                weather?.let { "${it.tempC.toDisplayTemp(settings.useCelsius)}° ${it.condition}" } ?: "Set a location",
                Modifier.weight(1f),
            ) { IslandState.setTab(IslandTab.WEATHER) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val next = events.firstOrNull { it.end > System.currentTimeMillis() }
            SummaryCard(
                "Next meeting",
                next?.title ?: "Nothing scheduled",
                Modifier.weight(1f),
            ) { IslandState.setTab(IslandTab.CALENDAR) }
            SummaryCard(
                "Sports",
                game?.let { "${it.away} ${it.awayScore}–${it.homeScore} ${it.home}" } ?: "No games today",
                Modifier.weight(1f),
            ) { IslandState.setTab(IslandTab.SPORTS) }
        }

        media?.let { m ->
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.title, color = Tokens.TextPrimary, fontSize = Tokens.Size12,
                            fontWeight = FontWeight.W500, fontFamily = Fonts.sans,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            m.artist.ifBlank { m.app }, color = Tokens.TextTertiary,
                            fontSize = Tokens.Size10_5, fontFamily = Fonts.sans, maxLines = 1,
                        )
                    }
                    RoundControl("‹‹", onMediaPrevious)
                    HSpace(4)
                    RoundControl(if (m.playing) "❙❙" else "▶", onMediaPlayPause)
                    HSpace(4)
                    RoundControl("››", onMediaNext)
                }
            }
        }

        if (settings.bookmarks.isNotEmpty()) {
            SectionLabel("Opens in Chrome")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                settings.bookmarks.take(5).forEach { BookmarkTile(it, onOpenUrl) }
            }
        }
    }
}

@Composable
private fun ApprovalCard(agent: Agent, settings: SettingsSnapshot, onDecision: (String, Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.CardRadius))
            .background(settings.theme.permission.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(settings.theme.permission, pulsing = true)
            HSpace(6)
            Text(
                agent.name, color = Tokens.TextPrimary, fontSize = Tokens.Size12,
                fontWeight = FontWeight.W500, fontFamily = Fonts.sans,
            )
            if (agent.project.isNotBlank()) {
                HSpace(4)
                Text("· ${agent.project}", color = Tokens.TextTertiary, fontSize = Tokens.Size11, fontFamily = Fonts.sans)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "NEEDS YOU", color = settings.theme.permission, fontSize = Tokens.Size10_5,
                fontWeight = FontWeight.W600, fontFamily = Fonts.sans,
            )
        }
        agent.ask?.let {
            VSpace(6)
            Text(it, color = Tokens.TextSecondary, fontSize = Tokens.Size12, fontFamily = Fonts.sans)
        }
        agent.command?.let {
            VSpace(6)
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Tokens.Control)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("$ $it", color = Color.White, fontSize = Tokens.Size11_5, fontFamily = Fonts.mono)
            }
        }
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            PillButton("Deny", Tokens.Control, Color.White) { onDecision(agent.id, false) }
            PillButton("Approve", Color.White, Color(0xFF111111)) { onDecision(agent.id, true) }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(Tokens.CardRadius))
            .background(Tokens.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        SectionLabel(label)
        VSpace(4)
        Text(
            value, color = Tokens.TextPrimary, fontSize = Tokens.Size12,
            fontWeight = FontWeight.W500, fontFamily = Fonts.sans,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoundControl(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Tokens.Control)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Color.White, fontSize = Tokens.Size11, fontFamily = Fonts.sans)
    }
}

@Composable
private fun BookmarkTile(bookmark: Bookmark, onOpenUrl: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(bookmark.colorArgb))
                .clickable { onOpenUrl(bookmark.url) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                bookmark.label.take(1).uppercase(), color = Color.White,
                fontSize = Tokens.Size11_5, fontWeight = FontWeight.W600, fontFamily = Fonts.sans,
            )
        }
        VSpace(3)
        Text(
            bookmark.label, color = Tokens.TextMuted, fontSize = Tokens.Size9_5,
            fontFamily = Fonts.sans, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// -------------------------------------------------------------------- Agents

@Composable
private fun AgentsTab(settings: SettingsSnapshot, onDecision: (String, Boolean) -> Unit) {
    val agents by IslandState.agents.collectAsState()

    if (agents.isEmpty()) {
        EmptyState("No agents connected", "Point Settings → Companion link at your Mac.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        agents.forEach { agent ->
            if (agent.state == AgentState.PERMISSION) {
                ApprovalCard(agent, settings, onDecision)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(settings.theme.forAgentState(agent.state), pulsing = agent.state == AgentState.RUNNING)
                        HSpace(8)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${agent.name}${if (agent.project.isNotBlank()) " · ${agent.project}" else ""}",
                                color = Tokens.TextPrimary, fontSize = Tokens.Size12,
                                fontWeight = FontWeight.W500, fontFamily = Fonts.sans, maxLines = 1,
                            )
                            Text(
                                agent.task, color = Tokens.TextTertiary, fontSize = Tokens.Size10_5,
                                fontFamily = Fonts.sans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            agent.state.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = settings.theme.forAgentState(agent.state),
                            fontSize = Tokens.Size10, fontWeight = FontWeight.W600, fontFamily = Fonts.sans,
                        )
                    }
                }
            }
        }
        Text(
            "Status is pushed from the Mac.",
            color = Tokens.TextMuted, fontSize = Tokens.Size9_5, fontFamily = Fonts.sans,
        )
    }
}

// --------------------------------------------------------------------- Inbox

@Composable
private fun InboxTab(onDismiss: (String) -> Unit, onClearAll: () -> Unit) {
    val inbox by IslandState.inbox.collectAsState()
    val time = remember0()

    if (inbox.isEmpty()) {
        EmptyState("All caught up.", null)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Inbox · ${inbox.size}")
            Spacer(Modifier.weight(1f))
            Text(
                "Clear all",
                color = Tokens.TextTertiary, fontSize = Tokens.Size10_5, fontFamily = Fonts.sans,
                modifier = Modifier.clickable(onClick = onClearAll),
            )
        }
        inbox.take(12).forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Tokens.Control),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            com.notchhud.island.service.NotificationRouter.glyphFor(item.kind),
                            color = Color.White, fontSize = Tokens.Size10, fontFamily = Fonts.sans,
                        )
                    }
                    HSpace(8)
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title.ifBlank { item.appLabel }, color = Tokens.TextPrimary,
                            fontSize = Tokens.Size11_5, fontWeight = FontWeight.W500,
                            fontFamily = Fonts.sans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.body, color = Tokens.TextTertiary, fontSize = Tokens.Size10_5,
                            fontFamily = Fonts.sans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HSpace(6)
                    Text(
                        time.format(Date(item.postedAt)), color = Tokens.TextMuted,
                        fontSize = Tokens.Size10, fontFamily = Fonts.mono,
                        modifier = Modifier.clickable { onDismiss(item.key) },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Calendar

@Composable
private fun CalendarTab(settings: SettingsSnapshot, onOpenUrl: (String) -> Unit) {
    val events by IslandState.events.collectAsState()
    val time = remember0()

    if (events.isEmpty()) {
        EmptyState("Nothing scheduled.", "Grant calendar access in Settings if this looks wrong.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()))
        events.forEach { event -> EventRow(event, time, settings, onOpenUrl) }
    }
}

@Composable
private fun EventRow(
    event: CalendarEvent,
    time: SimpleDateFormat,
    settings: SettingsSnapshot,
    onOpenUrl: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
            if (event.isLive()) {
                Text(
                    "LIVE", color = settings.theme.accent, fontSize = Tokens.Size9_5,
                    fontWeight = FontWeight.W600, fontFamily = Fonts.sans,
                )
            } else {
                Text(
                    time.format(Date(event.start)), color = Tokens.TextTertiary,
                    fontSize = Tokens.Size11, fontFamily = Fonts.mono,
                )
            }
        }
        HSpace(8)
        Box(Modifier.width(2.dp).height(28.dp).background(Color(event.colorArgb)))
        HSpace(8)
        Column(Modifier.weight(1f)) {
            Text(
                event.title, color = Tokens.TextPrimary, fontSize = Tokens.Size12,
                fontWeight = FontWeight.W500, fontFamily = Fonts.sans,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            event.location?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, color = Tokens.TextMuted, fontSize = Tokens.Size10_5,
                    fontFamily = Fonts.sans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        event.joinUrl?.let { url ->
            HSpace(6)
            PillButton("Join", Color(0xFF3D8BFF), Color.White) { onOpenUrl(url) }
        }
    }
}

// ------------------------------------------------------------------- Weather

@Composable
private fun WeatherTab(settings: SettingsSnapshot) {
    val weather by IslandState.weather.collectAsState()
    val w = weather

    if (w == null) {
        EmptyState("Set a location", "Settings → Weather. Nothing is shown until a real fetch succeeds.")
        return
    }

    val unit = settings.useCelsius
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.symbol, fontSize = Tokens.Size34, fontFamily = Fonts.sans, color = Color.White)
            HSpace(10)
            Column {
                Text(
                    "${w.tempC.toDisplayTemp(unit)}°",
                    color = Tokens.TextPrimary, fontSize = Tokens.Size34, fontFamily = Fonts.mono,
                )
                Text(
                    "${w.condition} · ${w.city}",
                    color = Tokens.TextSecondary, fontSize = Tokens.Size12,
                    fontWeight = FontWeight.W500, fontFamily = Fonts.sans,
                )
                Text(
                    "Feels like ${w.feelsLikeC.toDisplayTemp(unit)}°",
                    color = Tokens.TextMuted, fontSize = Tokens.Size10_5, fontFamily = Fonts.sans,
                )
            }
        }
        Text(
            "H ${w.hiC.toDisplayTemp(unit)}°  L ${w.loC.toDisplayTemp(unit)}°  ·  " +
                "${w.humidity}% humidity  ·  ${Math.round(w.windKph)} km/h",
            color = Tokens.TextTertiary, fontSize = Tokens.Size10_5, fontFamily = Fonts.sans,
        )
        w.alert?.let {
            Text(it, color = settings.theme.permission, fontSize = Tokens.Size11, fontFamily = Fonts.sans)
        }
        if (w.hourly.isNotEmpty()) {
            SectionLabel("Hourly")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                w.hourly.take(6).forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(h.hour, color = Tokens.TextMuted, fontSize = Tokens.Size10, fontFamily = Fonts.sans)
                        Text(h.symbol, fontSize = Tokens.Size18, fontFamily = Fonts.sans, color = Color.White)
                        Text(
                            if (h.precipChance > 0) "${h.precipChance}%" else " ",
                            color = Color(0xFF7FB2FF), fontSize = Tokens.Size9_5, fontFamily = Fonts.sans,
                        )
                        Text(
                            "${h.tempC.toDisplayTemp(unit)}°",
                            color = Tokens.TextPrimary, fontSize = Tokens.Size11_5, fontFamily = Fonts.mono,
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- Sports

@Composable
private fun SportsTab(settings: SettingsSnapshot) {
    val game by IslandState.game.collectAsState()
    val plays by IslandState.plays.collectAsState()
    val g = game

    if (g == null) {
        EmptyState("No games today for your teams", "Pick leagues and teams in Settings → Sports.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScoreColumn(g.away, g.awayScore, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel(g.league)
                Text(
                    g.period,
                    color = if (g.live) Tokens.LiveSports else Tokens.TextTertiary,
                    fontSize = Tokens.Size10_5, fontWeight = FontWeight.W600, fontFamily = Fonts.sans,
                )
            }
            ScoreColumn(g.home, g.homeScore, Modifier.weight(1f))
        }

        if (settings.sportsMode == SportsMode.SCORE_ONLY) {
            Text(
                "Score only — enable plays in Settings.",
                color = Tokens.TextMuted, fontSize = Tokens.Size10_5, fontFamily = Fonts.sans,
            )
        } else if (plays.isNotEmpty()) {
            SectionLabel(settings.sportsMode.label)
            plays.take(6).forEach { play ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        play.clock,
                        color = if (play.scoring) Tokens.LiveSports else Tokens.TextMuted,
                        fontSize = Tokens.Size10, fontFamily = Fonts.mono,
                        modifier = Modifier.width(40.dp),
                    )
                    Text(
                        play.text,
                        color = if (play.scoring) Tokens.TextPrimary else Tokens.TextTertiary,
                        fontSize = Tokens.Size11, fontFamily = Fonts.sans,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreColumn(team: String, score: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            team, color = Tokens.TextSecondary, fontSize = Tokens.Size11,
            fontFamily = Fonts.sans, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(score.toString(), color = Tokens.TextPrimary, fontSize = Tokens.Size26, fontFamily = Fonts.mono)
    }
}

// --------------------------------------------------------------------- misc

@Composable
private fun EmptyState(title: String, hint: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(title, color = Tokens.TextSecondary, fontSize = Tokens.Size12, fontFamily = Fonts.sans)
        hint?.let {
            VSpace(4)
            Text(it, color = Tokens.TextMuted, fontSize = Tokens.Size10_5, fontFamily = Fonts.sans)
        }
    }
}

@Composable
private fun remember0(): SimpleDateFormat =
    androidx.compose.runtime.remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
