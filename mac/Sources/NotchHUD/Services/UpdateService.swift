import AppKit
import Foundation

/// Self-update against the project's own GitHub Releases.
///
/// There is no Sparkle feed and no Developer ID signature here — the app is
/// ad-hoc signed — so this does the smallest honest thing: ask the Releases API
/// what the newest tag is, and if it is newer, download that release's zip and
/// swap the bundle. macOS has no notion of uninstalling an app, so an update is
/// just replacing the bundle; the only fiddly part is that a running app cannot
/// overwrite itself, which is what the handoff script below is for.
@MainActor
final class UpdateService: ObservableObject {

    static let shared = UpdateService()

    enum State: Equatable {
        case idle
        case checking
        case upToDate
        case available(version: String)
        case downloading
        case installing
        case failed(String)
    }

    @Published private(set) var state: State = .idle
    @Published private(set) var latestVersion: String?

    private var downloadURL: URL?
    private var timer: Timer?

    private let repository = "amosley0221/NotchHUD"

    var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
    }

    var updateAvailable: Bool {
        if case .available = state { return true }
        return false
    }

    private init() {}

    /// On launch, then every six hours. The unauthenticated API allows 60 calls an
    /// hour, so this is nowhere near any limit.
    func start() {
        Task { await check() }
        timer = Timer.scheduledTimer(withTimeInterval: 6 * 60 * 60, repeats: true) { _ in
            Task { @MainActor in await UpdateService.shared.check() }
        }
    }

    func check() async {
        state = .checking
        guard let url = URL(string: "https://api.github.com/repos/\(repository)/releases/latest") else {
            state = .failed("Bad repository URL")
            return
        }

        var request = URLRequest(url: url)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("NotchHUD/\(currentVersion)", forHTTPHeaderField: "User-Agent")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                state = .failed("GitHub returned an error")
                return
            }
            guard
                let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                let tag = root["tag_name"] as? String,
                let assets = root["assets"] as? [[String: Any]]
            else {
                state = .failed("Unexpected response")
                return
            }

            let version = tag.hasPrefix("v") ? String(tag.dropFirst()) : tag
            latestVersion = version

            let asset = assets.first { ($0["name"] as? String)?.hasSuffix("macos.zip") == true }
            downloadURL = (asset?["browser_download_url"] as? String).flatMap(URL.init(string:))

            if Self.isNewer(version, than: currentVersion), downloadURL != nil {
                state = .available(version: version)
            } else {
                state = .upToDate
            }
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    /// Downloads the new bundle, hands the swap to a detached script, and quits.
    func installAndRelaunch() async {
        guard let downloadURL else { return }
        state = .downloading

        do {
            let (temporaryFile, _) = try await URLSession.shared.download(from: downloadURL)
            state = .installing

            let workDirectory = FileManager.default.temporaryDirectory
                .appendingPathComponent("NotchHUDUpdate-\(UUID().uuidString)")
            try FileManager.default.createDirectory(at: workDirectory, withIntermediateDirectories: true)

            let zip = workDirectory.appendingPathComponent("update.zip")
            try FileManager.default.moveItem(at: temporaryFile, to: zip)

            try run("/usr/bin/ditto", ["-xk", zip.path, workDirectory.path])

            let newBundle = workDirectory.appendingPathComponent("NotchHUD.app")
            try validate(bundle: newBundle)

            // It came off the internet, so it carries the download quarantine. The
            // app is ad-hoc signed and cannot be notarised without a paid Apple
            // developer account, so Gatekeeper would refuse to launch it.
            try? run("/usr/bin/xattr", ["-dr", "com.apple.quarantine", newBundle.path])

            try handOffSwap(from: newBundle)
            NSApp.terminate(nil)
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    /// Refuses anything that is not recognisably this app, since we are about to
    /// replace the running application with it.
    private func validate(bundle: URL) throws {
        let plist = bundle.appendingPathComponent("Contents/Info.plist")
        guard
            let data = FileManager.default.contents(atPath: plist.path),
            let info = try PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any],
            info["CFBundleIdentifier"] as? String == "com.notchhud.mac"
        else {
            throw UpdateError.notOurApp
        }

        let executable = bundle.appendingPathComponent("Contents/MacOS/NotchHUD")
        guard FileManager.default.isExecutableFile(atPath: executable.path) else {
            throw UpdateError.notOurApp
        }
    }

    /// A running app cannot overwrite its own bundle, so a short script waits for
    /// this process to exit, swaps the directories and relaunches.
    private func handOffSwap(from newBundle: URL) throws {
        let installed = Bundle.main.bundleURL
        let script = newBundle.deletingLastPathComponent().appendingPathComponent("swap.sh")

        let body = """
        #!/bin/bash
        set -e
        while kill -0 \(ProcessInfo.processInfo.processIdentifier) 2>/dev/null; do sleep 0.2; done
        rm -rf "\(installed.path)"
        mv "\(newBundle.path)" "\(installed.path)"
        open "\(installed.path)"
        """

        try body.write(to: script, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: script.path)

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/bash")
        process.arguments = [script.path]
        try process.run()
    }

    private func run(_ launchPath: String, _ arguments: [String]) throws {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: launchPath)
        process.arguments = arguments
        try process.run()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else { throw UpdateError.commandFailed(launchPath) }
    }

    enum UpdateError: LocalizedError {
        case notOurApp
        case commandFailed(String)

        var errorDescription: String? {
            switch self {
            case .notOurApp: "The downloaded bundle is not Notch HUD."
            case .commandFailed(let path): "\(path) failed."
            }
        }
    }

    /// Plain semantic comparison; both sides come from our own MAJOR.MINOR.PATCH.
    static func isNewer(_ candidate: String, than current: String) -> Bool {
        func parts(_ value: String) -> [Int] {
            value.split(separator: ".").map { Int($0) ?? 0 }
        }
        let a = parts(candidate), b = parts(current)
        for index in 0..<max(a.count, b.count) {
            let left = index < a.count ? a[index] : 0
            let right = index < b.count ? b[index] : 0
            if left != right { return left > right }
        }
        return false
    }
}
