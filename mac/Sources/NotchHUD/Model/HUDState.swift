import AppKit
import Combine
import SwiftUI

/// Shared state for every HUD panel. One instance; the panels are just views of it.
@MainActor
final class HUDState: ObservableObject {

    static let shared = HUDState()

    @Published var mode: HUDMode = .compact
    @Published var tab: HUDTab = .overview

    @Published var agents: [Agent] = []
    @Published var media: NowPlaying?
    @Published var events: [MeetingEvent] = []
    @Published var reminders: [ReminderItem] = []
    @Published var weather: WeatherSnapshot?
    @Published var game: Game?
    @Published var plays: [Play] = []
    @Published var notifications: [NotificationItem] = []
    @Published var heldNotifications: [NotificationItem] = []

    @Published var clipboard: String = ""
    @Published var shelf: [URL] = []

    @Published var pomodoroSecondsLeft: Int = 25 * 60
    @Published var pomodoroRunning: Bool = false

    @Published var quietActive: Bool = false
    @Published var quietReason: String = ""

    /// Bumped when an agent changes state, so the panels can run the sweep.
    @Published var sweepToken: UUID?

    @Published var selectedCalendarDate: Date = Calendar.current.startOfDay(for: Date())
    @Published var calendarMonthOffset: Int = 0

    private var toastTask: Task<Void, Never>?

    private init() {}

    // MARK: - Derived

    var primaryAgentState: AgentState? {
        agents.map(\.state).min { $0.priority < $1.priority }
    }

    var glowColor: Color {
        let theme = Settings.shared.theme
        if let state = primaryAgentState { return theme.color(for: state) }
        if quietActive { return Tokens.quiet }
        return theme.accent
    }

    var pendingApproval: Agent? {
        agents.first { $0.state == .permission }
    }

    var nextMeeting: MeetingEvent? {
        events.filter { $0.end > Date() }.min { $0.start < $1.start }
    }

    // MARK: - Toasts

    /// Shows a toast, replacing any current one and resetting the timer. A toast
    /// also collapses the expanded panel, per the spec.
    func show(_ toast: Toast, module: Module) {
        guard Settings.shared.isEnabled(module) else { return }

        if quietActive, !Settings.shared.breaksThrough(module) {
            hold(toast, module: module)
            return
        }

        toastTask?.cancel()
        withAnimation(.spring(response: Tokens.widthChange, dampingFraction: 0.85)) {
            mode = .toast(toast)
        }
        toastTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(toast.duration))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard let self else { return }
                if case .toast = self.mode {
                    withAnimation(.spring(response: Tokens.widthChange, dampingFraction: 0.85)) {
                        self.mode = .compact
                    }
                }
            }
        }
    }

    private func hold(_ toast: Toast, module: Module) {
        let kind: NotificationItem.Kind = switch module {
        case .email: .email
        case .teams: .teams
        case .imessage: .imessage
        default: .system
        }
        let item = NotificationItem(
            id: UUID().uuidString,
            kind: kind,
            title: toast.keys.joined(separator: " "),
            body: toast.label,
            date: Date()
        )
        heldNotifications.append(item)
        notifications.insert(item, at: 0)
    }

    /// When Quiet ends, one summary toast rather than a burst of missed ones.
    func releaseHeld() {
        let count = heldNotifications.count
        heldNotifications.removeAll()
        guard count > 0 else { return }
        show(
            Toast(
                keys: ["Quiet"],
                label: "\(count) notification\(count == 1 ? "" : "s") held during your call",
                meter: nil,
                state: "Review",
                stateColor: Tokens.quietLink,
                duration: 2.6
            ),
            module: .agents
        )
    }

    func showCall(_ call: IncomingCall) {
        guard Settings.shared.isEnabled(.calls) else { return }
        toastTask?.cancel()
        withAnimation(.spring(response: Tokens.widthChange, dampingFraction: 0.85)) {
            mode = .call(call)
        }
    }

    func dismissCall() {
        withAnimation(.spring(response: Tokens.widthChange, dampingFraction: 0.85)) {
            mode = .compact
        }
    }

    func toggleExpanded() {
        withAnimation(.spring(response: Tokens.widthChange, dampingFraction: 0.85)) {
            mode = (mode == .expanded) ? .compact : .expanded
        }
    }

    func collapse() {
        guard mode == .expanded else { return }
        withAnimation(.spring(response: Tokens.widthChange, dampingFraction: 0.85)) {
            mode = .compact
        }
    }

    // MARK: - Agents

    func upsert(_ agent: Agent) {
        let previous = agents.first { $0.id == agent.id }?.state
        if let index = agents.firstIndex(where: { $0.id == agent.id }) {
            agents[index] = agent
        } else {
            agents.append(agent)
        }
        guard previous != agent.state else { return }

        sweepToken = UUID()
        let theme = Settings.shared.theme
        switch agent.state {
        case .done:
            show(
                Toast(keys: [agent.name], label: "Task complete", meter: nil,
                      state: "Done", stateColor: theme.done, duration: 1.4),
                module: .agents
            )
        case .error:
            show(
                Toast(keys: [agent.name], label: agent.task, meter: nil,
                      state: "Error", stateColor: theme.error, duration: 2.0),
                module: .agents
            )
        case .permission:
            show(
                Toast(keys: [agent.name], label: agent.ask ?? "Needs your approval", meter: nil,
                      state: "NEEDS YOU", stateColor: theme.permission, duration: 2.4),
                module: .agents
            )
        case .running:
            break
        }
    }

    func remove(agentID: String) {
        agents.removeAll { $0.id == agentID }
    }

    func decide(agentID: String, approve: Bool) {
        guard let index = agents.firstIndex(where: { $0.id == agentID }) else { return }
        let command = agents[index].command ?? ""
        agents[index].state = approve ? .running : .error
        agents[index].ask = nil
        if !approve { agents[index].task = "Denied — waiting for instructions" }

        AgentBridge.shared.publishDecision(agentID: agentID, approve: approve)

        let theme = Settings.shared.theme
        show(
            Toast(
                keys: [approve ? "Approved" : "Denied"],
                label: command.isEmpty ? agents[index].name : command,
                meter: nil,
                state: nil,
                stateColor: approve ? theme.done : theme.error,
                duration: 1.6
            ),
            module: .agents
        )
    }

    // MARK: - Pomodoro

    func startPomodoro() { pomodoroRunning = true }
    func pausePomodoro() { pomodoroRunning = false }
    func resetPomodoro() { pomodoroRunning = false; pomodoroSecondsLeft = 25 * 60 }

    func tickPomodoro() {
        guard pomodoroRunning, pomodoroSecondsLeft > 0 else { return }
        pomodoroSecondsLeft -= 1
        if pomodoroSecondsLeft == 0 {
            pomodoroRunning = false
            show(
                Toast(keys: ["Focus"], label: "Pomodoro complete", meter: nil,
                      state: "Done", stateColor: Settings.shared.theme.done, duration: 2.0),
                module: .pomodoro
            )
        }
    }
}

func formatClock(_ seconds: Int) -> String {
    String(format: "%02d:%02d", seconds / 60, seconds % 60)
}
