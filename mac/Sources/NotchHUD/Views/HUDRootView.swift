import SwiftUI

/// The HUD shape and whichever content the current mode calls for.
struct HUDRootView: View {
    let geometry: PanelGeometry

    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    @State private var sweepPhase: CGFloat = -1.2

    var body: some View {
        ZStack(alignment: .top) {
            shape
                .fill(Tokens.hud)
                .overlay(shape.stroke(Tokens.hairline, lineWidth: 1))
                .shadow(color: .black.opacity(0.35), radius: 15, y: 4)
                .shadow(color: state.glowColor.opacity(0.2), radius: 14)

            sweepOverlay

            content
                .padding(.horizontal, geometry.drawsNotchShape ? 14 : 12)

            VStack {
                Spacer()
                GlowRail(color: state.glowColor, motion: settings.motion)
                    .frame(height: 2)
            }
        }
        .clipShape(shape)
        .contentShape(shape)
        .onTapGesture { state.toggleExpanded() }
        .onChange(of: state.sweepToken) { _, token in
            guard token != nil else { return }
            sweepPhase = -1.2
            withAnimation(.easeOut(duration: Tokens.sweep)) { sweepPhase = 1.2 }
        }
    }

    private var shape: UnevenRoundedRectangle {
        let radii = geometry.cornerRadii
        return UnevenRoundedRectangle(
            topLeadingRadius: radii.top,
            bottomLeadingRadius: radii.bottom,
            bottomTrailingRadius: radii.bottom,
            topTrailingRadius: radii.top
        )
    }

    /// The agent-state sweep: a soft band crossing the whole HUD once.
    private var sweepOverlay: some View {
        GeometryReader { proxy in
            LinearGradient(
                colors: [.clear, state.glowColor.opacity(0.27), .clear],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: proxy.size.width)
            .offset(x: sweepPhase * proxy.size.width)
            .allowsHitTesting(false)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state.mode {
        case .compact:
            CompactView(geometry: geometry)
        case .toast(let toast):
            ToastView(toast: toast, geometry: geometry)
                .transition(.scale(scale: 0.92).combined(with: .opacity))
        case .call(let call):
            CallView(call: call, geometry: geometry)
        case .expanded:
            ExpandedView(geometry: geometry)
        }
    }
}

/// 2 pt rail along the bottom edge, animated per the Motion setting.
struct GlowRail: View {
    let color: Color
    let motion: MotionStyle

    @State private var bright = false

    var body: some View {
        LinearGradient(colors: [.clear, color, .clear], startPoint: .leading, endPoint: .trailing)
            .opacity(motion.period == nil ? 0.85 : (bright ? 1.0 : 0.55))
            .onAppear {
                guard let period = motion.period else { return }
                withAnimation(.easeInOut(duration: period).repeatForever(autoreverses: true)) {
                    bright = true
                }
            }
    }
}

/// 8 pt dot with a matching glow; pulses while something is actually running.
struct StatusDot: View {
    let color: Color
    var pulsing: Bool = false
    var size: CGFloat = 8

    @State private var dim = false

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: size, height: size)
            .shadow(color: color, radius: 5)
            .opacity(pulsing && dim ? 0.55 : 1)
            .onAppear {
                guard pulsing else { return }
                withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { dim = true }
            }
    }
}

/// Three bars, 5 → 14 pt, 1 s, staggered — shown when media is playing.
struct Equalizer: View {
    let color: Color
    @State private var tall = false

    var body: some View {
        HStack(alignment: .center, spacing: 2) {
            ForEach(0..<3, id: \.self) { index in
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(color)
                    .frame(width: 3, height: tall ? 14 : 5)
                    .animation(
                        .easeInOut(duration: 1).repeatForever(autoreverses: true)
                            .delay(Double(index) * 0.2),
                        value: tall
                    )
            }
        }
        .frame(height: 14)
        .onAppear { tall = true }
    }
}

struct Meter: View {
    let value: Double
    var color: Color
    var width: CGFloat = 110

    var body: some View {
        ZStack(alignment: .leading) {
            Capsule().fill(Tokens.track).frame(width: width, height: 4)
            Capsule().fill(color).frame(width: width * min(max(value, 0), 1), height: 4)
                .animation(.easeOut(duration: 0.2), value: value)
        }
    }
}

struct KeyCap: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.mono(12))
            .kerning(0.5)
            .foregroundStyle(.white)
            .padding(.vertical, 3)
            .padding(.horizontal, 8)
            .background(Tokens.keycap, in: RoundedRectangle(cornerRadius: 6))
    }
}

struct SectionLabel: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.ui(10.5, .medium))
            .kerning(0.8)
            .foregroundStyle(Tokens.textQuaternary)
    }
}

struct CardBackground: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.vertical, 10)
            .padding(.horizontal, 12)
            .background(Tokens.card, in: RoundedRectangle(cornerRadius: 10))
    }
}

extension View {
    func hudCard() -> some View { modifier(CardBackground()) }
}
