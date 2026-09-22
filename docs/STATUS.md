# What is wired up, and what is not

The spec in `mac/SPEC.md` and `android/SPEC.md` describes the finished product.
This file says where the code actually is, so nothing here is a surprise later.

## Working end to end

| | macOS | Android |
|---|---|---|
| HUD shell (compact / toast / detail / expanded) | ✅ | ✅ |
| Cutout & notch geometry, multi-screen / fold handling | ✅ | ✅ |
| Light themes, motion styles, glow rail, sweep | ✅ | ✅ |
| Agent status, approval card, Approve/Deny | ✅ | ✅ |
| Companion link (Mac ⇄ phone) | ✅ server | ✅ client |
| Calendar + Join links | ✅ EventKit | ✅ CalendarContract |
| Reminders | ✅ EventKit | — (not in the Android spec) |
| Weather | ✅ Open-Meteo | ✅ Open-Meteo |
| Sports (scores, scoring plays, play-by-play) | ✅ ESPN | ✅ ESPN |
| Quiet mode, breakthrough list, held count | ✅ | ✅ |
| Bookmarks → Chrome | ✅ | ✅ |
| Pomodoro / clipboard / file shelf | ✅ | — |
| Notification mirroring | partial, see below | ✅ NotificationListenerService |
| Incoming calls | ✅ UI, see below | ✅ via the call notification's own actions |
| Now playing | best-effort, see below | ✅ MediaSessionManager |
| Lock screen + unlock animation | n/a | ✅ |
| Shortcut / system-key feedback | ✅ CGEventTap | partial (broadcast-based) |

## Deliberate gaps, and why

**macOS now playing.** There has never been a public API for "what is any app
playing". The private `MediaRemote` framework was the universal answer and Apple
restricted it to entitled processes in macOS 15.4. `MediaService` tries it via
`dlopen`, then falls back to scripting Music and Spotify, and the Media tab says
plainly when neither works. Nothing is fabricated.

**macOS notification mirroring.** macOS does not let a third-party app read or
suppress another app's notifications. The spec's three routes are: direct APIs
(Microsoft Graph for Outlook/Teams, `chat.db` for iMessage), Accessibility
observation of Notification Center banners, and the `usernoted` database. All
three need either an OAuth app registration or Full Disk Access, so none of them
is wired up — the Inbox tab currently shows agent and system events only. The
Settings pane explains the "Deliver quietly" workaround.

**macOS incoming calls.** The call row, Accept and Decline are built and the
Accept path opens the app. Pressing the *native* Accept button via Accessibility
needs the same banner observation as above, so Accept currently hands off to the
app rather than answering in place.

**macOS screen-share detection for Quiet.** Call, Focus and full-screen detection
are implemented. Screen-share detection is not — the reliable signals need either
a `CGDisplayStream` of the user's own screen or window-title inspection, and a
false positive silences notifications, which is worse than missing the trigger.

**Android split island.** The setting exists and the priority resolver picks a
single activity correctly; the second floating bubble beside the pill is not
drawn yet.

**Android clipboard.** `ClipboardManager` only fires for the foreground app on
Android 10+, and the island is never focused. The spec's own fallback is an
accessibility service, which is a large ask for one feature, so this is off.

**Android volume keys.** `VOLUME_CHANGED_ACTION` is a hidden broadcast. Charging,
ringer and Bluetooth events work through public broadcasts; volume does not.

## Not yet verified on hardware

Neither app has run on a physical device from this workspace — there is no
Android SDK or signed-in Mac here, so both are built and linted in CI only. The
cutout maths, the fold transition and the overlay's behaviour under Samsung's
battery management are the three things most worth checking first on a real
Fold 8.
