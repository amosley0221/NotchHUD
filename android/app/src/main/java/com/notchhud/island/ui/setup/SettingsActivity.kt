package com.notchhud.island.ui.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notchhud.island.core.Bookmark
import com.notchhud.island.core.IslandSize
import com.notchhud.island.core.LightTheme
import com.notchhud.island.core.Modules
import com.notchhud.island.core.SettingsRepository
import com.notchhud.island.core.SettingsSnapshot
import com.notchhud.island.core.SportsMode
import com.notchhud.island.data.EspnRepository
import com.notchhud.island.data.WeatherRepository
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SettingsRepository(applicationContext)
        setContent {
            SetupTheme {
                Surface(Modifier.fillMaxSize()) { SettingsScreen(repo) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(repo: SettingsRepository) {
    val settings by repo.flow.collectAsState(initial = SettingsSnapshot())
    val scope = rememberCoroutineScope()
    val espn = remember { EspnRepository() }
    val weather = remember { WeatherRepository() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header("Island size")
        SegmentedRow(IslandSize.entries.map { it.label }, settings.islandSize.ordinal) { i ->
            scope.launch { repo.setIslandSize(IslandSize.entries[i]) }
        }

        Header("Light theme")
        SegmentedRow(LightTheme.entries.map { it.label }, settings.theme.ordinal) { i ->
            scope.launch { repo.setTheme(LightTheme.entries[i]) }
        }

        Header("Gestures")
        SegmentedRow(
            listOf("Short 250", "Default 400", "Long 600"),
            when (settings.holdMs) { 250 -> 0; 600 -> 2; else -> 1 },
        ) { i ->
            scope.launch { repo.setHoldMs(listOf(250, 400, 600)[i]) }
        }
        ToggleRow("Tap shows detail", settings.tapShowsDetail) { scope.launch { repo.setTapShowsDetail(it) } }
        ToggleRow("Haptics", settings.haptics) { scope.launch { repo.setHaptics(it) } }
        ToggleRow("Split island", settings.splitIsland) { scope.launch { repo.setSplitIsland(it) } }

        Header("Lock screen")
        ToggleRow("Show on lock screen", settings.showOnLock) { scope.launch { repo.setShowOnLock(it) } }
        ToggleRow("Hide content when locked", settings.hideContentOnLock) { scope.launch { repo.setHideContentOnLock(it) } }
        ToggleRow("Unlock animation", settings.unlockAnimation) { scope.launch { repo.setUnlockAnimation(it) } }

        Header("Modules")
        Modules.all.forEach { module ->
            ToggleRow(Modules.labels[module] ?: module, settings.modules[module] != false) {
                scope.launch { repo.setModuleEnabled(module, it) }
            }
        }

        Header("Quiet mode")
        ToggleRow("Quiet on right now", settings.quietManual) { scope.launch { repo.setQuietManual(it) } }
        ToggleRow("Follow Do Not Disturb", settings.quietFollowsDnd) { scope.launch { repo.setQuietFollowsDnd(it) } }
        ToggleRow("Auto-silence during calls", settings.quietOnCall) { scope.launch { repo.setQuietOnCall(it) } }
        Text("Still allowed during Quiet", fontSize = 12.sp, fontWeight = FontWeight.W500)
        Modules.all.forEach { module ->
            ToggleRow("  ${Modules.labels[module] ?: module}", module in settings.breakthrough) {
                scope.launch { repo.setBreakthrough(module, it) }
            }
        }

        Header("Sports")
        SegmentedRow(SportsMode.entries.map { it.label }, settings.sportsMode.ordinal) { i ->
            scope.launch { repo.setSportsMode(SportsMode.entries[i]) }
        }
        Text("Leagues", fontSize = 12.sp, fontWeight = FontWeight.W500)
        WrapChips(
            options = EspnRepository.KNOWN_LEAGUES.keys.toList(),
            selected = settings.leagues,
        ) { code ->
            val next = settings.leagues.toMutableSet().apply { if (!add(code)) remove(code) }
            scope.launch { repo.setLeagues(next) }
        }
        TeamPicker(settings, espn) { scope.launch { repo.setTeams(it) } }

        Header("Weather")
        var city by remember { mutableStateOf("") }
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text(settings.weatherCity.ifBlank { "City" }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            scope.launch {
                weather.geocode(city)?.let { (lat, lon, name) -> repo.setLocation(lat, lon, name) }
            }
        }) { Text("Set location") }
        ToggleRow("Use Celsius", settings.useCelsius) { scope.launch { repo.setUseCelsius(it) } }

        Header("Companion link")
        var url by remember(settings.companionUrl) { mutableStateOf(settings.companionUrl) }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("ws://<your-mac>.local:8788") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { scope.launch { repo.setCompanionUrl(url.trim()) } }) { Text("Connect") }
        Text(
            "Agent status comes from the Mac app. Run `notchhud serve` there and paste " +
                "the address it prints.",
            fontSize = 11.sp,
        )

        Header("Bookmarks")
        BookmarkEditor(settings.bookmarks) { scope.launch { repo.setBookmarks(it) } }
    }
}

@Composable
private fun TeamPicker(settings: SettingsSnapshot, espn: EspnRepository, onChange: (Set<String>) -> Unit) {
    var teams by remember(settings.leagues) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    if (settings.leagues.isEmpty()) {
        Text("Pick a league first.", fontSize = 12.sp)
        return
    }

    Button(onClick = {
        scope.launch { teams = settings.leagues.flatMap { espn.teams(it) }.sortedBy { it.second } }
    }) { Text("Load teams") }

    if (teams.isNotEmpty()) {
        var query by remember { mutableStateOf("") }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search teams") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        teams.filter { it.second.contains(query, ignoreCase = true) }.take(25).forEach { (id, name) ->
            ToggleRow(name, id in settings.teams) { on ->
                onChange(settings.teams.toMutableSet().apply { if (on) add(id) else remove(id) })
            }
        }
    }
}

@Composable
private fun BookmarkEditor(bookmarks: List<Bookmark>, onChange: (List<Bookmark>) -> Unit) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    bookmarks.forEach { b ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${b.label} — ${b.host}", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                "Remove",
                fontSize = 12.sp,
                modifier = Modifier.clickable { onChange(bookmarks.filterNot { it.id == b.id }) },
            )
        }
    }
    Text("${5 - bookmarks.size} slot${if (bookmarks.size == 4) "" else "s"} left", fontSize = 11.sp)

    if (bookmarks.size < 5) {
        OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it }, label = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (label.isNotBlank() && url.isNotBlank()) {
                val palette = listOf(0xFF3D8BFF, 0xFFF59A3A, 0xFF34D27A, 0xFFC77DFF, 0xFFFF6B6B)
                onChange(
                    bookmarks + Bookmark(
                        id = java.util.UUID.randomUUID().toString(),
                        label = label.trim(),
                        url = if (url.startsWith("http")) url.trim() else "https://${url.trim()}",
                        colorArgb = palette[bookmarks.size % palette.size].toInt(),
                    )
                )
                label = ""; url = ""
            }
        }) { Text("Add bookmark") }
    }
}

@Composable
private fun Header(text: String) {
    Divider()
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.W600, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SegmentedRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label, fontSize = 12.sp) })
        }
    }
}

@Composable
private fun WrapChips(options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { code ->
                    FilterChip(
                        selected = code in selected,
                        onClick = { onToggle(code) },
                        label = { Text(code, fontSize = 12.sp) },
                    )
                }
            }
        }
    }
}
