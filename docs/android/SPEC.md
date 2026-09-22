# Handoff: Island HUD for Android (Galaxy Z Fold 8)

## Overview
Android companion to the macOS Notch HUD. A Dynamic-Island-style black pill wraps the selfie-camera cutout on both Fold 8 screens and acts as the phone's live-activity surface: agent status pushed from the Mac, calls, timers, now-playing, live sports (scores / scoring plays / play-by-play), messages (SMS/RCS, WhatsApp, Teams), Outlook/Gmail, meetings with Join, weather, bookmarks that open in Chrome, and system events (charging, Buds, silent, volume, clipboard, navigation). Tap shows a detail card; **hold** expands into a tabbed panel. On the lock screen the pill shows a lock glyph and animates open on unlock.

## About the Design Files
`Island HUD Android.dc.html` is a **design reference built in HTML** — an interactive prototype of look, states and motion, not production code. Recreate it natively in **Kotlin + Jetpack Compose**. Architecture: a foreground service drawing a `TYPE_APPLICATION_OVERLAY` window (`SYSTEM_ALERT_WINDOW`) anchored to the top edge with `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`, touchable only within the pill; `NotificationListenerService` for mirroring; `MediaSessionManager` for now-playing; `InCallService` (default-dialer role, optional) or notification actions for calls; `WindowManager`/`Jetpack WindowManager` `FoldingFeature` + `DisplayCutout` for geometry. The Mac and phone share settings and agent state over a small companion link (WebSocket over LAN + push relay).


## Real data only — no placeholder content
Everything in the prototypes (LAL–BOS 98–94, "Priya Natarajan", "Nightcall", Austin weather, sample reminders/bookmarks) is **demo fixture data for the mock only**. The shipped app must never fabricate content:
- Render a module only when its source has real data. No sample games, contacts, tracks, emails or events — ever.
- Sports: show a game only if the ESPN scoreboard lists a game for a **followed team** with `status.type.state` = `pre` (within 60 min of tip-off), `in`, or `post` (for 30 min after final). Otherwise the Sports tab/slot shows "No games today for your teams" and the compact slot falls through to the next module.
- Agents: only from real hook/CLI events; none connected → "No agents connected" with a setup link.
- Weather: only after location permission + a successful fetch; otherwise "Set a location".
- Media/Calendar/Reminders/Inbox: empty states ("Nothing playing", "Nothing scheduled", "All caught up") when the OS reports nothing.
- Simulated-event buttons in the HTML are prototype scaffolding — do not build a "Simulate" UI. A hidden developer menu for injecting test events is fine but off by default.

## Fidelity
**High-fidelity.** Colors, radii, type and motion below are final. The simulated wallpaper / home grid / status bar in the prototype are context only.

## Device geometry (Galaxy Z Fold 8, not Ultra)
- **Cover screen (closed):** tall ~23:9 panel; punch-hole camera **top-center** (≈2.5 mm). Island centered on the cutout.
- **Inner screen (unfolded):** ~4:3 panel; punch-hole camera at the **top of the right half** (≈75 % of width in portrait-landscape use). Island is centered on that cutout and clamped ≥ 8 dp from the screen edge; expanded panels grow toward the center.
- Read the real cutout from `WindowInsets.displayCutout.boundingRects` on every configuration change (fold/unfold, rotation) — never hard-code. Recompute: `cx = cutout.centerX`, `cy = cutout.centerY`, `d = cutout.width`. Pill top = `cy − 17dp`, pill height 34 dp, so the cutout sits vertically centered in the pill with ≥ 5 dp of black around it. Content is laid out in two **in-flow** wings around a `d + 12dp` spacer whose center sits exactly at the cutout's x: left wing has a fixed width (`cutoutX − spacer/2 − 12dp`, min 40 dp) so it never collapses; right wing takes the remainder. Text ellipsizes inside its wing and never runs under the cutout. When the pill is centered on the cutout the wings are symmetric; when clamped to a screen edge they are not.
- Hide the island in immersive/full-screen apps (video, camera, games) unless a call or timer is live. Respect `WindowInsetsController` transitions.
- Foldables: on `FoldingFeature` state change animate the pill position (320 ms, `cubic-bezier(.2,.8,.2,1)`) rather than re-creating it.

