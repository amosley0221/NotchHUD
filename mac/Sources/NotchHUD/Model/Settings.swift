import Foundation
import SwiftUI

/// Everything the Settings window writes, persisted in UserDefaults.
@MainActor
final class Settings: ObservableObject {

    static let shared = Settings()

    @AppStorage("theme") var theme: LightTheme = .aurora
    @AppStorage("motion") var motion: MotionStyle = .breathe
    @AppStorage("externalMode") var externalMode: ExternalMode = .notchShape
    @AppStorage("leftSlot") var leftSlot: SlotKind = .agents
    @AppStorage("rightSlot") var rightSlot: SlotKind = .media

    @AppStorage("sportsMode") var sportsMode: SportsMode = .scoring
    @AppStorage("useCelsius") var useCelsius: Bool = false
    @AppStorage("weatherCity") var weatherCity: String = ""
    @AppStorage("weatherLat") var weatherLat: Double = .nan
    @AppStorage("weatherLon") var weatherLon: Double = .nan
    @AppStorage("companionPort") var companionPort: Int = 8788
    @AppStorage("agentPort") var agentPort: Int = 8787

    // Quiet rules
    @AppStorage("quietManual") var quietManual: Bool = false
    @AppStorage("quietOnCall") var quietOnCall: Bool = true
    @AppStorage("quietOnShare") var quietOnShare: Bool = true
    @AppStorage("quietOnDND") var quietOnDND: Bool = true
    @AppStorage("quietOnFullscreen") var quietOnFullscreen: Bool = false

    @Published var modules: [Module: Bool] {
        didSet { persist(modules.mapKeys(\.rawValue), to: "modules") }
    }

    @Published var breakthrough: [Module: Bool] {
        didSet { persist(breakthrough.mapKeys(\.rawValue), to: "breakthrough") }
    }

    @Published var bookmarks: [Bookmark] {
        didSet {
            if let data = try? JSONEncoder().encode(bookmarks) {
                UserDefaults.standard.set(data, forKey: "bookmarks")
            }
        }
    }

    @Published var leagues: Set<String> {
        didSet { UserDefaults.standard.set(Array(leagues), forKey: "leagues") }
    }

    @Published var teams: Set<String> {
        didSet { UserDefaults.standard.set(Array(teams), forKey: "teams") }
    }

    private init() {
        let defaults = UserDefaults.standard

        let storedModules = defaults.dictionary(forKey: "modules") as? [String: Bool] ?? [:]
        modules = Dictionary(uniqueKeysWithValues: Module.allCases.map { ($0, storedModules[$0.rawValue] ?? true) })

        // Defaults from the spec: agents, calls, meetings, system HUD and shortcuts
        // break through Quiet; sports, mail and chat do not.
        let defaultBreakthrough: Set<Module> = [.agents, .calls, .meetings, .systemHUD, .shortcuts]
        let storedBreak = defaults.dictionary(forKey: "breakthrough") as? [String: Bool] ?? [:]
        breakthrough = Dictionary(uniqueKeysWithValues: Module.allCases.map {
            ($0, storedBreak[$0.rawValue] ?? defaultBreakthrough.contains($0))
        })

        if let data = defaults.data(forKey: "bookmarks"),
           let decoded = try? JSONDecoder().decode([Bookmark].self, from: data) {
            bookmarks = decoded
        } else {
            bookmarks = []
        }

        leagues = Set(defaults.stringArray(forKey: "leagues") ?? [])
        teams = Set(defaults.stringArray(forKey: "teams") ?? [])
    }

    func isEnabled(_ module: Module) -> Bool { modules[module] ?? true }
    func breaksThrough(_ module: Module) -> Bool { breakthrough[module] ?? false }

    var hasLocation: Bool { !weatherLat.isNaN && !weatherLon.isNaN }

    private func persist(_ dict: [String: Bool], to key: String) {
        UserDefaults.standard.set(dict, forKey: key)
    }
}

private extension Dictionary {
    func mapKeys<T: Hashable>(_ transform: (Key) -> T) -> [T: Value] {
        Dictionary<T, Value>(uniqueKeysWithValues: map { (transform($0.key), $0.value) })
    }
}
