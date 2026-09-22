package com.notchhud.island.core

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class IslandView { COMPACT, DETAIL, EXPANDED }

enum class IslandTab(val label: String) {
    OVERVIEW("Overview"),
    AGENTS("Agents"),
    INBOX("Inbox"),
    CALENDAR("Calendar"),
    WEATHER("Weather"),
    SPORTS("Sports"),
}

/** What the pill is currently showing, after priority resolution. */
data class Activity(
    val kind: Kind,
    val left: String,
    val right: String?,
    val color: Color,
    val glyph: String? = null,
    val meter: Float? = null,
    val showBars: Boolean = false,
) {
    enum class Kind { CALL, TRANSIENT, AGENT_PERMISSION, TIMER, MEDIA, SPORTS_PBP, SPORTS_SCORE, AGENT_RUNNING, IDLE }
}

/**
 * Single shared store for everything the island draws. The overlay service, the
 * data collectors and the Compose tree all talk to this one object — an overlay
 * window has no activity to hang a ViewModel off, and every producer is a
 * long-lived service anyway.
 */
object IslandState {

    private val _view = MutableStateFlow(IslandView.COMPACT)
    val view: StateFlow<IslandView> = _view.asStateFlow()

    private val _tab = MutableStateFlow(IslandTab.OVERVIEW)
    val tab: StateFlow<IslandTab> = _tab.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _unlocking = MutableStateFlow(false)
    val unlocking: StateFlow<Boolean> = _unlocking.asStateFlow()

    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents: StateFlow<List<Agent>> = _agents.asStateFlow()

    private val _media = MutableStateFlow<MediaInfo?>(null)
    val media: StateFlow<MediaInfo?> = _media.asStateFlow()

    private val _call = MutableStateFlow<CallInfo?>(null)
    val call: StateFlow<CallInfo?> = _call.asStateFlow()

    private val _transient = MutableStateFlow<Transient?>(null)
    val transient: StateFlow<Transient?> = _transient.asStateFlow()

    private val _inbox = MutableStateFlow<List<InboxItem>>(emptyList())
    val inbox: StateFlow<List<InboxItem>> = _inbox.asStateFlow()

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _game = MutableStateFlow<Game?>(null)
    val game: StateFlow<Game?> = _game.asStateFlow()

    private val _plays = MutableStateFlow<List<Play>>(emptyList())
    val plays: StateFlow<List<Play>> = _plays.asStateFlow()

    private val _timer = MutableStateFlow<TimerState?>(null)
    val timer: StateFlow<TimerState?> = _timer.asStateFlow()

    private val _quiet = MutableStateFlow(false)
    val quiet: StateFlow<Boolean> = _quiet.asStateFlow()

    private val _heldCount = MutableStateFlow(0)
    val heldCount: StateFlow<Int> = _heldCount.asStateFlow()

    private val _bubble = MutableStateFlow<String?>(null)
    val bubble: StateFlow<String?> = _bubble.asStateFlow()

    private val _cutout = MutableStateFlow(CutoutGeometry.UNKNOWN)
    val cutout: StateFlow<CutoutGeometry> = _cutout.asStateFlow()

    /** Bumped on every event so the UI can replay its bounce animation. */
    private val _bounceTick = MutableStateFlow(0L)
    val bounceTick: StateFlow<Long> = _bounceTick.asStateFlow()

    fun setView(v: IslandView) { _view.value = v }
    fun setTab(t: IslandTab) { _tab.value = t }
    fun setLocked(v: Boolean) { _locked.value = v }
    fun setUnlocking(v: Boolean) { _unlocking.value = v }
    fun setMedia(m: MediaInfo?) { _media.value = m }
    fun setCall(c: CallInfo?) { _call.value = c; if (c != null) bounce() }
    fun setWeather(w: Weather?) { _weather.value = w }
    fun setEvents(e: List<CalendarEvent>) { _events.value = e }
    fun setGame(g: Game?) { _game.value = g }
    fun setPlays(p: List<Play>) { _plays.value = p }
    fun setTimer(t: TimerState?) { _timer.value = t }
    fun setQuiet(v: Boolean) { _quiet.value = v }
    fun setHeldCount(n: Int) { _heldCount.value = n }
    fun setBubble(s: String?) { _bubble.value = s }
    fun setCutout(c: CutoutGeometry) { _cutout.value = c }