## States

### Idle
Pill = `d + 36dp` wide × 34 dp, radius 17, black `#000000`; 2 dp bottom glow rail in the current status color (agent state color, violet `#8A5CF6` when Quiet, gray `#8A8A94` otherwise). Shadow `0 8 24 rgba(0,0,0,.45)`, hairline `rgba(255,255,255,.05)`, glow `0 0 22 color@33%`.

### Compact (live activity)
Width is **content-fitted**: `textWidth(left) + textWidth(right or 4 chars for bars) + d + 10 + 24 (dot + gaps) + 16 (padding)`, clamped to `[140, 200]` dp on the cover and `[150, 230]` dp on the inner screen (with a split bubble the cover cap is 168). The **Island size** setting scales those bounds: Minimal ×0.8 (pill height 31 dp), Compact ×1 (default), Roomy ×1.2. Idle pill is `d + 36dp`; locked pill `d + 54dp`. Left wing: 8 dp status dot (`box-shadow 0 0 10 sameColor`, pulses 1.6 s when active) + label 11.5/500 white, ellipsized. Right wing: label 11.5/500 `#D8D8DE`, or 3-bar equalizer (3 × 3 dp bars 5→14 dp, 1 s, staggered) when media plays. Every new event triggers a **bounce** (`scale 1 → 1.06,1.12 → .98,.96 → 1`, 600 ms) and a light haptic (`HapticFeedbackConstants.CONFIRM`).

**Priority (top wins):** incoming call › transient event (2.2 s default) › agent needs permission › running timer › playing media › sports play-by-play ticker › sports score › agent running › idle.

**Split island:** when a second activity is live (timer while music plays, live score while on a call, etc.) a 34 dp bubble (width = max(34, text) ) floats 6 dp beside the pill with a Plex Mono 11 glyph/value (`♫`, `04:12`, `98–94`). With a bubble present the compact pill shrinks to 176 dp on the cover screen; if `pill.right + 6 + bubble.width` would exceed `screenWidth − 8dp`, the bubble floats on the **left** instead. Status-bar items the pill/bubble would cover (battery, signal) fade out (200 ms) while the island is wider than ~220 dp or non-compact. Tap the bubble to swap it into the pill.

### Transient events (auto-dismiss)
Same compact layout, optional meter (110 × 3 dp track `rgba(255,255,255,.18)`, fill = event color). Examples: Copied (Chrome · 42 chars), Volume 70 % (meter), Charging 82 % (meter, green), Galaxy Buds connected 82 % (meter), Silent on/off (red/green), Meeting in 5 min, Teams/SMS/Email preview (sender left · text right), Scoring play (`LAL 101–94 BOS` · play text, red), Navigation step (`↰ 400 ft` · street, green, 3 s). Durations: 1.4 s system, 2.2 s messages, 2.6 s sports/meetings, 3 s navigation.

### Detail (single tap)
Width 272 dp (cover) / 340 dp (inner), radius 22, height auto. Top spacer clears the cutout; row: 40 dp tile (radius 12; artwork for media, `color@20%` with glyph otherwise) · title 13/600 + subtitle 11 `#9A9AA4` (+ 3 dp meter for media) · up to two 32 dp pill buttons (radius 16, 11.5/600): Accept `#34D27A`/`#04200F` & Decline `#FF5A5A`/white for calls; Approve (white/`#111`) & Deny (`rgba(255,255,255,.12)`) for agents; Stop for timer; ❙❙ / ›› for media. Tap again → compact. Incoming calls open Detail automatically (also on the lock screen when "Show on lock screen" is on).

