import AppKit

/// Per-screen overrides.
///
/// A width that reads well on a widescreen monitor is far too much of a 13"
/// Sidecar iPad, so mode and width are settable per display rather than once for
/// every screen at the same time.
struct DisplayConfig: Codable, Equatable {
    var mode: ExternalMode
    /// Multiplier on the HUD's width. Height stays put: the notch silhouette
    /// depends on it, and it is the length that crowds a small screen.
    var widthScale: Double

    static let `default` = DisplayConfig(mode: .notchShape, widthScale: 1.0)

    /// Width to use for a screen the user has not set up by hand.
    ///
    /// The menu bar has app menus on the left and status items on the right with a
    /// gap in the middle, and the HUD is supposed to live in that gap. 560 pt is
    /// 16 % of a 3440 pt ultrawide and lands there cleanly; the same 560 pt is 42 %
    /// of a 1314 pt Sidecar iPad and swallows menus at both ends. Sizing to a fifth
    /// of the screen keeps it in the gap at either extreme.
    ///
    /// Never scales *up*: 560 pt is the designed width, not a target to grow into.
    static func suggestedScale(for screen: NSScreen) -> Double {
        // A built-in display is fused to its physical notch; its width is not ours
        // to second-guess.
        guard screen.safeAreaInsets.top == 0 else { return 1.0 }
        let target = screen.frame.width * 0.20
        return min(1.0, max(0.4, target / Tokens.notchWidth))
    }
}

extension NSScreen {
    var displayID: CGDirectDisplayID? {
        deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")] as? CGDirectDisplayID
    }

    /// Stable across unplugging and rearranging, unlike the index or the frame.
    /// Falls back to the name if a UUID is unavailable.
    var persistentID: String {
        guard
            let id = displayID,
            let uuid = CGDisplayCreateUUIDFromDisplayID(id)?.takeRetainedValue()
        else { return localizedName }
        return CFUUIDCreateString(nil, uuid) as String
    }
}
