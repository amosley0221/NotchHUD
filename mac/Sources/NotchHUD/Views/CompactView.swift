import SwiftUI

/// Idle HUD: a user-chosen module in each wing, with the notch gap between them.
struct CompactView: View {
    let geometry: PanelGeometry

    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    var body: some View {
        HStack(spacing: 0) {
            slotView(settings.leftSlot, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            if geometry.centerGap > 0 {
                Color.clear.frame(width: geometry.centerGap)
            }

            slotView(settings.rightSlot, alignment: .trailing)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .frame(maxHeight: .infinity)
    }

    @ViewBuilder
    private func slotView(_ slot: SlotKind, alignment: HorizontalAlignment) -> some View {
        switch slot {
        case .agents:
            if let agent = state.agents.min(by: { $0.state.priority < $1.state.priority }) {
                labelled(agent.name, dot: settings.theme.color(for: agent.state), pulsing: agent.state == .running)
            } else {
                labelled("No agents", dot: Tokens.textQuaternary)
            }

        case .media:
            if let media = state.media {
                HStack(spacing: 6) {
                    if alignment == .trailing {
                        Text(media.title)
                            .font(.ui(12, .medium))
                            .foregroundStyle(Tokens.textSecondary)
                            .lineLimit(1)
                        if media.playing { Equalizer(color: settings.theme.accent) }
                    } else {
                        if media.playing { Equalizer(color: settings.theme.accent) }
                        Text(media.title)
                            .font(.ui(12, .medium))
                            .foregroundStyle(Tokens.textSecondary)
                            .lineLimit(1)
                    }
                }
            } else {
                labelled("Nothing playing", dot: Tokens.textQuaternary)
            }

        case .weather:
            if let weather = state.weather {
                labelled(
                    "\(displayTemp(weather.tempC))° · \(weather.condition)",
                    dot: weather.alert == nil ? settings.theme.accent : settings.theme.permission
                )
            } else {
                labelled("Set a location", dot: Tokens.textQuaternary)
            }

        case .meeting:
            if let meeting = state.nextMeeting {
                labelled(meeting.title, dot: settings.theme.accent)
            } else {
                labelled("Nothing scheduled", dot: Tokens.textQuaternary)
            }

        case .sports:
            if let game = state.game {
                labelled(
                    "\(game.away) \(game.awayScore) – \(game.homeScore) \(game.home) · \(game.period)",
                    dot: Tokens.liveSports,
                    pulsing: game.live
                )
            } else {
                labelled("No games today", dot: Tokens.textQuaternary)
            }

        case .focus:
            labelled(
                formatClock(state.pomodoroSecondsLeft),
                dot: state.pomodoroRunning ? settings.theme.running : Tokens.textQuaternary,
                pulsing: state.pomodoroRunning,
                mono: true
            )

        case .inbox:
            if state.quietActive, !state.heldNotifications.isEmpty {
                labelled("Quiet · \(state.heldNotifications.count) held", dot: Tokens.quiet)
            } else if state.notifications.isEmpty {
                labelled("All caught up", dot: Tokens.textQuaternary)
            } else {
                labelled("\(state.notifications.count) unread", dot: settings.theme.accent)
            }

        case .clock:
            TimelineView(.periodic(from: .now, by: 1)) { context in
                Text(context.date, format: .dateTime.hour().minute())
                    .font(.mono(12, .medium))
                    .foregroundStyle(Tokens.textSecondary)
            }
        }
    }

    private func labelled(_ text: String, dot: Color, pulsing: Bool = false, mono: Bool = false) -> some View {
        HStack(spacing: 6) {
            StatusDot(color: dot, pulsing: pulsing)
            Text(text)
                .font(mono ? .mono(12, .medium) : .ui(12, .medium))
                .foregroundStyle(Tokens.textPrimary)
                .lineLimit(1)
                .truncationMode(.tail)
        }
    }

    private func displayTemp(_ celsius: Double) -> Int {
        settings.useCelsius ? Int(celsius.rounded()) : Int((celsius * 9 / 5 + 32).rounded())
    }
}
