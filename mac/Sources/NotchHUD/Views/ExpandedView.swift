import SwiftUI

/// The tabbed panel. Clicking inside never collapses it; the panel controller's
/// global monitor handles clicks outside, and Esc is wired in AppDelegate.
struct ExpandedView: View {
    let geometry: PanelGeometry

    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if geometry.drawsNotchShape {
                Color.clear.frame(height: 20)   // clear the notch itself
            }

            tabBar

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    tabContent
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollIndicators(.never)
        }
        .padding(.horizontal, 14)
        .padding(.bottom, 14)
        .padding(.top, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var enabledTabs: [HUDTab] {
        HUDTab.allCases.filter { tab in
            switch tab {
            case .overview: true
            case .agents: settings.isEnabled(.agents)
            case .inbox: settings.isEnabled(.email) || settings.isEnabled(.teams) || settings.isEnabled(.imessage)
            case .reminders: settings.isEnabled(.reminders)
            case .calendar: settings.isEnabled(.meetings)
            case .weather: settings.isEnabled(.weather)
            case .sports: settings.isEnabled(.sports)
            case .media: settings.isEnabled(.media)
            case .focus: settings.isEnabled(.pomodoro)
            case .bookmarks: settings.isEnabled(.bookmarks)
            case .shelf: settings.isEnabled(.shelf) || settings.isEnabled(.clipboard)
            }
        }
    }

    private var tabBar: some View {
        HStack(spacing: 4) {
            ForEach(enabledTabs) { tab in
                TabButton(tab: tab, active: state.tab == tab, count: count(for: tab)) {
                    state.tab = tab
                }
            }

            Spacer()

            Button {
                settings.quietManual.toggle()
            } label: {
                Image(systemName: "moon.fill")
                    .font(.system(size: 11))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(state.quietActive ? Tokens.quiet : Tokens.card, in: RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
            .help(state.quietActive ? "Quiet is on — \(state.quietReason)" : "Turn Quiet on")

            Button {
                state.collapse()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Tokens.textTertiary)
                    .frame(width: 28, height: 28)
                    .background(Tokens.card, in: RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
    }

    private func count(for tab: HUDTab) -> Int {
        switch tab {
        case .agents: state.agents.filter { $0.state == .running || $0.state == .permission }.count
        case .inbox: state.notifications.count
        case .reminders: state.reminders.filter { !$0.done }.count
        case .calendar: state.events.count
        default: 0
        }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch state.tab {
        case .overview: OverviewTab()
        case .agents: AgentsTab()
        case .inbox: InboxTab()
        case .reminders: RemindersTab()
        case .calendar: CalendarTab()
        case .weather: WeatherTab()
        case .sports: SportsTab()
        case .media: MediaTab()
        case .focus: FocusTab()
        case .bookmarks: BookmarksTab()
        case .shelf: ShelfTab()
        }
    }
}

private struct TabButton: View {
    let tab: HUDTab
    let active: Bool
    let count: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: tab.symbol)
                    .font(.system(size: 11))
                if active {
                    Text(tab.label).font(.ui(12, .medium))
                }
                if count > 0 {
                    Text("\(count)").font(.mono(10))
                        .foregroundStyle(active ? .white : Tokens.textTertiary)
                }
            }
            .foregroundStyle(active ? Color.white : Color(hex: 0xB8B8C2))
            .padding(.horizontal, active ? 10 : 8)
            .frame(height: 28)
            .background(active ? Tokens.keycap : Tokens.card, in: RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .animation(.easeOut(duration: 0.15), value: active)
    }
}
