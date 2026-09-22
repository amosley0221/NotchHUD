# Changelog

Notable changes per release. Each version's section becomes the top of that
release's notes on the Releases page.

## 1.0.13

- **Sports loads teams again.** Every league returned HTTP 403 because of the
  `User-Agent`. ESPN's edge answers 403 to `IslandHUD/1.0` and — counterintuitively —
  to a Chrome-like string, but 200 to OkHttp's own default. The polite custom header
  was the bug; it is gone.
- **The island covers the camera instead of sitting under it.** A 34 dp pill cannot
  hide a 35 dp cutout, and the 12 dp touch margin added in 1.0.11 pushed it a further
  12 dp down, so the top of the lens poked out. The pill now sizes itself to the
  cutout plus 10 dp, and the touch margin is on the sides and bottom only — there is
  nothing above the top edge of the screen to press.
- **The nudge no longer follows you between screens.** Folding changes the display,
  not the configuration of the context the service holds, so the fold went unnoticed
  and the island kept the other screen's geometry — and its nudge. A display listener
  catches it, with the existing monitor loop reconciling as a safety net.
- **macOS: clicking the app in Finder opens Settings.** For an agent app with no Dock
  icon and no window, clicking an already-running copy did nothing at all.

## 1.0.12

- **The island no longer flashes between two positions after folding.** The
  window-insets listener added in 1.0.11 was a feedback loop: a small overlay window
  only reports a display cutout when it happens to overlap one, so asking the view
  where the camera is made the answer depend on where the window already was.
  Placing the island moved it out from under the cutout, the answer changed back,
  and it oscillated. Geometry now comes only from display-level window metrics,
  which do not depend on the window's own position.
- **The island's position can be set by hand.** Settings → Island position prints
  the numbers the app is actually working from — screen size, whether a cutout was
  reported at all, and where — and offers a nudge slider. The cover screen and the
  unfolded screen are stored separately, because the camera is in a different place
  on each.
- **Network failures say why.** "Could not reach" for all eight leagues at once was
  equally consistent with a blocked request, a DNS failure or an HTTP error; the
  reason was being discarded. Each league now reports its own.

## 1.0.11

- **The island finds the camera on the unfolded screen.** The 1.0.6 fix was right
  about which cutout rect to take and wrong about where to ask. The window context
  it asked through was built once at startup with `createDisplayContext`, which pins
  it to the screen it was created for — so after unfolding it kept describing the
  cover screen, where the punch-hole genuinely is top-centre. Geometry is now read
  through a context built fresh on every read, preferring the attached overlay
  view's own insets, and a window-insets listener refreshes it on every fold,
  unfold and rotation.
- **Hold is easier to land.** The idle pill is barely wider than the camera, and a
  long press that drifted a few pixels off it cancelled with no feedback. The window
  now carries a 12 dp transparent margin that still takes touches.

## 1.0.10

- **The HUD sizes itself to the screen it is on.** In notch-shape mode it overlaps
  the menu bar by design, which is fine at 560 pt on a 3440 pt ultrawide — 16 % of
  the width, landing in the empty middle between the app menus and the status items
  — and unusable at the same 560 pt on a 1314 pt Sidecar iPad, where 42 % of the
  bar means eating menus at both ends. A display with no per-screen size set now
  gets a fifth of its width, never more than the designed 560 pt. On the two
  displays this was tested against that means the ultrawide is untouched and the
  iPad drops to 263 pt. Settings → Displays has an **Auto** button per screen.

## 1.0.9

- **Settings is reachable without hunting for the menu bar icon.** `notchhud://settings`
  opens it, so `open notchhud://settings` works from anywhere, and the CLI gained a
  `notchhud settings` command. The app has no Dock icon and its menu bar item can
  end up behind the notch or in an overflow, which left no obvious way in once the
  one-shot first-launch window had been used.

## 1.0.8

- **"Load teams" works, and says what it is doing.** It fetched each league one
  after another, swallowed every failure into an empty list, and showed no state at
  all — so a tap looked identical whether it was still working or had failed. The
  college league payloads alone are around a megabyte each. Leagues now load in
  parallel, the button reports progress, unreachable leagues are named, and the
  read timeout went to 30 s.
- **Weather can follow the phone.** Settings → Weather has "Use my current
  location", using `LocationManager` rather than Play Services. This also fixed a
  latent bug: the old city lookup asked Open-Meteo's *forward* search endpoint for
  coordinates, which just returns an error, so a resolved location would have shown
  raw numbers. The platform geocoder does the reverse lookup now.
- **The macOS app updates itself.** It checks its own GitHub Releases on launch and
  every six hours. An update appears as a small accent dot beside the menu bar icon
  plus a menu item that installs it, and the same thing in Settings → General. The
  installer verifies the downloaded bundle really is Notch HUD before replacing the
  running one, and clears the download quarantine so Gatekeeper does not block it.
