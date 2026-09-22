import AppKit
import SwiftUI

/// Incoming call. Persists until Accept, Decline or the call ends.
struct CallView: View {
    let call: IncomingCall
    let geometry: PanelGeometry

    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    var body: some View {
        HStack(spacing: 12) {
            avatar

            VStack(alignment: .leading, spacing: 1) {
                Text(call.name)
                    .font(.ui(12.5, .medium))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(call.source)
                    .font(.ui(10.5))
                    .foregroundStyle(Tokens.textTertiary)
                    .lineLimit(1)
            }

            Spacer(minLength: geometry.centerGap)

            Button("Decline") { decline() }
                .buttonStyle(CallButtonStyle(background: Color(hex: 0xFF5A5A), foreground: .white))

            Button("Accept") { accept() }
                .buttonStyle(CallButtonStyle(background: Color(hex: 0x34D27A), foreground: Color(hex: 0x04200F)))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var avatar: some View {
        ZStack {
            Circle()
                .fill(settings.theme.accent.opacity(0.25))
                .frame(width: 30, height: 30)
                .shadow(color: settings.theme.accent.opacity(0.5), radius: 6)
            Text(initials)
                .font(.ui(11, .semibold))
                .foregroundStyle(.white)
        }
    }

    private var initials: String {
        call.name.split(separator: " ").prefix(2).compactMap { $0.first }.map(String.init).joined().uppercased()
    }

    private func accept() {
        state.show(
            Toast(keys: ["Call"], label: "On call with \(call.name)", meter: nil,
                  state: "Live", stateColor: settings.theme.done, duration: 1.6),
            module: .calls
        )
        if let url = call.joinURL { NSWorkspace.shared.open(url) }
        state.dismissCall()
    }

    private func decline() {
        state.dismissCall()
    }
}

private struct CallButtonStyle: ButtonStyle {
    let background: Color
    let foreground: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.ui(11.5, .semibold))
            .foregroundStyle(foreground)
            .padding(.horizontal, 14)
            .frame(height: 30)
            .background(background, in: Capsule())
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}
