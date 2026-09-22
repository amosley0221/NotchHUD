import AppKit
import SwiftUI

@main
struct NotchHUDMain {
    static func main() {
        let app = NSApplication.shared
        let delegate = AppDelegate()
        app.delegate = delegate
        // Agent app: no Dock icon, no menu bar of its own. The HUD is the UI.
        app.setActivationPolicy(.accessory)
        app.run()
    }
}

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {

    private var panelController: PanelController?
    private var settingsWindow: NSWindow?
    private var statusItem: NSStatusItem?
    private var pomodoroTimer: Timer?
    private var escMonitor: Any?

    func applicationDidFinishLaunching(_ notification: Notification) {
        panelController = PanelController()

        AgentBridge.shared.start()
        CalendarService.shared.start()
        WeatherService.shared.start()
        SportsService.shared.start()
        QuietService.shared.start()
        ClipboardService.shared.start()
        MediaService.shared.start()
        ShortcutService.shared.start()

        installMenuBarItem()
        installEscMonitor()

        // No Dock icon and no window means a first launch looks like nothing
        // happened. Show Settings once so the app is findable; after that it lives
        // in the menu bar like any other agent app.
        if !UserDefaults.standard.bool(forKey: "hasLaunchedBefore") {
            UserDefaults.standard.set(true, forKey: "hasLaunchedBefore")
            openSettings()
        }

        pomodoroTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            Task { @MainActor in HUDState.shared.tickPomodoro() }
        }

        NSAppleEventManager.shared().setEventHandler(
            self,
            andSelector: #selector(handleURLEvent(_:withReply:)),
            forEventClass: AEEventClass(kInternetEventClass),
            andEventID: AEEventID(kAEGetURL)
        )
    }

    func applicationWillTerminate(_ notification: Notification) {
        ShortcutService.shared.stop()
        SportsService.shared.stop()
        pomodoroTimer?.invalidate()
        if let escMonitor { NSEvent.removeMonitor(escMonitor) }
    }

    @objc private func handleURLEvent(_ event: NSAppleEventDescriptor, withReply reply: NSAppleEventDescriptor) {
        guard let string = event.paramDescriptor(forKeyword: keyDirectObject)?.stringValue,
              let url = URL(string: string) else { return }
        AgentBridge.shared.handle(url: url)
    }

    /// A small menu-bar item is the only way to reach Settings or quit in an
    /// accessory app — without it the user would have no way out.
    private func installMenuBarItem() {
        let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        item.button?.image = NSImage(
            systemSymbolName: "rectangle.topthird.inset.filled",
            accessibilityDescription: "Notch HUD"
        )

        let menu = NSMenu()
        menu.addItem(withTitle: "Settings…", action: #selector(openSettings), keyEquivalent: ",").target = self
        menu.addItem(.separator())
        menu.addItem(withTitle: "Toggle Quiet", action: #selector(toggleQuiet), keyEquivalent: "q").target = self
        menu.addItem(withTitle: "Grant Accessibility…", action: #selector(grantAccessibility), keyEquivalent: "").target = self
        menu.addItem(.separator())
        menu.addItem(withTitle: "Quit Notch HUD", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "")

        item.menu = menu
        statusItem = item
    }

    private func installEscMonitor() {
        escMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            if event.keyCode == 53 {   // Esc
                Task { @MainActor in HUDState.shared.collapse() }
            }
            return event
        }
    }

    @objc private func toggleQuiet() {
        Settings.shared.quietManual.toggle()
    }

    @objc private func grantAccessibility() {
        ShortcutService.shared.requestAccessibilityPermission()
    }

    @objc private func openSettings() {
        if let settingsWindow {
            settingsWindow.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            return
        }

        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 620, height: 640),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "Notch HUD Settings"
        window.center()
        window.isReleasedWhenClosed = false
        window.contentView = NSHostingView(
            rootView: SettingsView()
                .environmentObject(Settings.shared)
                .environmentObject(HUDState.shared)
        )
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
        settingsWindow = window
    }
}
