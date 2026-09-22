import AppKit
import EventKit
import SwiftUI

/// EventKit for meetings and reminders. Join URLs are parsed out of the location
/// and notes so the Join button works without any vendor SDK.
@MainActor
final class CalendarService {

    static let shared = CalendarService()

    private let store = EKEventStore()
    private var task: Task<Void, Never>?

    private let joinPattern = try? NSRegularExpression(
        pattern: #"https?://[\w.-]*(teams\.microsoft\.com|teams\.live\.com|zoom\.us|meet\.google\.com|webex\.com|whereby\.com)/\S+"#,
        options: [.caseInsensitive]
    )

    private init() {}

    func start() {
        Task {
            _ = try? await store.requestFullAccessToEvents()
            _ = try? await store.requestFullAccessToReminders()
            await refresh()
        }

        task?.cancel()
        task = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(300))
                await self?.refresh()
            }
        }

        NotificationCenter.default.addObserver(
            forName: .EKEventStoreChanged, object: store, queue: .main
        ) { _ in
            Task { @MainActor in await CalendarService.shared.refresh() }
        }
    }

    func refresh() async {
        if Settings.shared.isEnabled(.meetings) { loadEvents() } else { HUDState.shared.events = [] }
        if Settings.shared.isEnabled(.reminders) { await loadReminders() } else { HUDState.shared.reminders = [] }
    }

    private func loadEvents() {
        guard EKEventStore.authorizationStatus(for: .event) == .fullAccess else {
            HUDState.shared.events = []
            return
        }

        let calendar = Calendar.current
        // A month either side so the calendar grid has real dots to draw.
        let start = calendar.date(byAdding: .day, value: -31, to: Date()) ?? Date()
        let end = calendar.date(byAdding: .day, value: 62, to: start) ?? Date()

        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: nil)
        HUDState.shared.events = store.events(matching: predicate)
            .filter { !$0.isAllDay }
            .map { event in
                MeetingEvent(
                    id: event.eventIdentifier ?? UUID().uuidString,
                    title: event.title ?? "Untitled",
                    start: event.startDate,
                    end: event.endDate,
                    calendarColor: Color(nsColor: NSColor(cgColor: event.calendar.cgColor) ?? .systemBlue),
                    joinURL: findJoinURL(event),
                    location: event.location
                )
            }
    }

    private func findJoinURL(_ event: EKEvent) -> URL? {
        if let url = event.url, isMeetingURL(url) { return url }
        for field in [event.location, event.notes] {
            guard let field, let match = firstMatch(in: field) else { continue }
            return URL(string: match)
        }
        return nil
    }

    private func isMeetingURL(_ url: URL) -> Bool {
        let host = url.host ?? ""
        return ["teams.microsoft.com", "teams.live.com", "zoom.us", "meet.google.com", "webex.com"]
            .contains { host.contains($0) }
    }

    private func firstMatch(in text: String) -> String? {
        guard let joinPattern else { return nil }
        let range = NSRange(text.startIndex..., in: text)
        guard let match = joinPattern.firstMatch(in: text, range: range),
              let matched = Range(match.range, in: text) else { return nil }
        return String(text[matched]).trimmingCharacters(in: CharacterSet(charactersIn: ">),.\""))
    }

    private func loadReminders() async {
        guard EKEventStore.authorizationStatus(for: .reminder) == .fullAccess else {
            HUDState.shared.reminders = []
            return
        }

        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: Date())
        let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay) ?? Date()

        let predicate = store.predicateForIncompleteReminders(
            withDueDateStarting: nil, ending: endOfDay, calendars: nil
        )

        let reminders: [EKReminder] = await withCheckedContinuation { continuation in
            store.fetchReminders(matching: predicate) { continuation.resume(returning: $0 ?? []) }
        }

        HUDState.shared.reminders = reminders.map { reminder in
            ReminderItem(
                id: reminder.calendarItemIdentifier,
                text: reminder.title ?? "Untitled",
                done: reminder.isCompleted,
                listName: reminder.calendar?.title ?? "Reminders",
                due: reminder.dueDateComponents.flatMap { calendar.date(from: $0) }
            )
        }
    }

    func toggleReminder(id: String) {
        guard let reminder = store.calendarItem(withIdentifier: id) as? EKReminder else { return }
        reminder.isCompleted.toggle()
        try? store.save(reminder, commit: true)
        Task { await loadReminders() }
    }
}
