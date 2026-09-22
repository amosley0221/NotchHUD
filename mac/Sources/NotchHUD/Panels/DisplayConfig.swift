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
