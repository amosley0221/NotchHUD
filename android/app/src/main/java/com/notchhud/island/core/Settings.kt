package com.notchhud.island.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "island_settings")

data class SettingsSnapshot(
    val islandSize: IslandSize = IslandSize.COMPACT,
    val theme: LightTheme = LightTheme.AURORA,
    val holdMs: Int = 400,
    val tapShowsDetail: Boolean = true,
    val haptics: Boolean = true,
    val splitIsland: Boolean = true,
    val showOnLock: Boolean = true,
    val hideContentOnLock: Boolean = true,
    val unlockAnimation: Boolean = true,
    val modules: Map<String, Boolean> = Modules.defaults,
    val sportsMode: SportsMode = SportsMode.SCORING_PLAYS,
    val leagues: Set<String> = emptySet(),
    val teams: Set<String> = emptySet(),
    val bookmarks: List<Bookmark> = emptyList(),
    val companionUrl: String = "",
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val weatherCity: String = "",
    val useDeviceLocation: Boolean = false,
    /// Nudge along the screen, as a fraction of its width, kept separately for the
    /// two screens because the camera is in a different place on each.
    val offsetFolded: Float = 0f,
    val offsetUnfolded: Float = 0f,
    /// Vertical nudge in dp. The camera is inset from the top edge by a different
    /// amount on each screen, so the pill needs to be placeable too.
    val offsetYFolded: Float = 0f,
    val offsetYUnfolded: Float = 0f,
    /// Extra height around the cutout, in dp.
    val pillPadding: Float = 6f,
    val useCelsius: Boolean = false,
    val quietFollowsDnd: Boolean = true,
    val quietOnCall: Boolean = true,
    val quietManual: Boolean = false,
    val breakthrough: Set<String> = setOf(Modules.AGENTS, Modules.CALLS, Modules.MEETINGS, Modules.SYSTEM),
)

object Modules {
    const val AGENTS = "agents"
    const val CALLS = "calls"
    const val MESSAGES = "messages"
    const val TEAMS = "teams"
    const val EMAIL = "email"
    const val MEETINGS = "meetings"
    const val SPORTS = "sports"
    const val WEATHER = "weather"
    const val MEDIA = "media"
    const val TIMER = "timer"
    const val BOOKMARKS = "bookmarks"
    const val SYSTEM = "system"

    val all = listOf(AGENTS, CALLS, MESSAGES, TEAMS, EMAIL, MEETINGS, SPORTS, WEATHER, MEDIA, TIMER, BOOKMARKS, SYSTEM)

    val labels = mapOf(
        AGENTS to "Agent status",
        CALLS to "Calls",
        MESSAGES to "Messages",
        TEAMS to "Teams messages",
        EMAIL to "Email",
        MEETINGS to "Meetings",
        SPORTS to "Sports scores",
        WEATHER to "Weather",
        MEDIA to "Now playing",
        TIMER to "Timer & Pomodoro",
        BOOKMARKS to "Bookmarks",
        SYSTEM to "System",
    )

