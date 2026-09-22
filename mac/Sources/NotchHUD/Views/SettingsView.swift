import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var settings: Settings
    @EnvironmentObject private var state: HUDState

    var body: some View {
        TabView {
            GeneralPane().tabItem { Label("General", systemImage: "gearshape") }
            DisplaysPane().tabItem { Label("Displays", systemImage: "display.2") }
            ModulesPane().tabItem { Label("Modules", systemImage: "square.grid.2x2") }
            QuietPane().tabItem { Label("Quiet", systemImage: "moon") }
            SportsPane().tabItem { Label("Sports", systemImage: "sportscourt") }
            BookmarksPane().tabItem { Label("Bookmarks", systemImage: "bookmark") }
            AgentsPane().tabItem { Label("Agents", systemImage: "sparkles") }
        }
        .frame(minWidth: 600, minHeight: 600)
    }
}

private struct GeneralPane: View {
    @EnvironmentObject private var settings: Settings

    var body: some View {
        Form {
            Picker("Light theme", selection: $settings.theme) {
                ForEach(LightTheme.allCases) { Text($0.label).tag($0) }
            }
            Picker("Motion", selection: $settings.motion) {
                ForEach(MotionStyle.allCases) { Text($0.label).tag($0) }
            }
            Picker("External monitor", selection: $settings.externalMode) {
                ForEach(ExternalMode.allCases) { Text($0.label).tag($0) }
            }

            Section("Compact slots") {
                Picker("Left slot", selection: $settings.leftSlot) {
                    ForEach(SlotKind.allCases) { Text($0.label).tag($0) }
                }
                Picker("Right slot", selection: $settings.rightSlot) {
                    ForEach(SlotKind.allCases) { Text($0.label).tag($0) }
                }
            }

            Section("Weather") {
                WeatherLocationField()
                Toggle("Use Celsius", isOn: $settings.useCelsius)
            }

            Section("Updates") {
                UpdateRow()
            }

            Section("Permissions") {
                HStack {
                    Text(ShortcutService.shared.hasAccessibilityPermission
                         ? "Accessibility granted"
                         : "Accessibility needed for shortcut feedback")
                    Spacer()
                    Button("Open…") { ShortcutService.shared.requestAccessibilityPermission() }
                }
                Text("Other apps' notifications cannot be intercepted by any third-party app on macOS. The HUD mirrors what it can reach directly; set those apps to \"Deliver quietly\" in System Settings → Notifications so you do not see each alert twice.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
    }
}

private struct UpdateRow: View {
    @ObservedObject private var updates = UpdateService.shared

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Version \(updates.currentVersion)")
                Text(statusText).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            switch updates.state {
            case .available:
                Button("Install and Relaunch") {
                    Task { await updates.installAndRelaunch() }
                }
                .buttonStyle(.borderedProminent)
            case .downloading, .installing, .checking:
                ProgressView().controlSize(.small)
            default:
                Button("Check Now") { Task { await updates.check() } }
            }
        }
    }

    private var statusText: String {
        switch updates.state {
        case .idle: "Updates are checked on launch and every six hours."
        case .checking: "Checking…"
        case .upToDate: "Up to date."
        case .available(let version): "Version \(version) is available."
        case .downloading: "Downloading…"
        case .installing: "Installing…"
        case .failed(let message): "Check failed — \(message)"
        }
    }
}

private struct WeatherLocationField: View {
    @EnvironmentObject private var settings: Settings
    @State private var query = ""

    var body: some View {
        HStack {
            TextField("City", text: $query)
            Button("Set") {
                Task {
                    if let result = await WeatherService.shared.geocode(query) {
                        settings.weatherLat = result.lat
                        settings.weatherLon = result.lon
                        settings.weatherCity = result.name
                        await WeatherService.shared.refresh()
                    }
                }
            }
            Button("Use my location") { WeatherService.shared.requestLocationIfNeeded() }
        }
        if !settings.weatherCity.isEmpty {
            Text("Currently: \(settings.weatherCity)").font(.caption).foregroundStyle(.secondary)
        }
    }
}

/// One row per connected screen. A width that suits a widescreen monitor is far
/// too much of a 13" Sidecar iPad, so each display gets its own mode and length.
private struct DisplaysPane: View {
    @EnvironmentObject private var settings: Settings
    @State private var screens: [NSScreen] = NSScreen.screens

