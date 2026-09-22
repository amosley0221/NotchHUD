import SwiftUI

/// Design tokens from docs/mac/SPEC.md. Declared final by the spec, so they live
/// in exactly one place.
enum Tokens {
    // Surfaces
    static let hud = Color.black
    static let card = Color.white.opacity(0.05)
    static let control = Color.white.opacity(0.10)
    static let keycap = Color.white.opacity(0.14)
    static let track = Color.white.opacity(0.18)
    static let hairline = Color.white.opacity(0.06)

    // Text
    static let textPrimary = Color.white
    static let textSecondary = Color(hex: 0xD8D8DE)
    static let textTertiary = Color(hex: 0x9A9AA4)
    static let textQuaternary = Color(hex: 0x8A8A94)
    static let textMuted = Color(hex: 0x7A7A84)

    // Fixed semantics
    static let quiet = Color(hex: 0x8A5CF6)
    static let quietLink = Color(hex: 0xC4B5FD)
    static let teams = Color(hex: 0x6264A7)
    static let iMessage = Color(hex: 0x34C759)
    static let liveSports = Color(hex: 0xFF6B6B)

    // Geometry
    static let notchWidth: CGFloat = 560
    static let notchHeight: CGFloat = 34
    static let notchGap: CGFloat = 150
    static let menuBarPillWidth: CGFloat = 360
    static let menuBarPillHeight: CGFloat = 24
    static let floatingPillWidth: CGFloat = 380
    static let expandedWidth: CGFloat = 680
    static let toastWidthNotch: CGFloat = 460
    static let toastWidthPill: CGFloat = 340
    static let callWidthNotch: CGFloat = 600
    static let callWidthPill: CGFloat = 440
    static let callHeight: CGFloat = 44

    // Motion
    static let pop: Double = 0.18
    static let fadeIn: Double = 0.22
    static let widthChange: Double = 0.28
    static let sweep: Double = 0.9
    static let breathe: Double = 2.2
    static let pulse: Double = 0.9
}

enum LightTheme: String, CaseIterable, Codable, Identifiable {
    case aurora, ember, mono, candy
    var id: String { rawValue }

    var label: String {
        switch self {
        case .aurora: "Aurora"
        case .ember: "Ember"
        case .mono: "Mono"
        case .candy: "Candy"
        }
    }

    var running: Color {
        switch self {
        case .aurora: Color(hex: 0x3D8BFF)
        case .ember: Color(hex: 0xFF7A3D)
        case .mono: Color(hex: 0xFFFFFF)
        case .candy: Color(hex: 0xC77DFF)
        }
    }

    var permission: Color {
        switch self {
        case .aurora: Color(hex: 0xF59A3A)
        case .ember: Color(hex: 0xFFD23D)
        case .mono: Color(hex: 0xC9C9C9)
        case .candy: Color(hex: 0xFF8FAB)
        }
    }

    var done: Color {
        switch self {
        case .aurora: Color(hex: 0x34D27A)
        case .ember: Color(hex: 0x7EE08A)
        case .mono: Color(hex: 0x8F8F8F)
        case .candy: Color(hex: 0x5CE1E6)
        }
    }

    var error: Color {
        switch self {
        case .ember: Color(hex: 0xFF4D6D)
        default: Color(hex: 0xFF5A5A)
        }
    }

    var accent: Color {
        switch self {
        case .aurora: Color(hex: 0x7FB2FF)
        case .ember: Color(hex: 0xFFB38A)
        case .mono: Color(hex: 0xFFFFFF)
        case .candy: Color(hex: 0xE0AAFF)
        }
    }

    func color(for state: AgentState) -> Color {
        switch state {
        case .running: running
        case .permission: permission
        case .done: done
        case .error: error
        }
    }
}

enum MotionStyle: String, CaseIterable, Codable, Identifiable {
    case breathe, pulse, still
    var id: String { rawValue }

    var label: String {
        switch self {
        case .breathe: "Breathe"
        case .pulse: "Pulse"
        case .still: "Static"
        }
    }

    var period: Double? {
        switch self {
        case .breathe: Tokens.breathe
        case .pulse: Tokens.pulse
        case .still: nil
        }
    }
}

/// How the HUD is drawn on a screen that has no physical notch.
enum ExternalMode: String, CaseIterable, Codable, Identifiable {
    case notchShape, menuBar, floatingPill
    var id: String { rawValue }

    var label: String {
        switch self {
        case .notchShape: "Notch shape"
        case .menuBar: "In menu bar"
        case .floatingPill: "Floating pill"
        }
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

extension Font {
    /// The spec asks for IBM Plex Sans/Mono with SF Pro / SF Mono as the sanctioned
    /// fallback. We take the fallback so the bundle ships no font binaries.
    static func ui(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight)
    }

    static func mono(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight, design: .monospaced)
    }
}
