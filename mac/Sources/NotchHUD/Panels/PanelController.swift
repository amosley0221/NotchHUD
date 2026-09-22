import AppKit
import Combine
import SwiftUI

/// One panel per screen, rebuilt whenever the screen arrangement changes.
@MainActor
final class PanelController: NSObject {

    private var panels: [PanelEntry] = []
    private var cancellables = Set<AnyCancellable>()
    private var outsideMonitor: Any?

    private struct PanelEntry {
        let panel: HUDPanel
        let geometry: PanelGeometry
    }

    override init() {
        super.init()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(screensChanged),
            name: NSApplication.didChangeScreenParametersNotification,
            object: nil
        )

        // Re-place every panel as the HUD changes size.
        HUDState.shared.$mode
            .receive(on: RunLoop.main)
            .sink { [weak self] mode in self?.resize(for: mode) }
            .store(in: &cancellables)

        Settings.shared.objectWillChange
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                DispatchQueue.main.async { self?.rebuild() }
            }
            .store(in: &cancellables)

        rebuild()
        installOutsideClickMonitor()
    }

    deinit {
        if let outsideMonitor { NSEvent.removeMonitor(outsideMonitor) }
    }

    @objc private func screensChanged() { rebuild() }

    func rebuild() {
        panels.forEach { $0.panel.orderOut(nil) }
        panels.removeAll()

        for screen in NSScreen.screens {
            // A physical notch shows up as a non-zero top safe-area inset.
            let hasNotch = screen.safeAreaInsets.top > 0
            let geometry = PanelGeometry(
                hasNotch: hasNotch,
                mode: Settings.shared.externalMode,
                screen: screen
            )

            let frame = geometry.frame(for: geometry.compactSize)
            let panel = HUDPanel(contentRect: frame)
            panel.level = geometry.windowLevel

            let root = HUDRootView(geometry: geometry)
                .environmentObject(HUDState.shared)
                .environmentObject(Settings.shared)

            let hosting = NSHostingView(rootView: root)
            hosting.frame = NSRect(origin: .zero, size: frame.size)
            panel.contentView = hosting
            panel.setFrame(frame, display: true)
            panel.orderFrontRegardless()

            panels.append(PanelEntry(panel: panel, geometry: geometry))
        }

        resize(for: HUDState.shared.mode)
    }

    private func resize(for mode: HUDMode) {
        for entry in panels {
            let size = targetSize(mode: mode, geometry: entry.geometry)
            let frame = entry.geometry.frame(for: size)
            NSAnimationContext.runAnimationGroup { context in
                context.duration = Tokens.widthChange
                context.timingFunction = CAMediaTimingFunction(controlPoints: 0.2, 0.8, 0.2, 1)
                entry.panel.animator().setFrame(frame, display: true)
            }
            entry.panel.contentView?.frame = NSRect(origin: .zero, size: size)
        }
    }

    private func targetSize(mode: HUDMode, geometry: PanelGeometry) -> CGSize {
        switch mode {
        case .compact:
            return geometry.compactSize
        case .toast:
            return CGSize(width: geometry.toastWidth, height: geometry.compactSize.height)
        case .call:
            return CGSize(width: geometry.callWidth, height: Tokens.callHeight)
        case .expanded:
            // Height is generous and the content is scrollable; a fixed panel keeps
            // the window server from thrashing on every tab change.
            return CGSize(width: Tokens.expandedWidth, height: 460)
        }
    }

    /// Collapse the expanded panel when the user clicks anywhere else.
    private func installOutsideClickMonitor() {
        outsideMonitor = NSEvent.addGlobalMonitorForEvents(matching: [.leftMouseDown, .rightMouseDown]) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                _ = self
                HUDState.shared.collapse()
            }
        }
    }
}
