package com.notchhud.island.data

import com.notchhud.island.core.Game
import com.notchhud.island.core.Play
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ESPN's public scoreboard/summary JSON. No key, no account.
 *
 * A game is only ever surfaced when it involves a team the user actually follows
 * and is pre (within 60 min of start), in, or post (for 30 min after final) —
 * the "real data only" rule from the spec. Nothing is invented for an empty day.
 */
class EspnRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class LeaguePath(val sport: String, val league: String) {
        val code: String get() = league.uppercase()
    }

    companion object {
        val KNOWN_LEAGUES = mapOf(
            "NFL" to LeaguePath("football", "nfl"),
            "NBA" to LeaguePath("basketball", "nba"),
            "WNBA" to LeaguePath("basketball", "wnba"),
            "MLB" to LeaguePath("baseball", "mlb"),
            "NHL" to LeaguePath("hockey", "nhl"),
            "NCAAF" to LeaguePath("football", "college-football"),
            "NCAAB" to LeaguePath("basketball", "mens-college-basketball"),
            "MLS" to LeaguePath("soccer", "usa.1"),
            "EPL" to LeaguePath("soccer", "eng.1"),
        )

        private const val PRE_WINDOW_MS = 60 * 60 * 1000L
        private const val POST_WINDOW_MS = 30 * 60 * 1000L
    }

    /**
     * Teams in a league, for the searchable picker in Settings.
     *
     * Returns null when the fetch or the parse failed, as opposed to an empty list
     * for a league that genuinely has no teams. Collapsing both into "empty" is why
     * the picker used to show nothing at all with no hint as to why.
     */
    suspend fun teams(leagueCode: String): TeamsResult = withContext(Dispatchers.IO) {
        val path = KNOWN_LEAGUES[leagueCode]
            ?: return@withContext TeamsResult.Failed("unknown league")

        val body = when (
            val result = Http.get(
                "https://site.api.espn.com/apis/site/v2/sports/${path.sport}/${path.league}/teams"
            )
        ) {
            is Http.Result.Ok -> result.body
            is Http.Result.Failed -> return@withContext TeamsResult.Failed(result.reason)
        }

        val parsed = runCatching {
            json.parseToJsonElement(body).jsonObject["sports"]!!.jsonArray[0].jsonObject["leagues"]!!
                .jsonArray[0].jsonObject["teams"]!!.jsonArray.map { entry ->
                    val team = entry.jsonObject["team"]!!.jsonObject
                    // League-qualified. ESPN numbers teams per league, so NFL 1 and
                    // NBA 1 are different teams — deduping on the bare id used to
                    // delete whole leagues from the picker.
                    val id = team["id"]!!.jsonPrimitive.content
                    val name = team["displayName"]!!.jsonPrimitive.content
                    "$leagueCode:$id" to "$name · $leagueCode"
                }
        }.getOrNull()

        parsed?.let { TeamsResult.Ok(it) } ?: TeamsResult.Failed("could not parse the response")
    }

    sealed interface TeamsResult {
        data class Ok(val teams: List<Pair<String, String>>) : TeamsResult
        data class Failed(val reason: String) : TeamsResult
    }

    /**
     * The single most relevant game across the followed leagues, or null when the
     * user's teams simply are not playing. Live games win over upcoming ones.
     */
    suspend fun currentGame(leagues: Set<String>, teamIds: Set<String>): Game? = withContext(Dispatchers.IO) {
        if (leagues.isEmpty() || teamIds.isEmpty()) return@withContext null
        val candidates = leagues.mapNotNull { code ->
            val path = KNOWN_LEAGUES[code] ?: return@mapNotNull null
            val body = Http.getString(
                "https://site.api.espn.com/apis/site/v2/sports/${path.sport}/${path.league}/scoreboard"
            ) ?: return@mapNotNull null
            runCatching { parseScoreboard(body, path.code, teamIds) }.getOrNull()
        }.flatten()

        candidates.minByOrNull { g ->
            when (g.state) { "in" -> 0; "pre" -> 1; else -> 2 }
        }
    }

    private fun parseScoreboard(body: String, leagueCode: String, teamIds: Set<String>): List<Game> {
        val root = json.parseToJsonElement(body).jsonObject
        val events = root["events"]?.jsonArray ?: return emptyList()
        val now = System.currentTimeMillis()

        return events.mapNotNull { ev ->
            val event = ev.jsonObject
            val comp = event["competitions"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
            val competitors = comp["competitors"]?.jsonArray ?: return@mapNotNull null

            val home = competitors.firstOrNull { it.jsonObject["homeAway"]?.jsonPrimitive?.content == "home" }?.jsonObject
            val away = competitors.firstOrNull { it.jsonObject["homeAway"]?.jsonPrimitive?.content == "away" }?.jsonObject
            if (home == null || away == null) return@mapNotNull null

            val homeId = home["team"]?.jsonObject?.get("id")?.jsonPrimitive?.content
            val awayId = away["team"]?.jsonObject?.get("id")?.jsonPrimitive?.content
            // Real data only: ignore every game the user does not follow.
            if (homeId !in teamIds && awayId !in teamIds) return@mapNotNull null

            val status = comp["status"]?.jsonObject ?: event["status"]?.jsonObject
            val type = status?.get("type")?.jsonObject
            val state = type?.get("state")?.jsonPrimitive?.content ?: return@mapNotNull null

            val startMs = event["date"]?.jsonPrimitive?.content?.let { parseIso(it) } ?: 0L
            val inWindow = when (state) {
                "in" -> true
                "pre" -> startMs - now in 0..PRE_WINDOW_MS
                "post" -> now - startMs < POST_WINDOW_MS + 4 * 60 * 60 * 1000L &&
                    now - (parseIso(event["date"]!!.jsonPrimitive.content)) < 8 * 60 * 60 * 1000L
                else -> false
            }
            if (!inWindow) return@mapNotNull null

            Game(
                id = event["id"]!!.jsonPrimitive.content,
                league = leagueCode,
                home = home.teamName(),
                away = away.teamName(),
                homeScore = home["score"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                awayScore = away["score"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                period = type?.get("shortDetail")?.jsonPrimitive?.content ?: "",
                state = state,
            )
        }
    }

    private fun JsonObject.teamName(): String {
        val team = this["team"]?.jsonObject ?: return "?"
        return team["shortDisplayName"]?.jsonPrimitive?.content
            ?: team["displayName"]?.jsonPrimitive?.content
            ?: "?"
    }

    /** Newest first, capped at 50 as the spec asks. */
    suspend fun plays(leagueCode: String, eventId: String): List<Play> = withContext(Dispatchers.IO) {
        val path = KNOWN_LEAGUES[leagueCode] ?: return@withContext emptyList()
        val body = Http.getString(
            "https://site.api.espn.com/apis/site/v2/sports/${path.sport}/${path.league}/summary?event=$eventId"
        ) ?: return@withContext emptyList()

        runCatching {
            val arr = json.parseToJsonElement(body).jsonObject["plays"]?.jsonArray ?: return@runCatching emptyList()
            arr.map { p ->
                val o = p.jsonObject
                Play(
                    id = o["id"]?.jsonPrimitive?.content ?: o.hashCode().toString(),
                    clock = o["clock"]?.jsonObject?.get("displayValue")?.jsonPrimitive?.content ?: "",
                    text = o["text"]?.jsonPrimitive?.content ?: "",
                    scoring = o["scoringPlay"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    homeScore = o["homeScore"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    awayScore = o["awayScore"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                )
            }.reversed().take(50)
        }.getOrDefault(emptyList())
    }

    private fun parseIso(s: String): Long = runCatching {
        java.time.Instant.parse(s.replace("Z", "Z")).toEpochMilli()
    }.getOrElse {
        runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrDefault(0L)
    }
}