    var body: some View {
        Form {
            ForEach(screens, id: \.persistentID) { screen in
                Section(header: Text(label(for: screen))) {
                    let config = settings.config(for: screen)

                    if screen.safeAreaInsets.top > 0 {
                        Text("Built-in display — the HUD is fused to the notch.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else {
                        Picker(
                            "Shape",
                            selection: Binding(
                                get: { config.mode },
                                set: { settings.setConfig(DisplayConfig(mode: $0, widthScale: config.widthScale), for: screen) }
                            )
                        ) {
                            ForEach(ExternalMode.allCases) { Text($0.label).tag($0) }
                        }
                    }

                    HStack {
                        Slider(
                            value: Binding(
                                get: { config.widthScale },
                                set: { settings.setConfig(DisplayConfig(mode: config.mode, widthScale: $0), for: screen) }
                            ),
                            in: 0.4...1.2,
                            step: 0.05
                        ) {
                            Text("Length")
                        }
                        Text("\(Int(config.widthScale * 100))%")
                            .font(.system(.body, design: .monospaced))
                            .frame(width: 52, alignment: .trailing)
                    }

                    Text(sizeSummary(for: screen, config: config))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                Button("Refresh display list") { screens = NSScreen.screens }
                Text("Settings are remembered per display, so unplugging and reconnecting keeps them.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .onReceive(
            NotificationCenter.default.publisher(for: NSApplication.didChangeScreenParametersNotification)
        ) { _ in
            screens = NSScreen.screens
        }
    }

    private func label(for screen: NSScreen) -> String {
        let size = screen.frame.size
        return "\(screen.localizedName) · \(Int(size.width)) × \(Int(size.height))"
    }

    private func sizeSummary(for screen: NSScreen, config: DisplayConfig) -> String {
        let geometry = PanelGeometry(
            hasNotch: screen.safeAreaInsets.top > 0,
            mode: config.mode,
            screen: screen,
            widthScale: CGFloat(config.widthScale)
        )
        let width = Int(geometry.compactSize.width)
        let percent = Int(geometry.compactSize.width / screen.frame.width * 100)
        return "HUD is \(width) pt wide — about \(percent)% of this screen."
    }
}

private struct ModulesPane: View {
    @EnvironmentObject private var settings: Settings

    var body: some View {
        Form {
            ForEach(Module.allCases) { module in
                Toggle(module.label, isOn: Binding(
                    get: { settings.modules[module] ?? true },
                    set: { settings.modules[module] = $0 }
                ))
            }
        }
        .formStyle(.grouped)
    }
}

private struct QuietPane: View {
    @EnvironmentObject private var settings: Settings
    @EnvironmentObject private var state: HUDState

    var body: some View {
        Form {
            Section("Auto-silence when") {
                Toggle("On a Teams / FaceTime / Zoom call", isOn: $settings.quietOnCall)
                Toggle("While sharing my screen", isOn: $settings.quietOnShare)
                Toggle("macOS Focus is on", isOn: $settings.quietOnDND)
                Toggle("In full-screen apps", isOn: $settings.quietOnFullscreen)
            }

            Section("Still allowed during Quiet") {
                ForEach(Module.allCases) { module in
                    Toggle(module.label, isOn: Binding(
                        get: { settings.breakthrough[module] ?? false },
                        set: { settings.breakthrough[module] = $0 }
                    ))
                }
            }

            Section {
                Toggle("Quiet on right now", isOn: $settings.quietManual)
                if state.quietActive {
                    Text("Quiet is on — \(state.quietReason).").font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .formStyle(.grouped)
    }
}

private struct SportsPane: View {
    @EnvironmentObject private var settings: Settings
    @State private var teams: [(id: String, name: String)] = []
    @State private var query = ""
    @State private var loading = false

    var body: some View {
        Form {
            Picker("Update mode", selection: $settings.sportsMode) {
                ForEach(SportsMode.allCases) { Text($0.label).tag($0) }
            }

            Section("Leagues") {
                ForEach(SportsService.leagues.keys.sorted(), id: \.self) { code in
                    Toggle(code, isOn: Binding(
                        get: { settings.leagues.contains(code) },
                        set: { on in
                            if on { settings.leagues.insert(code) } else { settings.leagues.remove(code) }
                        }
                    ))
                }
            }

            Section("Teams") {
                HStack {
                    TextField("Search", text: $query)
                    Button(loading ? "Loading…" : "Load teams") {
                        loading = true
                        Task {
                            var all: [(id: String, name: String)] = []
                            for code in settings.leagues {
                                all.append(contentsOf: await SportsService.shared.teams(in: code))
                            }
                            teams = all.sorted { $0.name < $1.name }
                            loading = false
                        }
                    }
                    .disabled(settings.leagues.isEmpty || loading)
                }

                ForEach(filtered, id: \.id) { team in
                    Toggle(team.name, isOn: Binding(
                        get: { settings.teams.contains(team.id) },
                        set: { on in
                            if on { settings.teams.insert(team.id) } else { settings.teams.remove(team.id) }
                        }
                    ))
                }
            }
        }
        .formStyle(.grouped)
    }

    private var filtered: [(id: String, name: String)] {
        let matches = query.isEmpty ? teams : teams.filter { $0.name.localizedCaseInsensitiveContains(query) }
        return Array(matches.prefix(40))
    }
}

private struct BookmarksPane: View {
    @EnvironmentObject private var settings: Settings
    @State private var label = ""
    @State private var urlString = ""

    private let palette: [UInt32] = [0x3D8BFF, 0xF59A3A, 0x34D27A, 0xC77DFF, 0xFF6B6B]

    var body: some View {
        Form {
            Section("Bookmarks — opens in Chrome") {
                ForEach(settings.bookmarks) { bookmark in
                    HStack {
                        Circle().fill(bookmark.color).frame(width: 12, height: 12)
                        Text(bookmark.label)
                        Text(bookmark.host).foregroundStyle(.secondary)
                        Spacer()
                        Button("Remove") {
                            settings.bookmarks.removeAll { $0.id == bookmark.id }
                        }
                    }
                }
                Text("\(5 - settings.bookmarks.count) slots left").font(.caption).foregroundStyle(.secondary)
            }

            if settings.bookmarks.count < 5 {
                Section("Add") {
                    TextField("Name", text: $label)
                    TextField("https://…", text: $urlString)
                    Button("Add bookmark") {
                        let normalized = urlString.hasPrefix("http") ? urlString : "https://\(urlString)"
                        guard !label.isEmpty, let url = URL(string: normalized) else { return }
                        settings.bookmarks.append(
                            Bookmark(
                                id: UUID().uuidString,
                                label: label,
                                url: url,
                                colorHex: palette[settings.bookmarks.count % palette.count]
                            )
                        )
                        label = ""
                        urlString = ""
                    }
                }
            }
        }
        .formStyle(.grouped)
    }
}

private struct AgentsPane: View {
    @EnvironmentObject private var settings: Settings

    var body: some View {
        Form {
            Section("Companion link — paste one of these into the Android app") {
                let urls = AgentBridge.shared.companionURLs()
                if urls.isEmpty {
                    Text("No LAN address found.").foregroundStyle(.secondary)
                } else {
                    ForEach(urls, id: \.self) { url in
                        HStack {
                            Text(url).font(.system(.body, design: .monospaced)).textSelection(.enabled)
                            Spacer()
                            Button("Copy") {
                                NSPasteboard.general.clearContents()
                                NSPasteboard.general.setString(url, forType: .string)
                            }
                        }
                    }
                }
            }

            Section("Claude Code hook") {
                Text("Add this to ~/.claude/settings.json so agent state reaches the HUD:")
                    .font(.caption).foregroundStyle(.secondary)
                Text(hookSnippet)
                    .font(.system(.caption, design: .monospaced))
                    .textSelection(.enabled)
                Button("Copy hook") {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(hookSnippet, forType: .string)
                }
            }

            Section("CLI") {
                Text("notchhud emit '{\"id\":\"build\",\"name\":\"Codex\",\"state\":\"running\",\"task\":\"Refactoring\"}'")
                    .font(.system(.caption, design: .monospaced))
                    .textSelection(.enabled)
            }
        }
        .formStyle(.grouped)
    }

    private var hookSnippet: String {
        """
        {
          "hooks": {
            "Notification": [{ "hooks": [{ "type": "command",
              "command": "notchhud emit \\"{\\\\\\"id\\\\\\":\\\\\\"claude\\\\\\",\\\\\\"name\\\\\\":\\\\\\"Claude Code\\\\\\",\\\\\\"state\\\\\\":\\\\\\"permission\\\\\\"}\\"" }] }],
            "Stop": [{ "hooks": [{ "type": "command",
              "command": "notchhud emit \\"{\\\\\\"id\\\\\\":\\\\\\"claude\\\\\\",\\\\\\"state\\\\\\":\\\\\\"done\\\\\\"}\\"" }] }]
          }
        }
        """
    }
}