- **Per-display sizing on macOS.** Shape and length are stored per screen, keyed by
  display UUID so they survive unplugging. Settings → Displays lists every
  connected screen with its own shape and a length slider, showing how much of that
  screen the HUD will take. Height is untouched — it is the length that crowds a
  small display like a Sidecar iPad.

## 1.0.7

Found while verifying that the macOS app from 1.0.6 actually launches: it did, and
then sat at ~12 % CPU doing nothing.

- **The glow rail no longer breathes when the HUD has nothing to say.** A
  `repeatForever` animation forces a Core Animation commit every frame for as long
  as it runs, on every panel on every screen. A profile of the idle app showed the
  main thread dominated by `CA::Transaction::flush`. The rail keeps its colour and
  holds steady unless an agent is working, media is playing, a timer is running, a
  followed game is live, Quiet is on, or the HUD is showing a toast, a call or the
  expanded panel.

## 1.0.6

The macOS app never ran, and the island missed the camera on the unfolded screen
in landscape.

- **The macOS app shipped the wrong binary.** `make_app.sh` copied the CLI to
  `Contents/MacOS/notchhud`, which is the same path as `Contents/MacOS/NotchHUD`
  on a case-insensitive filesystem — so it overwrote the app. Every build from
  1.0.0 to 1.0.5 contained the CLI under the app's name: double-clicking it printed
  usage to a terminal nobody was watching and exited, which looked exactly like
  nothing happening. The CLI now lives at `Contents/Resources/notchhud`, and the
  build fails if the app binary does not link SwiftUI.
- **Settings opens on first launch.** An agent app has no Dock icon and no window,
  so there was nothing to show that the app had started.
- **The island follows the camera when the Fold is unfolded and held sideways.**
  The cutout reader only accepted a rect in the top quarter of the screen; in
  landscape the punch-hole is against a side edge, so it was rejected and the
  island fell back to top-centre. It now takes the largest cutout rect wherever it
  is, and the window is clamped on both axes.
- **Rotations cannot be missed**, via a configuration listener on the overlay's own
  window context in addition to the service's.
- README documents clearing the Gatekeeper quarantine, which is required because
  the app is ad-hoc signed rather than notarised.

## 1.0.5

The island runs. This is the first release fixing behaviour rather than a crash.

- **The padlock now clears when you unlock.** Lock state was driven entirely by
  the `ACTION_USER_PRESENT` broadcast, which does not arrive on every unlock flow —
  when it went missing the island sat showing a padlock over an unlocked phone with
  nothing able to clear it. A monitor now reconciles against
  `KeyguardManager.isKeyguardLocked` directly, so the state cannot get stuck
  whatever the OEM broadcasts. The broadcast is kept as a fast path.
- **The unlock animation plays properly**, because it now actually gets triggered:
  the shackle rotates and lifts, the dot and glow rail turn green, the pill bounces,
  and the lock wing disappears.
- **"Show on lock screen" does something.** The toggle existed but was wired to
  nothing. Turning it off hides the island while locked; an incoming call still
  comes through.

## 1.0.4

Third and, on the evidence, final crash in the startup path. The window context
fix in 1.0.3 worked — the app now got as far as attaching the island to the
window before failing.

- **The view-tree owners are now set on the view handed to WindowManager.** They
  were on the `ComposeView`, one level down. Compose resolves its recomposer from
  the *root* view of the window, so it looked for a `ViewTreeLifecycleOwner`
  starting at the container, found nothing, and threw `IllegalStateException` the
  moment the view attached. Both views carry the owners now.
- **Crash reports name real frames.** Traces were fully obfuscated
  (`n0.a.f(SourceFile:373)`), which is useless in a report the app shows to you.
  Line numbers are kept for everything and our own class names are no longer
  renamed.

## 1.0.3

Actually fixes the crash on starting the island. Confirmed against a real trace
from a Galaxy Z Fold 8 on Android 17 (API 37), captured by the reporter added in
1.0.2.

- **The window context is now built from an explicit display.** 1.0.2 called
  `createWindowContext(type, options)` on the Service. That overload infers the
  display by calling `getDisplay()` on the receiver, and a Service is not
  associated with a display — so the call added to fix the crash threw the same
  `UnsupportedOperationException` one frame earlier. Naming the default display
  with `createDisplayContext()` first produces a context that is associated with
  one, and `createWindowContext` on that is valid.
- **Failing to build the window context no longer stops the island.** It falls
  back to the Service context and records why; the cutout read already degrades to
  display metrics.
- **`startForeground` now runs before anything that can throw,** so a startup
  failure is reported as itself rather than as a five-second foreground-service
  timeout.
- Shutdown and configuration changes tolerate a startup that aborted partway.

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
