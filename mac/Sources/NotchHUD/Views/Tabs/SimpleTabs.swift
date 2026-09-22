import AppKit
import SwiftUI

// MARK: - Agents

struct AgentsTab: View {
    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    var body: some View {
        if state.agents.isEmpty {
            EmptyStateView(
                title: "No agents connected",
                hint: "Add the hook from Settings → Agents, or run `notchhud emit` from any script."
            )
        } else {
            ForEach(state.agents) { agent in
                if agent.state == .permission {
                    ApprovalCard(agent: agent)
                } else {
                    HStack(spacing: 10) {
                        StatusDot(color: settings.theme.color(for: agent.state), pulsing: agent.state == .running)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(agent.project.isEmpty ? agent.name : "\(agent.name) · \(agent.project)")
                                .font(.ui(12.5, .medium)).foregroundStyle(.white).lineLimit(1)
                            Text(agent.task)
                                .font(.ui(11)).foregroundStyle(Tokens.textTertiary).lineLimit(1)
                        }
                        Spacer()
                        Text(agent.state.rawValue.capitalized)
                            .font(.ui(10.5, .semibold))
                            .foregroundStyle(settings.theme.color(for: agent.state))
                    }
                    .hudCard()
                }
            }
        }
    }
}

// MARK: - Inbox

struct InboxTab: View {
    @EnvironmentObject private var state: HUDState

    var body: some View {
        if state.notifications.isEmpty {
            EmptyStateView(title: "All caught up.", hint: nil)
        } else {
            HStack {
                SectionLabel("Inbox · \(state.notifications.count)")
                Spacer()
                Button("Clear all") {
                    state.notifications.removeAll()
                    state.heldNotifications.removeAll()
                }
                .buttonStyle(SmallButtonStyle(filled: false))
            }

            ForEach(state.notifications) { item in
                HStack(spacing: 10) {
                    Image(systemName: symbol(for: item.kind))
                        .font(.system(size: 11))
                        .foregroundStyle(.white)
                        .frame(width: 24, height: 24)
                        .background(tint(for: item.kind), in: RoundedRectangle(cornerRadius: 7))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title).font(.ui(12, .medium)).foregroundStyle(.white).lineLimit(1)
                        Text(item.body).font(.ui(11)).foregroundStyle(Tokens.textTertiary).lineLimit(1)
                    }
                    Spacer()
                    Text(item.date, format: .dateTime.hour().minute())
                        .font(.mono(10)).foregroundStyle(Tokens.textMuted)
                }
                .hudCard()
            }
        }
    }

    private func symbol(for kind: NotificationItem.Kind) -> String {
        switch kind {
        case .email: "envelope"
        case .teams: "bubble.left"
        case .imessage: "message"
        case .system: "bell"
        }
    }

    private func tint(for kind: NotificationItem.Kind) -> Color {
        switch kind {
        case .teams: Tokens.teams
        case .imessage: Tokens.iMessage
        default: Tokens.control
        }
    }
}

// MARK: - Reminders

struct RemindersTab: View {
    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text("Today").font(.ui(16, .semibold)).foregroundStyle(.white)
            Text("\(state.reminders.filter { !$0.done }.count) reminders to do")
                .font(.ui(11)).foregroundStyle(Tokens.textTertiary)
            Spacer()
        }

        if state.reminders.isEmpty {
            EmptyStateView(title: "Nothing due today.", hint: "Reminders come from the Reminders app.")
        } else {
            ForEach(state.reminders) { reminder in
                HStack(spacing: 10) {
                    Button {
                        CalendarService.shared.toggleReminder(id: reminder.id)
                    } label: {
                        Circle()
                            .strokeBorder(reminder.done ? settings.theme.done : Color(hex: 0x6F6F78), lineWidth: 1.5)
                            .background(Circle().fill(reminder.done ? settings.theme.done : .clear))
                            .frame(width: 18, height: 18)
                    }
                    .buttonStyle(.plain)

                    VStack(alignment: .leading, spacing: 1) {
                        Text(reminder.text)
                            .font(.ui(12.5, .medium))
                            .foregroundStyle(reminder.done ? Tokens.textMuted : .white)
                            .strikethrough(reminder.done)
                        Text(metaLine(reminder)).font(.ui(10.5)).foregroundStyle(Tokens.textMuted)
                    }
                    Spacer()
                }
                .padding(.vertical, 10)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(Color.white.opacity(0.07)).frame(height: 1)
                }
            }
        }
    }

    private func metaLine(_ reminder: ReminderItem) -> String {
        guard let due = reminder.due else { return reminder.listName }
        return "\(reminder.listName) · \(due.formatted(date: .omitted, time: .shortened))"
    }
}

