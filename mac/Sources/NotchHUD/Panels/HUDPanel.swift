import AppKit
import SwiftUI

/// A borderless, non-activating panel. It must never take focus — clicking the
/// HUD should not pull the user out of whatever app they are in.
final class HUDPanel: NSPanel {

    init(contentRect: NSRect) {
        super.init(
            contentRect: contentRect,
            styleMask: [.borderless, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        isFloatingPanel = true
        level = .statusBar
        isOpaque = false
        backgroundColor = .clear
        hasShadow = false                 // the SwiftUI shape draws its own shadow
        ignoresMouseEvents = false
        hidesOnDeactivate = false
        isMovable = false
        collectionBehavior = [.canJoinAllSpaces, .stationary, .fullScreenAuxiliary, .ignoresCycle]
        animationBehavior = .none
    }

    override var canBecomeKey: Bool { false }
    override var canBecomeMain: Bool { false }
    override var acceptsFirstResponder: Bool { false }
}

/// Geometry for one screen, derived from whether it has a physical notch and,
/// if not, which external mode the user picked.
struct PanelGeometry {
    var hasNotch: Bool
    var mode: ExternalMode
    var screen: NSScreen

    /// The notch is only in the middle of the built-in display; on external screens
    /// in notch-shape mode we draw the same silhouette anyway so the two match.
    var drawsNotchShape: Bool { hasNotch || mode == .notchShape }

    var compactSize: CGSize {
        if drawsNotchShape { return CGSize(width: Tokens.notchWidth, height: Tokens.notchHeight) }
        switch mode {
        case .menuBar: return CGSize(width: Tokens.menuBarPillWidth, height: Tokens.menuBarPillHeight)
        case .floatingPill: return CGSize(width: Tokens.floatingPillWidth, height: Tokens.notchHeight)
        case .notchShape: return CGSize(width: Tokens.notchWidth, height: Tokens.notchHeight)
        }
    }

    var cornerRadii: (top: CGFloat, bottom: CGFloat) {
        if drawsNotchShape { return (0, 20) }
        switch mode {
        case .menuBar: return (12, 12)
        case .floatingPill: return (18, 18)
        case .notchShape: return (0, 20)
        }
    }

    /// Gap left empty in the middle so the shape reads as a notch.
    var centerGap: CGFloat { drawsNotchShape ? Tokens.notchGap : 0 }

    /// Distance from the top of the screen to the top of the HUD.
    var topInset: CGFloat {
        if drawsNotchShape { return 0 }
        switch mode {
        case .menuBar: return 1
        case .floatingPill: return 26
        case .notchShape: return 0
        }
    }

    var windowLevel: NSWindow.Level {
        mode == .menuBar && !hasNotch
            ? NSWindow.Level(Int(CGWindowLevelForKey(.statusWindow)) + 1)
            : .statusBar
    }

    var toastWidth: CGFloat { drawsNotchShape ? Tokens.toastWidthNotch : Tokens.toastWidthPill }
    var callWidth: CGFloat { drawsNotchShape ? Tokens.callWidthNotch : Tokens.callWidthPill }

    /// Places a frame of `size` at the top-centre of this screen, in AppKit's
    /// bottom-left origin coordinates.
    func frame(for size: CGSize) -> NSRect {
        let visible = screen.frame
        let x = visible.midX - size.width / 2
        let y = visible.maxY - topInset - size.height
        return NSRect(x: x, y: y, width: size.width, height: size.height)
    }
}