### Expanded (hold ≥ 400 ms, configurable 250/400/600)
While pressing: `scale 1.04` over 400 ms as a progress cue; at threshold: haptic tick and grow to 284 dp (cover) / 400 dp (inner), radius 22, content fades in −6 dp → 0 (220 ms). Tab row (28 dp buttons, radius 9, gap 4; active = `rgba(255,255,255,.14)` + label, inactive = `.06` + icon only, Plex Mono 10 count) — Overview, Agents, Inbox, Calendar, Weather, Sports — plus a Quiet toggle at the right (violet when on). Content cards `rgba(255,255,255,.05)`, radius 12, padding 10 × 12.
- **Overview:** approval card (orange-bordered, ask text, `$ command` chip, Deny/Approve) when an agent needs you; 2-col grid of Agents / Weather / Next meeting (blue **Join**) / Sports score; now-playing row with prev/play/next; 5 bookmark tiles (32 dp, radius 9).
- **Agents:** card per agent with dot, name · project, task, state badge; approval controls inline. Footer note: status is pushed from the Mac.
- **Inbox:** mirrored notifications (24 dp app tile, title, body, Plex Mono time), Clear all, "All caught up."
- **Calendar:** today's events with Plex Mono time, color bar, meta, **Join** when a Teams/Zoom/Meet link exists.
- **Weather:** 40 dp condition icon · Plex Mono 34 temp · condition + "City · Feels like"; hi/lo, humidity, wind; 6-hour strip (hour, 18 dp icon, precipitation % in `#7FB2FF`, Plex Mono 11.5 temp).
- **Sports:** home / league + live period / away columns with Plex Mono 26 scores; play list per mode (clock Plex Mono 10 — red for scoring; text white/scoring or `#9A9AA4`).
Tap outside the pill → compact.

### Locked
While the keyguard is showing (`KeyguardManager.isKeyguardLocked`, overlay uses `FLAG_SHOW_WHEN_LOCKED` / `setShowWhenLocked`), the pill is `d + 66dp` wide (200 dp only if "Hide content when locked" is off and something is live): left wing = lock glyph (8 × 7 dp shackle, 2 dp stroke, over an 11 × 8 dp body, white), right wing = a 6 dp dim dot `rgba(255,255,255,.35)` (no text) — or the live activity's title when content is allowed. Glow rail gray. Notifications on the lock screen show sender only when content is hidden.

**Unlock animation** (on `ACTION_USER_PRESENT` / keyguard dismissed): shackle rotates −14° about its bottom-left and lifts 3 dp (500 ms ease), the right-wing dot turns green `#34D27A` with a glow, glow rail turns green, then after 700 ms the pill bounces and morphs to its compact/idle state. Fingerprint failure: horizontal shake (±4 dp, 300 ms) and red glow flash.

## Settings
- **Island size:** Minimal / Compact (default) / Roomy — scales compact and detail widths (see above).
- **Gestures:** Hold to expand (Short 250 / Default 400 / Long 600 ms); Tap shows detail (on); Haptics (on); Split island (on).
- **Lock screen:** Show on lock screen (on); Hide content when locked (on); Unlock animation (on).
- **Modules:** Agent status · Calls · Messages · Teams messages · Email · Meetings · Sports scores · Weather · Now playing · Timer & Pomodoro · Bookmarks · System.
- **Sports:** update mode (Score only / Scoring plays / Play-by-play), leagues, teams — identical data pipeline to the Mac (ESPN scoreboard + summary `plays[]`, poll 10–15 s while live).
- **Light theme:** Aurora / Ember / Mono / Candy (same hex values as Mac).
- Quiet mode rules, breakthrough list and bookmarks sync from the Mac app; Quiet also follows Android's own Do Not Disturb (`NotificationManager.currentInterruptionFilter`) and auto-enables during calls (`TelephonyManager`/`AudioManager.mode == MODE_IN_COMMUNICATION`).

