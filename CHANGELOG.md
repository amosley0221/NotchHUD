# Changelog

Notable changes per release. Each version's section becomes the top of that
release's notes on the Releases page.

## 1.0.2

Fixes the crash on starting the island.

- **The overlay now builds from a window context.** `CutoutReader` asked a plain
  Service context for `getCurrentWindowMetrics`, which is only legal on a visual
  context — an Activity, or one from `createWindowContext`. On a Service it throws
  `UnsupportedOperationException`, and it was the first thing the service did after
  going foreground, so the app died before the island was ever added to the screen.
  The service now creates a `TYPE_APPLICATION_OVERLAY` window context and uses it
  for the metrics, the window, the Compose view and the density.
- **Reading the cutout can no longer take the app down.** Each geometry read falls
  back to the display metrics, and an unreadable cutout draws a plain centred pill
  instead of failing.
- **Startup failures are reported instead of vanishing.** An uncaught exception is
  written to a file and the setup screen shows it on next launch, with a Copy
  button — an overlay service otherwise dies with nothing on screen to explain it.
  Failing to add the overlay window (usually a revoked permission) is recorded the
  same way.

## 1.0.1

Two reactivity bugs found reviewing the first build.

- **The island now redraws when you change a setting.** `ServiceRuntime` held the settings snapshot in a plain field, which Compose cannot observe, so changing the theme, the island size or the hold duration did nothing visible until some unrelated state happened to force a recomposition. It is a `StateFlow` now, and the island collects it.
- **macOS no longer rebuilds every HUD panel on every settings write.** Any `@AppStorage` change — including toggling Quiet — tore down and recreated the panel on each screen. Only the external-display mode actually changes panel geometry, so only that triggers a rebuild; the SwiftUI views already observe everything else.
- Polling loops check their own coroutine for cancellation rather than the service-wide scope, so a settings change stops the old poller deterministically.

## 1.0.0

First build of both apps from the design handoff.

**Island HUD (Android)**
- Overlay island anchored to the real display cutout, re-read on every fold, unfold and rotation — cover screen and inner screen both handled, never hard-coded.
- Compact, detail (tap) and expanded (hold) views with the bounce, press-scale and width animations from the spec.
- Lock-screen pill with the shackle unlock animation; content hidden while locked by default.
- Priority resolution: call › transient › agent-needs-you › timer › media › sports play-by-play › sports score › agent running › idle.
- Notification mirroring via `NotificationListenerService`, including invoking an app's own Answer/Decline actions for incoming calls.
- Now playing and transport controls via `MediaSessionManager`.
- Live scores from ESPN for followed teams only, at 12 s while a game is live and 5 min otherwise; scoring-play and play-by-play toasts.
- Weather from Open-Meteo, today's meetings from `CalendarContract` with Join-link parsing, bookmarks that open in Chrome.
- Quiet mode following Do Not Disturb and call state, with a breakthrough list and a held-notification count.
- Companion link to the Mac over WebSocket, with Approve/Deny travelling back.
- Settings for island size, theme, gestures, lock screen, modules, Quiet rules, sports, weather and bookmarks.

**Notch HUD (macOS)**
- One non-activating `NSPanel` per screen, re-laid out on screen changes; notch detection via `safeAreaInsets`, plus notch-shape, in-menu-bar and floating-pill modes for external displays.
- Compact slots, toasts with key caps/meters/state tags, the incoming-call row, and the agent-state sweep.
- Expanded panel with all eleven tabs: Overview, Agents, Inbox, Reminders, Calendar, Weather, Sports, Media, Focus, Bookmarks, Shelf.
- Agent bridge: line-delimited JSON on `127.0.0.1:8787`, the `notchhud://` URL scheme, and a WebSocket server for the phone.
- EventKit meetings and reminders, Open-Meteo weather, ESPN sports, clipboard shelf, Pomodoro, drag-and-drop file shelf.
- Quiet mode from call detection, Focus state and full-screen, with held notifications and a summary toast when it ends.
- Shortcut feedback through a listen-only `CGEventTap`; the four light themes and three motion styles.
- `notchhud` CLI for pushing agent state from hooks and scripts.

**Build**
- Release workflow that signs the APK with a fixed certificate, refuses a `versionCode` that does not move forward, verifies the signature, and publishes both apps to a GitHub Release with generated notes.
