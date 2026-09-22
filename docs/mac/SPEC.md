# Handoff: Notch HUD for macOS

## Overview
A macOS menu-bar/notch utility that turns the MacBook notch (or a floating pill on external monitors) into a status surface. It shows agent status (Claude Code / Codex) as a **light language** (blue running, orange permission needed, green done, red error), instant visual feedback for keyboard shortcuts and system keys (copy/paste/screenshot, Caps Lock, volume, brightness, mic), and user-selectable modules: now playing, meetings, sports scores, email, Teams, reminders, Pomodoro, clipboard, files shelf. A Settings window chooses what appears.

## About the Design Files
`Notch HUD.dc.html` is a **design reference built in HTML** — an interactive prototype showing intended look, states and motion. It is not production code. Recreate it as a **native macOS app** (Swift + SwiftUI/AppKit). Recommended architecture: an `NSStatusItem`-less agent app (`LSUIElement = true`) that places a borderless, non-activating `NSPanel` per screen at `.statusBar` window level, positioned at the top-center of each `NSScreen`. Use `NSScreen.safeAreaInsets.top > 0` (or `auxiliaryTopLeftArea`) to detect a physical notch.


## Real data only — no placeholder content
Everything in the prototypes (LAL–BOS 98–94, "Priya Natarajan", "Nightcall", Austin weather, sample reminders/bookmarks) is **demo fixture data for the mock only**. The shipped app must never fabricate content:
- Render a module only when its source has real data. No sample games, contacts, tracks, emails or events — ever.
- Sports: show a game only if the ESPN scoreboard lists a game for a **followed team** with `status.type.state` = `pre` (within 60 min of tip-off), `in`, or `post` (for 30 min after final). Otherwise the Sports tab/slot shows "No games today for your teams" and the compact slot falls through to the next module.
- Agents: only from real hook/CLI events; none connected → "No agents connected" with a setup link.
- Weather: only after location permission + a successful fetch; otherwise "Set a location".
- Media/Calendar/Reminders/Inbox: empty states ("Nothing playing", "Nothing scheduled", "All caught up") when the OS reports nothing.
- Simulated-event buttons and the Settings panel in the HTML are prototype scaffolding — do not build a "Simulate" UI. A hidden developer menu for injecting test events is fine but off by default.

## Fidelity
**High-fidelity.** Colors, sizes, radii, type scale and animation timings below are final. Buttons/labels inside the HUD should be rebuilt with SwiftUI; the desktop/menu bar in the prototype is simulated context only — do not rebuild it.

