import SwiftUI

struct OverviewTab: View {
    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    private let columns = [GridItem(.adaptive(minimum: 170), spacing: 10)]

    var body: some View {
        if state.quietActive, !state.heldNotifications.isEmpty {
            HStack {
                Text("Quiet · \(state.heldNotifications.count) notifications held")
                    .font(.ui(12))
                    .foregroundStyle(Tokens.quietLink)
                Spacer()
                Button("Review") { state.tab = .inbox }
                    .buttonStyle(SmallButtonStyle(filled: false))
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 12)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Tokens.quiet.opacity(0.5), lineWidth: 1)
                    .background(Tokens.quiet.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
            )
        }

        if let agent = state.pendingApproval {
            ApprovalCard(agent: agent)
        }

        LazyVGrid(columns: columns, spacing: 10) {
            summaryCard("Agents", agentSummary) { state.tab = .agents }
            summaryCard("Weather", weatherSummary) { state.tab = .weather }
            summaryCard("Next meeting", meetingSummary) { state.tab = .calendar }
            summaryCard("Sports", sportsSummary) { state.tab = .sports }
        }

        if let media = state.media {
            HStack(spacing: 12) {
                artwork(media)
                VStack(alignment: .leading, spacing: 2) {
                    Text(media.title).font(.ui(12.5, .medium)).foregroundStyle(.white).lineLimit(1)
                    Text(media.artist.isEmpty ? media.app : media.artist)
                        .font(.ui(11)).foregroundStyle(Tokens.textTertiary).lineLimit(1)
                    Meter(value: media.progress, color: settings.theme.accent, width: 220)
                }
                Spacer()
                MediaControls()
            }
            .hudCard()
        }
    }

    private func artwork(_ media: NowPlaying) -> some View {
        Group {
            if let image = media.artwork {
                Image(nsImage: image).resizable().aspectRatio(contentMode: .fill)
            } else {
                Rectangle().fill(Tokens.control).overlay(
                    Image(systemName: "music.note").foregroundStyle(Tokens.textTertiary)
                )
            }
        }
        .frame(width: 38, height: 38)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var agentSummary: String {
        if state.agents.isEmpty { return "No agents connected" }
        let running = state.agents.filter { $0.state == .running }.count
        let needsYou = state.agents.filter { $0.state == .permission }.count
        if needsYou > 0 { return "\(needsYou) needs you" }
        return "\(running) running"
    }

    private var weatherSummary: String {
        guard let weather = state.weather else { return "Set a location" }
        let temp = settings.useCelsius ? weather.tempC : weather.tempC * 9 / 5 + 32
        return "\(Int(temp.rounded()))° · \(weather.condition)"
    }

    private var meetingSummary: String {
        state.nextMeeting?.title ?? "Nothing scheduled"
    }

    private var sportsSummary: String {
        guard let game = state.game else { return "No games today for your teams" }
        return "\(game.away) \(game.awayScore) – \(game.homeScore) \(game.home)"
    }

    private func summaryCard(_ label: String, _ value: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 5) {
                SectionLabel(label)
                Text(value)
                    .font(.ui(12.5, .medium))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .hudCard()
        }
        .buttonStyle(.plain)
    }
}

struct ApprovalCard: View {
    let agent: Agent

    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                StatusDot(color: settings.theme.permission, pulsing: true)
                Text(agent.name).font(.ui(12.5, .medium)).foregroundStyle(.white)
                if !agent.project.isEmpty {
                    Text("· \(agent.project)").font(.ui(11)).foregroundStyle(Tokens.textTertiary)
                }
                Spacer()
                Text("NEEDS YOU")
                    .font(.ui(10.5, .semibold))
                    .foregroundStyle(settings.theme.permission)
            }

            if let ask = agent.ask {
                Text(ask).font(.ui(12)).foregroundStyle(Tokens.textSecondary)
            }

            if let command = agent.command {
                Text("$ \(command)")
                    .font(.mono(11.5))
                    .foregroundStyle(.white)
                    .padding(.vertical, 6)
                    .padding(.horizontal, 10)
                    .background(Tokens.control, in: RoundedRectangle(cornerRadius: 6))
                    .textSelection(.enabled)
            }

            HStack(spacing: 8) {
                Spacer()
                Button("Deny") { state.decide(agentID: agent.id, approve: false) }
                    .buttonStyle(SmallButtonStyle(filled: false))
                Button("Approve") { state.decide(agentID: agent.id, approve: true) }
                    .buttonStyle(SmallButtonStyle(filled: true))
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .stroke(settings.theme.permission.opacity(0.33), lineWidth: 1)
                .background(Tokens.card, in: RoundedRectangle(cornerRadius: 10))
        )
    }
}

struct SmallButtonStyle: ButtonStyle {
    let filled: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.ui(11.5, .semibold))
            .foregroundStyle(filled ? Color(hex: 0x111111) : Color.white)
            .padding(.vertical, 6)
            .padding(.horizontal, 12)
            .background(filled ? Color.white : Tokens.control, in: RoundedRectangle(cornerRadius: 7))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

struct MediaControls: View {
    @EnvironmentObject private var state: HUDState

    var body: some View {
        HStack(spacing: 8) {
            control("backward.fill") { MediaService.shared.previous() }
            control(state.media?.playing == true ? "pause.fill" : "play.fill") { MediaService.shared.playPause() }
            control("forward.fill") { MediaService.shared.next() }
        }
    }

    private func control(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 11))
                .foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(Tokens.control, in: Circle())
        }
        .buttonStyle(.plain)
    }
}
