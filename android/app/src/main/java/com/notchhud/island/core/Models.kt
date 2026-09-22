package com.notchhud.island.core

import kotlinx.serialization.Serializable

enum class AgentState { RUNNING, PERMISSION, DONE, ERROR }

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val project: String,
    val task: String,
    val state: AgentState,
    val ask: String? = null,
    val command: String? = null,
)

data class MediaInfo(
    val title: String,
    val artist: String,
    val app: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val artwork: android.graphics.Bitmap? = null,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

data class CallInfo(
    val name: String,
    val source: String,
    val packageName: String,
    val video: Boolean = false,
)

/** A mirrored notification. [key] is the StatusBarNotification key so we can cancel it. */
data class InboxItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val body: String,
    val postedAt: Long,
    val kind: Kind,
) {
    enum class Kind { MESSAGE, EMAIL, TEAMS, CALL, OTHER }
}

data class CalendarEvent(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val location: String?,
    val colorArgb: Int,
    val joinUrl: String?,
) {
    fun isLive(now: Long = System.currentTimeMillis()) = now in start..end
}

data class Weather(
    val city: String,
    val tempC: Double,
    val feelsLikeC: Double,
    val hiC: Double,
    val loC: Double,
    val condition: String,
    val symbol: String,
    val humidity: Int,
    val windKph: Double,
    val alert: String? = null,
    val hourly: List<HourlyPoint> = emptyList(),
)

data class HourlyPoint(val hour: String, val tempC: Double, val symbol: String, val precipChance: Int)

data class Game(
    val id: String,
    val league: String,
    val home: String,
    val away: String,
    val homeScore: Int,
    val awayScore: Int,
    val period: String,
    val state: String, // pre | in | post
) {
    val live: Boolean get() = state == "in"
}

data class Play(
    val id: String,
    val clock: String,
    val text: String,
    val scoring: Boolean,
    val homeScore: Int,
    val awayScore: Int,
)

@Serializable
data class Bookmark(val id: String, val label: String, val url: String, val colorArgb: Int) {
    val host: String
        get() = runCatching { java.net.URI(url).host?.removePrefix("www.") ?: url }.getOrDefault(url)
}

data class TimerState(val secondsLeft: Int, val running: Boolean, val label: String = "Focus")

/** A short-lived event that takes over the pill and then expires. */
data class Transient(
    val left: String,
    val right: String?,
    val color: androidx.compose.ui.graphics.Color,
    val glyph: String? = null,
    val meter: Float? = null,
    val durationMs: Long = 1400L,
    val stamp: Long = System.currentTimeMillis(),
)

enum class SportsMode(val label: String) {
    SCORE_ONLY("Score only"),
    SCORING_PLAYS("Scoring plays"),
    PLAY_BY_PLAY("Play-by-play"),
}