## Display handling (core requirement)
- **Built-in display with notch:** HUD is a black shape fused to the notch. Compact: 560 × 34 pt (notch ≈ 150 pt wide in the middle; content lives in the two wings). Bottom corners radius 20. Top edge flush with screen top (y = 0).
- **External monitor (no notch):** three user-selectable modes:
  - *Notch shape (default):* identical geometry to the built-in display — 560 × 34 pt black shape, radius 0 0 20 20, flush with the top edge (y = 0), horizontally centered, overlapping the menu bar. The 150 pt center gap stays empty so it reads as a notch. Toasts/expanded panel behave exactly as on the MacBook.
  - *In menu bar:* 360 × 24 pt pill, radius 12, placed **inside the menu bar strip** at top-center (y = 0, 1 pt inset), between the app menus and status icons. Window level `.statusBar + 1`. This never covers app content — the menu bar is reserved space above every window (Chrome's toolbar starts below it). If the frontmost app's menus extend past screen-center (very long menu sets), shift the pill right until it clears them (measure via Accessibility `AXMenuBar` frames) or shrink to a 160 pt dot-only pill. Toasts and the expanded panel grow **downward** from the bar for their duration, like the system volume bezel.
  - *Floating pill:* 380 × 34 pt, radius 18, all-round; top at 26 pt (just below the menu bar), horizontally centered on that screen.
  - *Fake notch:* same geometry as the built-in version (560 × 34, radius 0 0 20 20) flush with the top edge.
- One HUD per screen. Events show on **all screens** (or only the screen with the mouse — expose as a setting later). State is shared.
- Re-layout on `NSApplication.didChangeScreenParametersNotification`.

## Views

### 1. Compact HUD (idle)
- Size per display rules above. Background `#000000`. Shadow `0 8 30 rgba(0,0,0,.35)`, hairline `rgba(255,255,255,.06)`, plus a colored glow `0 0 28 <stateColor>@33%`.
- **Glow rail:** 2 pt line along the bottom edge, `linear-gradient(90°, transparent, stateColor, transparent)`, animated per Motion setting.
- **Left slot** (user-selectable): 8 pt status dot (`box-shadow 0 0 10 sameColor`, pulses when active) + 12 pt / 500 label, white, ellipsized.
- **Right slot** (user-selectable): 12 pt / 500 label `#D8D8DE` + either an 8 pt dot or a 3-bar equalizer (3 × 3 pt bars, 5→14 pt height, 1 s ease-in-out, 0/.2/.4 s stagger) when media is playing.
- Slot options: Agents, Media, Weather (“72°F · Partly cloudy”, dot `#7FB2FF`, orange when an alert is active), Meeting, Sports, Focus (Pomodoro), Inbox, Clock.
- Click anywhere → Expanded. Hover may widen by 8 pt (optional).

### 2. Toast (event feedback)
- Replaces compact content for 1.4 s (2.0–2.4 s for notifications/meetings). Width animates to 460 pt (notch) / 340 pt (pill). Entry: scale .92→1 + fade, 180 ms `cubic-bezier(.2,.8,.2,1)`.
- Layout, centered row, gap 12: key cap (Plex Mono 12, `rgba(255,255,255,.14)` bg, radius 6, padding 3×8, letter-spacing .5) · label (13 / 500 white) · optional **meter** (110 × 4 pt track `rgba(255,255,255,.18)`, fill = accent, width transition 200 ms) · optional **state tag** (11 / 600 uppercase, letter-spacing .5; green for On/Live, red for Off/Muted, accent otherwise).
- Events → content:
  - ⌘C “Copied”, ⌘V “Pasted”, ⌘⇧4 “Screenshot saved”
  - Caps Lock → key `AB`, label “Caps Lock”, state On/Off
  - Volume/Brightness → meter
  - Mic → state Live/Muted
  - Email → `✉` + “Sender · subject”; Teams → `T` + “Name: message”
  - Meeting → `▣` “Design sync in 5 min”, state “Join” (clickable → open link)
  - Sports → `NBA` + “Lakers 101 – 94 Celtics”, state Live
  - Track change → `♫` + “Title — Artist”
  - Low battery → meter at level; AirPods connected → meter = AirPods battery
  - Agent done → key `Claude`, “Task complete”, state Done
- Agent state changes also fire a **sweep**: full-HUD gradient `transparent → stateColor@27% → transparent` translating −120%→120% over 900 ms ease-out.

### 2b. Incoming call
- Height grows to 44 pt, width 600 pt (notch) / 440 pt (pill). Persists until Accept/Decline or the call ends.
- Left: 30 pt round avatar (initials, app color, pulsing glow) · caller name 12.5/500 white · source line 10.5 `#9A9AA4` (“Teams · incoming call”, “FaceTime · video”). Right: **Decline** (`#FF5A5A`, white text) and **Accept** (`#34D27A`, text `#04200F`) pill buttons, 30 pt tall, radius 15, 11.5/600.
- Accept → toast “On call with <name>” state Live, then opens the app (`msteams:` / `facetime:` URL or AX press on the app's own Accept button). Decline → AX press on the native Decline button, or ignore.

### 3. Expanded panel (tabbed)
- Width 680 pt, height auto, same radii as compact. Width transition 280 ms `cubic-bezier(.2,.8,.2,1)`; content fades/slides in (−6 pt → 0) 220 ms. On notch displays reserve 20 pt at top. Padding 12 × 14 (bottom 14), vertical gap 12. Clicking inside never collapses. Collapse on: ✕ button, click/mouse-down anywhere outside the panel (global `NSEvent.addGlobalMonitorForEvents(matching: .leftMouseDown)` + local monitor), Esc, or the mouse leaving the panel for > 1.5 s (optional setting). Collapse animates width/height back with the same 280 ms curve.
- **Tab bar** (top row): one icon button per enabled module, 28 pt tall, radius 8, gap 4. Inactive: bg `rgba(255,255,255,.06)`, icon `#B8B8C2`, padding 0 8; active: bg `rgba(255,255,255,.14)`, white, padding 0 10, and the label (12/500) expands beside the icon (animate width 150 ms). Count badge (Plex Mono 10) after the icon when > 0. Right side: Quiet toggle (28 pt, violet when on) and ✕. Tabs in order: Overview, Agents (count = running + needs-you), Inbox (unread), Reminders (open), Calendar (today's events), Weather, Sports, Media, Focus, Bookmarks, Shelf. SF Symbols: `square.grid.2x2`, `sparkles`, `bubble.left`, `checklist`, `calendar`, `cloud.sun`, `sportscourt`, `music.note`, `timer`, `bookmark`, `tray`.
- Cards everywhere: `rgba(255,255,255,.05)` bg, radius 10, padding 10 × 12. Section labels 10.5 / uppercase / .8 tracking / `#8A8A94`.

**Overview tab** — (1) if quiet is holding notifications: violet-bordered row “Quiet · N notifications held” + **Review** button → Inbox tab. (2) if any agent needs permission: **approval card** (orange `#F59A3A` 1 pt border at 33%): pulsing orange dot · agent name (12.5/500) · “· project” (11 `#9A9AA4`) · right-aligned “NEEDS YOU” (10.5/600 orange); ask text (12 `#D8D8DE`); command chip (Plex Mono 11.5 white on `rgba(255,255,255,.08)`, radius 6, padding 6 × 10, prefixed `$`); right-aligned **Deny** (translucent) and **Approve** (white bg, `#111` text) buttons 11.5/600, radius 7, padding 6 × 12. Approve → agent state running + toast “Approved · <command>”; Deny → state error “Denied — waiting for instructions”. (3) grid `minmax(170, 1fr)` of summary cards (click → their tab): Agents (dot + summary), Weather (Plex Mono 18 temp + condition), Next meeting (title + blue **Join** `#3D8BFF`), Sports (score line + live period). (4) Now-playing row (38 pt art, progress, prev/play/next 28 pt round buttons).

**Agents tab** — one card per agent: dot · name “· project” · task · state badge; when state = permission the card expands with the ask, command chip, “Open in <agent>” link text, Deny / Approve.

**Inbox tab** — header “Inbox · N” + “Clear all”; rows as before (24 pt icon tile, title, body, Plex Mono time); empty state “All caught up.”

**Reminders tab** — “Today” (16/600) + “N reminders to do” (11 `#9A9AA4`), round **+** (28 pt) at right. Rows 10 pt vertical padding, 1 pt `rgba(255,255,255,.07)` dividers: 18 pt circle checkbox (1.5 pt `#6F6F78`; done = theme green fill), title 12.5/500, meta 10.5 `#7A7A84` (“Work · 4:00 PM”). Data: EventKit reminders due today.

**Calendar tab** — two columns `minmax(200,240) | 1fr`, 16 gap, 1 pt divider. Left: month label (12.5/600) + ‹ › 28 pt buttons; 7-col grid, weekday initials 9.5 `#7A7A84`; day cells 24 pt circles, 10.5 pt: selected = white bg / `#111` text 600, today = accent text 600, days with events get a 2 pt accent dot beneath. Right: selected-date label (uppercase 10.5) + round **+**; event rows: time (Plex Mono 11 `#9A9AA4`, 40 pt right-aligned; “LIVE” in accent 9.5/600 when the event is in progress) · 2 pt colored bar (calendar color) · title 12.5/500 + meta 10.5 · blue **Join** (11/600, radius 7, padding 5 × 11) when a Teams/Zoom/Meet link exists. Data: EventKit; parse join URLs from location/notes.

**Weather tab** — two columns `minmax(180,220) | 1fr`. Left: city 14/600, date line 10.5 `#7A7A84`; 44 pt condition icon (SF Symbol, multicolor) · Plex Mono 34 temp · condition 12.5/500 + “Feels like N°” 10.5; row of hi/lo, humidity, wind (10.5 `#9A9AA4`); alert line orange when present. Right: “Hourly” label + °F/°C toggle; 6 columns, each: hour (10 `#7A7A84`), 18 pt icon, precipitation % (9.5 `#7FB2FF`, blank if 0), Plex Mono 11.5 temp. Data: WeatherKit current + hourly(6).

**Sports tab** — `minmax(160,200) | 1fr`: score card (league label, live period red, team rows with Plex Mono 22 scores) · play feed (up to 6 rows, 5 pt padding, hairline dividers; clock Plex Mono 10, scoring rows white with score at right, others `#9A9AA4`). Score-only mode shows “Score only — enable plays in Settings.”

**Media tab** — 64 pt artwork (radius 12) · title 15/600 + artist 12 · progress · prev / 36 pt white play / next · source app name right (10.5 `#7A7A84`).

**Focus tab** — Plex Mono 44 timer · status line · Start/Pause (white) + Reset (translucent) buttons.

**Bookmarks tab** — label “Opens in Chrome”; 5-column grid of buttons (radius 10, card bg, hover `rgba(255,255,255,.1)`): 38 pt favicon tile (radius 10; fall back to first letter on brand color), label 11.5/500, host 9.5 `#7A7A84`. Click → `NSWorkspace.shared.open([url], withApplicationAt: <Chrome bundle URL>)` (bundle id `com.google.Chrome`; fall back to default browser if Chrome is absent) and toast “Opening <host>” (key `Chrome`). Max 5; managed in Settings (name, URL, color; favicon fetched from `https://www.google.com/s2/favicons?domain=<host>&sz=64` or the site's `/favicon.ico`).

**Shelf tab** — Clipboard (Plex Mono 11, wraps) and Files shelf (dashed border, 40 pt file tiles, drop target) side by side.

### 4. Settings
Standard macOS Settings window (or a popover from the HUD). Sections:
- **Modules** (toggle each): Agent status · Shortcut feedback · System HUD (volume, brightness, mic, battery, AirPods) · Weather · Now playing · Meetings · Sports scores · Email · Teams messages · iMessage · Calls (Teams, FaceTime) · Reminders · Pomodoro · Bookmarks · Clipboard · Files shelf.
- **Bookmarks**: list of up to 5 (name, URL, color swatch), + Add, Remove; “N slots left” counter.
- **Compact left slot / right slot**: single-select among Agents, Media, Meeting, Sports, Focus, Inbox, Clock.
- **Light theme**: Aurora (default), Ember, Mono, Candy — see tokens.
- **Motion**: Breathe (2.2 s), Pulse (0.9 s), Static.
- **External monitor**: Notch shape (default) / In menu bar / Floating pill.
- Toggle style: 36 × 22 track, radius 11, on = theme running color, knob 18 pt white, 200 ms.
- **Quiet (focus) mode**:
  - *Auto-silence when* (toggles): On a Teams / FaceTime / Zoom call (default on) · While sharing my screen (on) · When macOS Focus / Do Not Disturb is on (on) · In full-screen apps (off).
  - *Still allowed during quiet* (multi-select): Agent status, Incoming calls, Meetings, System HUD, Shortcuts (defaults on); Sports, Email, Teams messages, iMessage (defaults off).
  - Manual toggle: "Quiet" button in the expanded panel header, a global hotkey (default ⌃⌥Q), and the CLI.
  - Behavior: gated modules are **held** — stored in the inbox list but no toast/slot update. Held count shows in the panel header ("3 held", click to review) and, if the compact Inbox slot is selected, as "Quiet · 3 held". When quiet ends, a single summary toast: key `Quiet`, "N notifications held during your call", state "Review" (2.6 s), which opens the expanded panel.
  - Visual: while quiet is active and no agent state has priority, the glow rail and status dot use violet `#8A5CF6`. Agent states still override the color.
  - Detection: call = a call app (Teams, FaceTime, Zoom, Meet in browser) has the mic or camera in use — observe `kAudioHardwarePropertyProcessIsRunningInput`/CoreAudio process tap, or poll `AVCaptureDevice` in-use flags, or watch for the app's call window title via Accessibility. Screen share = `CGDisplayStream` or a Teams/Zoom sharing window present. DND = `~/Library/DoNotDisturb/DB/Assertions.json` change / `com.apple.controlcenter` Focus state via Shortcuts. Full-screen = `NSWorkspace.frontmostApplication` window frame equals screen frame.

- **Sports — live updates** (own section in Settings):
  - *Update mode* (single-select): Score only / Scoring plays (default) / Play-by-play.
  - *Leagues* (multi-select chips): NFL, NBA, MLB, NHL, NCAAF, NCAAB, MLS, EPL, … (any league ESPN exposes).
  - *Teams* (multi-select, searchable list populated from the chosen leagues' `/teams` endpoint, with logos). Only games involving a followed team are tracked.
  - Data: ESPN public JSON — `https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard` to find live games for followed teams; `…/summary?event={id}` for `plays[]` (each has `clock`, `text`, `scoringPlay`, `homeScore`/`awayScore`, `period`). Poll every 10–15 s while a followed game is `status.type.state == "in"`, every 5 min otherwise. Diff by play id; emit a toast per new play according to mode (Scoring plays → only `scoringPlay == true`; Play-by-play → all plays, but coalesce if more than one arrives in a poll, showing the latest). Fall back to score-only if the league has no play feed.
  - Compact slot: Score only → “Lakers 98 – 94 Celtics · Q4 3:12”; Scoring plays → same, with a toast on each score; Play-by-play → “98–94 · <latest play text>” ticker (ellipsize; optionally marquee).
  - Expanded Sports card: score rows, then a divider and up to 3 recent plays (Plex Mono 10 clock in `#FF6B6B` for scoring / `#7A7A84` otherwise; text 11 pt white for scoring, `#9A9AA4` otherwise). Section label = mode name.
  - Toast: key = league code, label = “98–94 · play text”, state “Live”; 2.4 s for scoring plays, 1.8 s for other plays.

## Interactions & Behavior
- HUD is `ignoresMouseEvents = false` only within its bounds; never steals focus (`.nonactivatingPanel`, `canBecomeKey = false`).
- Toasts queue; a new toast replaces the current one and resets its timer. Toast collapses the expanded panel.
- Compact ↔ Expanded ↔ Toast width transitions use `spring(response: 0.28, dampingFraction: 0.85)` or the bezier above.
- Priority of the glow/left-slot color: permission (orange) > error (red) > running (blue) > done (green).
- Agent state sources: watch Claude Code / Codex via their hooks (Claude Code `Notification`/`Stop`/`PermissionRequest` hooks calling a local URL scheme or Unix socket, e.g. `notchhud://agent?id=…&state=running&task=…`). Expose a tiny CLI `notchhud emit <json>` for anything else.
- Shortcuts: global `CGEventTap` (needs Accessibility permission) for ⌘C/⌘V/⌘⇧3/4 and `.flagsChanged` for Caps Lock. Volume/brightness via `NSEvent` media-key monitoring (`NX_KEYTYPE_SOUND_UP` etc.) — optionally suppress Apple's native bezel.
- Media: `MediaRemote` private framework (`MRMediaRemoteGetNowPlayingInfo`) or `MPNowPlayingInfoCenter` observation.
- Meetings: EventKit; detect Teams/Zoom links in notes for “Join”.
- **Mirroring other apps' notifications (iMessage, FaceTime, Teams calls, Outlook):** macOS does not allow a third-party app to intercept or suppress another app's notifications, so the HUD **mirrors** them and the user silences the originals ("Deliver quietly" per app in System Settings → Notifications; the Settings window links there and explains this). Sources, in order of preference:
  1. Direct APIs where they exist: Microsoft Graph for Outlook mail + Teams chat/calls (push via change notifications); `~/Library/Messages/chat.db` (SQLite, needs Full Disk Access) via FSEvents for iMessage sender + preview.
  2. Notification Center observation: Accessibility API watching the `NotificationCenter` process's banner windows (`AXNotificationCenterBanner`) for title/body/app — works for FaceTime and Teams incoming-call banners and lets the HUD press the native Accept/Decline via `AXPress`.
  3. Fallback: poll `~/Library/Group Containers/group.com.apple.usernoted/db2/db` (Full Disk Access) for new records.
- Email/Teams: `UNUserNotificationCenter` can’t read other apps' notifications; use Mail/Outlook via Gmail/Graph APIs, Teams via Graph `/me/chats/getAllMessages` with delta polling, or a Shortcuts/Automation bridge.
- **Weather:** location from CoreLocation (`kCLLocationAccuracyReduced` is enough; ask once, fall back to a manually entered city). Data from **WeatherKit** (`WeatherService.shared.weather(for:)` — current, daily hi/lo, `weatherAlerts`, minute-precipitation for “Rain starting in N min”). Refresh every 15 min, on wake, and on significant location change. Unit follows `Locale.current.measurementSystem` with a manual override. Toasts: key `WX`; precipitation-soon (2.2 s), severe alert with state “Alert” in the theme's orange (2.6 s). Weather toasts are gated by the System HUD breakthrough rule.
- Reminders: EventKit `EKReminder`. Clipboard: `NSPasteboard.general.changeCount` polling 0.5 s. Files shelf: `NSDraggingDestination` on the panel.

## State Management
```
display: { hasNotch: Bool, extMode: pill|mirror }   // per NSScreen
mode: compact | toast(Toast) | call(Call) | expanded
tab: overview|agents|inbox|reminders|calendar|weather|sports|media|focus|bookmarks|shelf ; calendar: { monthOffset, selectedDate }
bookmarks: [{ id, label, url, color }]   // max 5
events: [{ id, date, time, title, meta, color, joinURL? }]
quiet: { manual: Bool, onCall: Bool, sharing: Bool, dnd: Bool, fullscreen: Bool, held: [Notification], autoRules: [rule: Bool], breakthrough: [module: Bool] }
// isQuiet = manual || (onCall && rules.calls) || (sharing && rules.screenShare) || (dnd && rules.dnd) || (fullscreen && rules.fullscreen)
call: { name, source, app, avatar?, acceptAction, declineAction }
toast: { keys, label, meter?: 0…1, state?: String, duration }
theme: aurora|ember|mono|candy ; motion: breathe|pulse|static
show: [module: Bool] ; leftSlot, rightSlot: SlotKind
agents: [{ id, name, project, task, state: running|permission|done|error, ask?, command? }]
media: { title, artist, artwork, playing, progress }
meeting: { title, start, joinURL? }
game: { id, league, home, away, homeScore, awayScore, period, live }
plays: [{ id, clock, text, scoring, homeScore, awayScore }]   // newest first, cap 50
sports: { mode: score|scoring|pbp, leagues: [String], teams: [teamId] }
weather: { temp, feelsLike, hi, lo, condition, symbol, city, humidity, wind, unit: F|C, alert?: String, precipSoon?: minutes, hourly: [{ hour, temp, symbol, precipChance }] }
notifs: [{ id, kind: email|teams|imessage, title, body, time }]
reminders: [{ id, text, done }]
pomodoro: { secondsLeft, running }
system: { volume, brightness, micLive, caps, battery }
```
Persist settings in `UserDefaults`. Derived: `primaryState`, `glowColor`.

## Design Tokens
Type: **IBM Plex Sans** (UI) and **IBM Plex Mono** (keys, numbers, times); fall back to SF Pro / SF Mono if bundling fonts is undesired.
Sizes: 9.5, 10, 10.5 (section labels), 11, 11.5, 12, 12.5, 13, 14, 15, 16, 18, 20, 22, 34, 44 (Plex Mono display numbers). Weights 400/500/600.

Surfaces: HUD `#000000`; card `rgba(255,255,255,.05)`; control `rgba(255,255,255,.10)`; keycap `rgba(255,255,255,.14)`; track `rgba(255,255,255,.18)`.
Text: `#FFFFFF`, `#D8D8DE`, `#9A9AA4`, `#8A8A94`, `#7A7A84`.

Light themes (running / permission / done / error / accent):
- Aurora `#3D8BFF / #F59A3A / #34D27A / #FF5A5A / #7FB2FF`
- Ember `#FF7A3D / #FFD23D / #7EE08A / #FF4D6D / #FFB38A`
- Mono `#FFFFFF / #C9C9C9 / #8F8F8F / #FF5A5A / #FFFFFF`
- Candy `#C77DFF / #FF8FAB / #5CE1E6 / #FF5A5A / #E0AAFF`
Other: quiet violet `#8A5CF6` (link text `#C4B5FD`), Teams `#6264A7`, iMessage green `#34C759`, live-sports red `#FF6B6B`.

Radii: HUD 18 (pill) / 0 0 20 20 (notch); card 10; button 6–7; keycap 6; badge 5.
Spacing: 8 / 10 / 12 / 14 / 16.
Motion: 180 ms pop, 220 ms fade-in, 280 ms width, 900 ms sweep, glow breathe 2.2 s / pulse .9 s, bars 1 s.

## Screenshots
`screenshots/` — 01 MacBook compact (agent running), 02 permission approval card in Overview, 03 Caps Lock toast, 04 external monitor notch-shape mode, 05 expanded Calendar tab, 06 expanded Weather tab, 07 Sports tab with play-by-play, 08 Quiet mode holding notifications. Content in the screenshots is demo fixture data.

## Assets
None required. Glyphs in the prototype are Unicode placeholders — use SF Symbols: `doc.on.doc`, `doc.on.clipboard`, `camera.viewfinder`, `speaker.wave.2`, `sun.max`, `capslock`, `mic.slash`, `envelope`, `calendar`, `sportscourt`, `music.note`, `battery.25`, `airpodspro`.

## Files
- `Notch HUD.dc.html` — interactive prototype (open in a browser). Top bar switches MacBook/external monitor and light/dark desktop; “Simulate events” buttons fire every toast; Settings panel on the right controls modules, slots, themes, motion, external-monitor mode.
- `support.js` — runtime the prototype needs to open; not part of the design.