    val defaults: Map<String, Boolean> = all.associateWith { true }
}

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val islandSize = stringPreferencesKey("island_size")
        val theme = stringPreferencesKey("theme")
        val holdMs = intPreferencesKey("hold_ms")
        val tapShowsDetail = booleanPreferencesKey("tap_detail")
        val haptics = booleanPreferencesKey("haptics")
        val splitIsland = booleanPreferencesKey("split_island")
        val showOnLock = booleanPreferencesKey("show_on_lock")
        val hideContentOnLock = booleanPreferencesKey("hide_content_lock")
        val unlockAnimation = booleanPreferencesKey("unlock_anim")
        val modulesOff = stringSetPreferencesKey("modules_off")
        val sportsMode = stringPreferencesKey("sports_mode")
        val leagues = stringSetPreferencesKey("leagues")
        val teams = stringSetPreferencesKey("teams")
        val bookmarks = stringPreferencesKey("bookmarks_json")
        val companionUrl = stringPreferencesKey("companion_url")
        val weatherLat = stringPreferencesKey("weather_lat")
        val weatherLon = stringPreferencesKey("weather_lon")
        val weatherCity = stringPreferencesKey("weather_city")
        val useDeviceLocation = booleanPreferencesKey("use_device_location")
        val offsetFolded = floatPreferencesKey("offset_folded")
        val offsetUnfolded = floatPreferencesKey("offset_unfolded")
        val offsetYFolded = floatPreferencesKey("offset_y_folded")
        val offsetYUnfolded = floatPreferencesKey("offset_y_unfolded")
        val pillPadding = floatPreferencesKey("pill_padding")
        val useCelsius = booleanPreferencesKey("use_celsius")
        val quietFollowsDnd = booleanPreferencesKey("quiet_dnd")
        val quietOnCall = booleanPreferencesKey("quiet_call")
        val quietManual = booleanPreferencesKey("quiet_manual")
        val breakthrough = stringSetPreferencesKey("breakthrough")
    }

    val flow: Flow<SettingsSnapshot> = context.dataStore.data.map { p ->
        val off = p[Keys.modulesOff] ?: emptySet()
        SettingsSnapshot(
            islandSize = p[Keys.islandSize]?.let { runCatching { IslandSize.valueOf(it) }.getOrNull() } ?: IslandSize.COMPACT,
            theme = p[Keys.theme]?.let { runCatching { LightTheme.valueOf(it) }.getOrNull() } ?: LightTheme.AURORA,
            holdMs = p[Keys.holdMs] ?: 400,
            tapShowsDetail = p[Keys.tapShowsDetail] ?: true,
            haptics = p[Keys.haptics] ?: true,
            splitIsland = p[Keys.splitIsland] ?: true,
            showOnLock = p[Keys.showOnLock] ?: true,
            hideContentOnLock = p[Keys.hideContentOnLock] ?: true,
            unlockAnimation = p[Keys.unlockAnimation] ?: true,
            modules = Modules.all.associateWith { it !in off },
            sportsMode = p[Keys.sportsMode]?.let { runCatching { SportsMode.valueOf(it) }.getOrNull() } ?: SportsMode.SCORING_PLAYS,
            leagues = p[Keys.leagues] ?: emptySet(),
            teams = p[Keys.teams] ?: emptySet(),
            bookmarks = p[Keys.bookmarks]?.let { runCatching { json.decodeFromString<List<Bookmark>>(it) }.getOrNull() } ?: emptyList(),
            companionUrl = p[Keys.companionUrl] ?: "",
            weatherLat = p[Keys.weatherLat]?.toDoubleOrNull(),
            weatherLon = p[Keys.weatherLon]?.toDoubleOrNull(),
            weatherCity = p[Keys.weatherCity] ?: "",
            useDeviceLocation = p[Keys.useDeviceLocation] ?: false,
            offsetFolded = p[Keys.offsetFolded] ?: 0f,
            offsetUnfolded = p[Keys.offsetUnfolded] ?: 0f,
            offsetYFolded = p[Keys.offsetYFolded] ?: 0f,
            offsetYUnfolded = p[Keys.offsetYUnfolded] ?: 0f,
            pillPadding = p[Keys.pillPadding] ?: 6f,
            useCelsius = p[Keys.useCelsius] ?: false,
            quietFollowsDnd = p[Keys.quietFollowsDnd] ?: true,
            quietOnCall = p[Keys.quietOnCall] ?: true,
            quietManual = p[Keys.quietManual] ?: false,
            breakthrough = p[Keys.breakthrough] ?: setOf(Modules.AGENTS, Modules.CALLS, Modules.MEETINGS, Modules.SYSTEM),
        )
    }

    suspend fun setIslandSize(v: IslandSize) = edit { it[Keys.islandSize] = v.name }
    suspend fun setTheme(v: LightTheme) = edit { it[Keys.theme] = v.name }
    suspend fun setHoldMs(v: Int) = edit { it[Keys.holdMs] = v }
    suspend fun setTapShowsDetail(v: Boolean) = edit { it[Keys.tapShowsDetail] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[Keys.haptics] = v }
    suspend fun setSplitIsland(v: Boolean) = edit { it[Keys.splitIsland] = v }
    suspend fun setShowOnLock(v: Boolean) = edit { it[Keys.showOnLock] = v }
    suspend fun setHideContentOnLock(v: Boolean) = edit { it[Keys.hideContentOnLock] = v }
    suspend fun setUnlockAnimation(v: Boolean) = edit { it[Keys.unlockAnimation] = v }
    suspend fun setSportsMode(v: SportsMode) = edit { it[Keys.sportsMode] = v.name }
    suspend fun setLeagues(v: Set<String>) = edit { it[Keys.leagues] = v }
    suspend fun setTeams(v: Set<String>) = edit { it[Keys.teams] = v }
    suspend fun setCompanionUrl(v: String) = edit { it[Keys.companionUrl] = v }
    suspend fun setUseCelsius(v: Boolean) = edit { it[Keys.useCelsius] = v }
    suspend fun setUseDeviceLocation(v: Boolean) = edit { it[Keys.useDeviceLocation] = v }
    suspend fun setOffsetFolded(v: Float) = edit { it[Keys.offsetFolded] = v }
    suspend fun setOffsetUnfolded(v: Float) = edit { it[Keys.offsetUnfolded] = v }
    suspend fun setOffsetYFolded(v: Float) = edit { it[Keys.offsetYFolded] = v }
    suspend fun setOffsetYUnfolded(v: Float) = edit { it[Keys.offsetYUnfolded] = v }
    suspend fun setPillPadding(v: Float) = edit { it[Keys.pillPadding] = v }
    suspend fun setQuietFollowsDnd(v: Boolean) = edit { it[Keys.quietFollowsDnd] = v }
    suspend fun setQuietOnCall(v: Boolean) = edit { it[Keys.quietOnCall] = v }
    suspend fun setQuietManual(v: Boolean) = edit { it[Keys.quietManual] = v }

    suspend fun setModuleEnabled(module: String, enabled: Boolean) = edit { p ->
        val off = (p[Keys.modulesOff] ?: emptySet()).toMutableSet()
        if (enabled) off.remove(module) else off.add(module)
        p[Keys.modulesOff] = off
    }

    suspend fun setBreakthrough(module: String, allowed: Boolean) = edit { p ->
        val set = (p[Keys.breakthrough] ?: emptySet()).toMutableSet()
        if (allowed) set.add(module) else set.remove(module)
        p[Keys.breakthrough] = set
    }

    suspend fun setBookmarks(v: List<Bookmark>) = edit { it[Keys.bookmarks] = json.encodeToString(v.take(5)) }

    suspend fun setLocation(lat: Double, lon: Double, city: String) = edit {
        it[Keys.weatherLat] = lat.toString()
        it[Keys.weatherLon] = lon.toString()
        it[Keys.weatherCity] = city
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
