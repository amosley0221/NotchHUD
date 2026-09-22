import AppKit
import AVFoundation
import Foundation

/// Decides whether Quiet is on. Detection is deliberately conservative: a false
/// positive silences the user's notifications, which is worse than a false negative.
@MainActor
final class QuietService {

    static let shared = QuietService()

    private var task: Task<Void, Never>?
    private var wasQuiet = false

    private static let callApps: Set<String> = [
        "com.microsoft.teams2", "com.microsoft.teams",
        "com.apple.FaceTime",
        "us.zoom.xos",
        "com.google.Chrome",           // Meet in the browser
        "com.tinyspeck.slackmacgap",
    ]

    private init() {}

    func start() {
        task?.cancel()
        task = Task { [weak self] in
            while !Task.isCancelled {
                self?.evaluate()
                try? await Task.sleep(for: .seconds(3))
            }
        }
    }

    private func evaluate() {
        let settings = Settings.shared
        var reasons: [String] = []

        if settings.quietManual { reasons.append("turned on manually") }
        if settings.quietOnCall, isOnCall() { reasons.append("you are on a call") }
        if settings.quietOnDND, isDoNotDisturb() { reasons.append("Focus is on") }
        if settings.quietOnFullscreen, isFrontmostFullScreen() { reasons.append("a full-screen app is frontmost") }

        let quiet = !reasons.isEmpty
        HUDState.shared.quietActive = quiet
        HUDState.shared.quietReason = reasons.joined(separator: ", ")

        if wasQuiet, !quiet { HUDState.shared.releaseHeld() }
        wasQuiet = quiet
    }

    /// "On a call" = a known call app is running and the microphone is in use.
    /// Camera alone is not enough (screen recording would trip it).
    private func isOnCall() -> Bool {
        guard micInUse() else { return false }
        let running = NSWorkspace.shared.runningApplications.compactMap(\.bundleIdentifier)
        return running.contains { Self.callApps.contains($0) }
    }

    private func micInUse() -> Bool {
        let session = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.microphone], mediaType: .audio, position: .unspecified
        )
        return session.devices.contains { $0.isInUseByAnotherApplication }
    }

    /// Focus/DND state is not public API; the assertions file is the stable-enough
    /// signal every utility uses. Absent or unreadable simply means "not in Focus".
    private func isDoNotDisturb() -> Bool {
        let path = ("~/Library/DoNotDisturb/DB/Assertions.json" as NSString).expandingTildeInPath
        guard let data = FileManager.default.contents(atPath: path),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let records = root["data"] as? [[String: Any]],
              let first = records.first,
              let details = first["storeAssertionRecords"] as? [[String: Any]]
        else { return false }
        return !details.isEmpty
    }

    private func isFrontmostFullScreen() -> Bool {
        guard let screen = NSScreen.main else { return false }
        // A full-screen app leaves no visible menu bar, so the visible frame equals
        // the full frame. Cheap, and it needs no extra permission.
        return screen.visibleFrame.height >= screen.frame.height
    }
}