## Data sources (Android)
- **Notifications:** `NotificationListenerService` — read title/text/actions for Teams, Outlook, Gmail, Messages, WhatsApp; `cancelNotification` when the user dismisses in the island; invoke `Notification.Action` PendingIntents for Reply/Join.
- **Calls:** default-dialer `InCallService` for full Accept/Decline of cellular + VoIP via Telecom; fallback: the call notification's answer/decline actions.
- **Media:** `MediaSessionManager.getActiveSessions` (requires notification-listener permission) → metadata, playback state, transport controls.
- **Agents:** companion link from the Mac app (`notchhud://agent` events relayed over WebSocket/FCM). Approve/Deny round-trip back to the Mac.
- **Calendar:** `CalendarContract` + `READ_CALENDAR`; parse join URLs.
- **Weather:** `FusedLocationProviderClient` (coarse) + Open-Meteo or WeatherKit REST; refresh 15 min.
- **Clipboard:** `ClipboardManager.OnPrimaryClipChangedListener` — fires only while the overlay has focus on Android 10+; alternatively observe copy via the accessibility service if the user grants it.
- **System:** `ACTION_POWER_CONNECTED`, `BluetoothHeadset` connection + battery via `BluetoothDevice` metadata, `RINGER_MODE_CHANGED_ACTION`, `VOLUME_CHANGED_ACTION` (hidden broadcast — poll `AudioManager` on volume-key notification).
- **Bookmarks:** `Intent(ACTION_VIEW, url).setPackage("com.android.chrome")` with fallback to default browser; favicons via `https://www.google.com/s2/favicons?domain=<host>&sz=64`.
- **Permissions to request on first run:** Draw over other apps, Notification access, Location (coarse), Phone (optional), Accessibility (optional, clipboard). Add the app to battery-optimization exemptions and, on Samsung, disable "Put unused apps to sleep."

## State model
```
fold: closed|open ; cutout: { cx, cy, d } ; locked: Bool ; unlocking: Bool
view: compact|detail|expanded ; tab ; pressing ; holdMs
activity (derived by priority): { kind: call|transient|agent|timer|media|sports|idle, left, right, color, glyph, meter?, actions[] }
bubble: String? (split island)
agents[], media, timer, call, transient, events[], game, plays[], weather, notifs[], bookmarks[]
quiet, silent, volume, battery, charging
settings: gestures{tapDetail,haptics,splitIsland}, lockOpts{showOnLock,hideContentOnLock,unlockAnim}, show{…}, sports{mode,leagues,teams}, lightTheme
```

## Design tokens
Type: IBM Plex Sans / IBM Plex Mono (or Roboto Flex + Roboto Mono if bundling is undesired). Sizes 9.5–13 UI, 18/26/34/56 display numbers. Weights 400/500/600.
Surfaces: `#000000` pill; card `rgba(255,255,255,.05)`; control `.10`–`.14`; track `.18`. Text `#FFFFFF`, `#D8D8DE`, `#9A9AA4`, `#8A8A94`, `#7A7A84`.
Status colors (Aurora): running `#3D8BFF`, permission `#F59A3A`, done `#34D27A`, error `#FF5A5A`, accent `#7FB2FF`; Quiet violet `#8A5CF6`; live-sports red `#FF6B6B`; Teams `#6264A7`; Messages green `#34C759`.
Radii: pill 17, detail/expanded 22, cards 12, buttons 8–16. Motion: 320 ms width/position, 220 ms content fade, 600 ms bounce, 400 ms press-scale, 500 ms shackle.

## Screenshots
`screenshots/` — 01 locked (cover), 02 unlock animation, 03 music live activity + split bubble, 04 incoming call detail, 05 expanded panel (hold), 06 inner screen: approval card anchored to the right-half cutout, 07 inner screen expanded Sports tab. Content in the screenshots is demo fixture data.

## Files
- `Island HUD Android.dc.html` — interactive prototype (open in a browser). Switch Closed/Unfolded, Lock/Unlock, hold the island to expand, fire events from "Simulate", configure in Settings.
- `support.js` — prototype runtime, not part of the design.
- See also `../design_handoff_notch_hud/` for the macOS app; shared modules should share one spec.