// MARK: - Media

struct MediaTab: View {
    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    var body: some View {
        if let media = state.media {
            HStack(spacing: 14) {
                Group {
                    if let image = media.artwork {
                        Image(nsImage: image).resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Rectangle().fill(Tokens.control)
                            .overlay(Image(systemName: "music.note").foregroundStyle(Tokens.textTertiary))
                    }
                }
                .frame(width: 64, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 4) {
                    Text(media.title).font(.ui(15, .semibold)).foregroundStyle(.white).lineLimit(1)
                    Text(media.artist).font(.ui(12)).foregroundStyle(Tokens.textSecondary).lineLimit(1)
                    Meter(value: media.progress, color: settings.theme.accent, width: 260)
                    MediaControls()
                }
                Spacer()
                Text(media.app).font(.ui(10.5)).foregroundStyle(Tokens.textMuted)
            }
        } else {
            EmptyStateView(
                title: "Nothing playing",
                hint: "macOS 15.4 and later restrict the now-playing API to entitled apps; see the README."
            )
        }
    }
}

// MARK: - Focus

struct FocusTab: View {
    @EnvironmentObject private var state: HUDState

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(formatClock(state.pomodoroSecondsLeft))
                .font(.mono(44, .medium))
                .foregroundStyle(.white)
            Text(state.pomodoroRunning ? "Focus running" : "Paused")
                .font(.ui(12)).foregroundStyle(Tokens.textTertiary)
            HStack(spacing: 8) {
                Button(state.pomodoroRunning ? "Pause" : "Start") {
                    state.pomodoroRunning ? state.pausePomodoro() : state.startPomodoro()
                }
                .buttonStyle(SmallButtonStyle(filled: true))
                Button("Reset") { state.resetPomodoro() }
                    .buttonStyle(SmallButtonStyle(filled: false))
            }
        }
    }
}

// MARK: - Bookmarks

struct BookmarksTab: View {
    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 5)

    var body: some View {
        SectionLabel("Opens in Chrome")

        if settings.bookmarks.isEmpty {
            EmptyStateView(title: "No bookmarks yet.", hint: "Add up to five in Settings → Bookmarks.")
        } else {
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(settings.bookmarks) { bookmark in
                    Button {
                        BrowserService.open(bookmark.url)
                        state.show(
                            Toast(keys: ["Chrome"], label: "Opening \(bookmark.host)", meter: nil,
                                  state: nil, stateColor: nil, duration: 1.4),
                            module: .bookmarks
                        )
                    } label: {
                        VStack(spacing: 6) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10).fill(bookmark.color)
                                Text(bookmark.label.prefix(1).uppercased())
                                    .font(.ui(16, .semibold)).foregroundStyle(.white)
                            }
                            .frame(width: 38, height: 38)

                            Text(bookmark.label).font(.ui(11.5, .medium)).foregroundStyle(.white).lineLimit(1)
                            Text(bookmark.host).font(.ui(9.5)).foregroundStyle(Tokens.textMuted).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Tokens.card, in: RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

// MARK: - Shelf

struct ShelfTab: View {
    @EnvironmentObject private var state: HUDState

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 8) {
                SectionLabel("Clipboard")
                Text(state.clipboard.isEmpty ? "Nothing copied yet." : state.clipboard)
                    .font(.mono(11))
                    .foregroundStyle(state.clipboard.isEmpty ? Tokens.textMuted : Tokens.textSecondary)
                    .lineLimit(6)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .hudCard()

            VStack(alignment: .leading, spacing: 8) {
                SectionLabel("Files shelf")
                if state.shelf.isEmpty {
                    Text("Drop files here.").font(.ui(11)).foregroundStyle(Tokens.textMuted)
                } else {
                    ForEach(state.shelf, id: \.self) { url in
                        Button {
                            NSWorkspace.shared.open(url)
                        } label: {
                            Text(url.lastPathComponent).font(.ui(11)).foregroundStyle(Tokens.textSecondary).lineLimit(1)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                    .foregroundStyle(Color.white.opacity(0.15))
            )
            .onDrop(of: [.fileURL], isTargeted: nil) { providers in
                for provider in providers {
                    _ = provider.loadObject(ofClass: URL.self) { url, _ in
                        guard let url else { return }
                        Task { @MainActor in
                            if !state.shelf.contains(url) { state.shelf.append(url) }
                        }
                    }
                }
                return true
            }
        }
    }
}

// MARK: - Shared

struct EmptyStateView: View {
    let title: String
    let hint: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.ui(12.5)).foregroundStyle(Tokens.textSecondary)
            if let hint {
                Text(hint).font(.ui(10.5)).foregroundStyle(Tokens.textMuted)
            }
        }
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
