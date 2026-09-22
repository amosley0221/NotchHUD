import AppKit
import Foundation

/// Clipboard shelf. NSPasteboard has no change notification, so a 0.5 s poll of
/// changeCount is the documented approach.
@MainActor
final class ClipboardService {

    static let shared = ClipboardService()

    private var lastChangeCount = NSPasteboard.general.changeCount
    private var task: Task<Void, Never>?

    private init() {}

    func start() {
        task?.cancel()
        task = Task { [weak self] in
            while !Task.isCancelled {
                self?.poll()
                try? await Task.sleep(for: .milliseconds(500))
            }
        }
    }

    private func poll() {
        let pasteboard = NSPasteboard.general
        guard pasteboard.changeCount != lastChangeCount else { return }
        lastChangeCount = pasteboard.changeCount

        guard Settings.shared.isEnabled(.clipboard),
              let text = pasteboard.string(forType: .string) else { return }
        HUDState.shared.clipboard = text
    }
}

enum BrowserService {
    /// Bookmarks open in Chrome when it is installed, otherwise the default browser.
    static func open(_ url: URL) {
        let chrome = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.google.Chrome")
        if let chrome {
            NSWorkspace.shared.open([url], withApplicationAt: chrome, configuration: NSWorkspace.OpenConfiguration())
        } else {
            NSWorkspace.shared.open(url)
        }
    }
}