    fun bounce() { _bounceTick.value = System.currentTimeMillis() }

    fun showTransient(t: Transient) {
        _transient.value = t
        bounce()
    }

    fun clearTransientIfExpired() {
        val t = _transient.value ?: return
        if (System.currentTimeMillis() - t.stamp >= t.durationMs) _transient.value = null
    }

    fun upsertAgent(agent: Agent) {
        _agents.update { list ->
            val i = list.indexOfFirst { it.id == agent.id }
            if (i >= 0) list.toMutableList().also { it[i] = agent } else list + agent
        }
        bounce()
    }

    fun removeAgent(id: String) = _agents.update { list -> list.filterNot { it.id == id } }

    fun setInbox(items: List<InboxItem>) { _inbox.value = items }
    fun addInbox(item: InboxItem) = _inbox.update { (listOf(item) + it).distinctBy { i -> i.key }.take(50) }
    fun removeInbox(key: String) = _inbox.update { list -> list.filterNot { it.key == key } }
    fun clearInbox() { _inbox.value = emptyList() }

    /**
     * Priority from the spec, top wins:
     * call > transient > agent-permission > timer > media > sports PBP > sports score > agent running > idle
     */
    fun resolveActivity(theme: LightTheme, sportsMode: SportsMode): Activity {
        val call = _call.value
        if (call != null) {
            return Activity(Activity.Kind.CALL, call.name, if (call.video) "Video" else "Call", theme.done, glyph = "☎")
        }

        val t = _transient.value
        if (t != null && System.currentTimeMillis() - t.stamp < t.durationMs) {
            return Activity(Activity.Kind.TRANSIENT, t.left, t.right, t.color, t.glyph, t.meter)
        }

        _agents.value.firstOrNull { it.state == AgentState.PERMISSION }?.let {
            return Activity(Activity.Kind.AGENT_PERMISSION, it.name, "Needs you", theme.permission, glyph = "✦")
        }

        _timer.value?.takeIf { it.running }?.let {
            return Activity(Activity.Kind.TIMER, it.label, formatClock(it.secondsLeft), theme.accent, glyph = "⏱")
        }

        _media.value?.takeIf { it.playing }?.let {
            return Activity(Activity.Kind.MEDIA, it.title, it.artist, theme.accent, showBars = true)
        }

        val g = _game.value
        if (g != null && g.live) {
            val score = "${abbr(g.away)} ${g.awayScore}–${g.homeScore} ${abbr(g.home)}"
            return if (sportsMode == SportsMode.PLAY_BY_PLAY && _plays.value.isNotEmpty()) {
                Activity(Activity.Kind.SPORTS_PBP, score, _plays.value.first().text, Tokens.LiveSports)
            } else {
                Activity(Activity.Kind.SPORTS_SCORE, score, g.period, Tokens.LiveSports)
            }
        }

        _agents.value.firstOrNull { it.state == AgentState.RUNNING }?.let {
            return Activity(Activity.Kind.AGENT_RUNNING, it.name, it.task, theme.running)
        }
        _agents.value.firstOrNull { it.state == AgentState.ERROR }?.let {
            return Activity(Activity.Kind.AGENT_RUNNING, it.name, "Error", theme.error)
        }

        val idleColor = if (_quiet.value) Tokens.Quiet else Tokens.Idle
        return Activity(Activity.Kind.IDLE, "", null, idleColor)
    }

    private fun abbr(team: String) = team.take(3).uppercase()

    fun formatClock(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}

/**
 * Where the camera cutout actually is, in px, read from WindowInsets on every
 * configuration change. Never hard-coded — the cover screen and the inner screen
 * of a Fold put it in completely different places.
 */
data class CutoutGeometry(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    /** Height of the cutout in px. The pill has to be at least this tall to hide it. */
    val height: Int = 0,
    val screenWidth: Int,
    val screenHeight: Int,
    val folded: Boolean,
) {
    companion object {
        val UNKNOWN = CutoutGeometry(0, 0, 0, 0, 0, 0, true)
    }

    val hasCutout: Boolean get() = width > 0
}
