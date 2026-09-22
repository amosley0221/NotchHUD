# Notch HUD + Island HUD

Two coordinated apps built from [`docs/HANDOFF.md`](docs/HANDOFF.md):

| | |
|---|---|
| **`mac/`** | **Notch HUD** — a macOS agent app that turns the MacBook notch (or a pill on an external display) into a status surface. Swift + SwiftUI, no Xcode project. |
| **`android/`** | **Island HUD** — a Dynamic-Island-style overlay wrapped around the camera cutout, built for the Galaxy Z Fold 8's two screens. Kotlin + Jetpack Compose. |

They share one light language — blue running, orange needs-you, green done, red error, violet Quiet — and the Mac pushes agent state to the phone over a LAN WebSocket.

## Get the app

Every build is published on the [Releases page](https://github.com/amosley0221/NotchHUD/releases) with notes describing what changed.

- **Android:** download `IslandHUD-<version>.apk` and open it on the phone.
- **macOS:** download `NotchHUD-<version>-macos.zip`, unzip, and move `NotchHUD.app` to `/Applications`. It is ad-hoc signed rather than notarised (that needs a paid Apple Developer account), so Gatekeeper blocks it until you clear the download quarantine:

```bash
xattr -dr com.apple.quarantine /Applications/NotchHUD.app
```

The app has no Dock icon — it lives in the menu bar, and Settings opens by itself on the first launch. To reach Settings at any time, click the menu bar icon, or run:

```bash
open notchhud://settings
```

The `notchhud` CLI ships inside the bundle. To put it on your PATH:

```bash
ln -sf /Applications/NotchHUD.app/Contents/Resources/notchhud /usr/local/bin/notchhud
```

### Updating the Android app never requires an uninstall

This is enforced, not just intended. Four things have to hold, and CI checks each one:

1. **`applicationId` never changes** — it is pinned to `com.notchhud.island` in [`android/app/build.gradle.kts`](android/app/build.gradle.kts). A different id would install side by side instead of updating.
2. **Every release is signed with the same certificate**, held in the `ANDROID_KEYSTORE_BASE64` repository secret. The release workflow refuses to publish if that secret is missing, so a debug-signed APK can never reach the Releases page. Each release's notes print the certificate's SHA-256 so you can confirm it has not moved.
3. **`versionCode` only ever increases** — it is derived from `versionName` (`major × 10000 + minor × 100 + patch`) in [`android/version.properties`](android/version.properties), and the workflow fails the build if the new code is not greater than the last published one.
4. **No `sharedUserId`**, which would otherwise pin the app to an immovable Linux uid.

The one case that still needs an uninstall: if the copy on your phone is a **debug** build (`com.notchhud.island.debug`, or an older APK you built locally), it was signed with a machine-local debug key that no release can match. Remove that one, install a release APK, and every update after it is in place.

## Cutting a release

```bash
# 1. bump the version
vim android/version.properties        # versionName=1.0.1

# 2. record what changed
vim CHANGELOG.md                      # add a "## 1.0.1" section

# 3. commit and tag
git commit -am "Release 1.0.1"
git tag v1.0.1 && git push origin main --tags
```

The `Release` workflow then builds and signs the APK, verifies the signature, builds the Mac app, and publishes both to a GitHub Release whose notes combine your CHANGELOG section with the commit list since the previous tag.

## Building locally

**Android** needs a JDK 17 and an Android SDK:

```bash
cd android && ./gradlew assembleDebug
```

**macOS** needs Xcode 15 or newer:

```bash
cd mac && swift build -c release && ./Scripts/make_app.sh
```

## First run

**Android** — open the app and grant, in order: *draw over other apps* (this is the island), *notification access* (mirroring and now-playing), calendar/location, and a battery-optimisation exemption. On Samsung, also turn off **Put unused apps to sleep** in Device care, or the island stops after a few hours.

**macOS** — the app has no Dock icon; it lives in the menu bar. Grant Accessibility if you want shortcut feedback, and Calendar/Reminders/Location when prompted.

## Linking the two

1. On the Mac, open **Settings → Agents** and copy one of the `ws://…` addresses.
2. On the phone, paste it into **Settings → Companion link** and tap Connect.

Agent events flow Mac → phone; Approve/Deny flows back.

## Feeding it agent state

The Mac app listens on `127.0.0.1:8787` for line-delimited JSON and registers the `notchhud://` URL scheme. The bundled CLI wraps both:

```bash
notchhud running    build "Codex" "Refactoring auth"
notchhud permission build "Run the migration?" "npm run migrate"
notchhud done       build
```

**Settings → Agents** has a ready-made Claude Code hook block to paste into `~/.claude/settings.json`.

## What is real and what is not

The prototypes in `docs/` are full of fixture data (LAL–BOS, "Priya Natarajan", "Nightcall"). None of it ships. Every module renders only when its source actually has data, and shows a plain empty state otherwise — "No games today for your teams", "Nothing scheduled", "Set a location", "No agents connected". There is no Simulate UI.

See [`docs/STATUS.md`](docs/STATUS.md) for exactly which data sources are wired up and which are not.

## Layout

```
android/          Island HUD — Kotlin, Compose, Gradle
  app/src/main/java/com/notchhud/island/
    core/         tokens, models, shared state, settings
    data/         ESPN, Open-Meteo, CalendarContract
    service/      overlay window, notification listener, media, companion link
    ui/           the island itself, plus the setup screens
mac/              Notch HUD — Swift, SwiftUI, SwiftPM
  Sources/NotchHUD/
    Model/        tokens, models, shared state, settings
    Panels/       NSPanel placement per screen
    Services/     agent bridge, EventKit, weather, sports, quiet, media
    Views/        compact, toast, call, expanded + tabs, settings
  Sources/notchhud/   the CLI
docs/             the original handoff spec, prototypes and screenshots
```
