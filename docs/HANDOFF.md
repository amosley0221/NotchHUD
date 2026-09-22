# Handoff: Notch HUD (macOS) + Island HUD (Android)

Two coordinated apps by one product. Start with the macOS app; the Android app is a companion that shares settings, Quiet rules, bookmarks and agent state over a companion link.

- `mac/README.md` — macOS Notch HUD spec (Swift/SwiftUI), prototype `mac/Notch HUD.dc.html`, screenshots in `mac/screenshots/`.
- `android/README.md` — Android Island HUD spec for Galaxy Z Fold 8 (Kotlin/Compose), prototype `android/Island HUD Android.dc.html`, screenshots in `android/screenshots/`.

Open either prototype in a browser to inspect states (the `support.js` beside it is the runtime; the HTML is a design reference, not code to ship).

## Cross-cutting rules
1. **Real data only.** The prototypes use fixture data (LAL–BOS, sample contacts, tracks, weather). Shipping apps show a module only when a real source has data; sports appear only when a followed team is actually pre-game/live/just-finished. No "Simulate" UI in production.
2. **Shared state.** Settings (modules, slots, themes, Quiet rules, breakthrough list, sports teams/leagues, bookmarks) and agent events sync Mac ↔ phone. Suggested transport: local WebSocket on LAN with a lightweight relay (FCM/APNs-free) for off-network; JSON messages `{ type: "agent"|"settings"|"approval", ... }`.
3. **Light language** is identical on both: blue running, orange needs-you, green done, red error, violet Quiet. Same hex values in both READMEs.
4. **Privacy.** Notification content stays on device except agent events and approvals relayed between the user's own devices.

## Suggested build order
1. Mac: window placement (notch / external notch-shape / pill), compact + toast + expanded shell, Settings.
2. Mac: agent hooks + approval flow, shortcut/system HUD, Quiet mode.
3. Mac: media, calendar, reminders, weather, sports (ESPN), inbox sources, bookmarks.
4. Android: overlay + cutout geometry for both Fold 8 screens, lock/unlock, tap/hold gestures.
5. Android: notification listener, calls, media, sports/weather/calendar, companion link to Mac.
