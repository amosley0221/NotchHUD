import AppKit
import SwiftUI

/// Month grid on the left, the selected day's events on the right.
struct CalendarTab: View {
    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    private let calendar = Calendar.current

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            monthGrid
                .frame(width: 220)

            Rectangle().fill(Color.white.opacity(0.07)).frame(width: 1)

            dayList
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Month

    private var displayedMonth: Date {
        calendar.date(byAdding: .month, value: state.calendarMonthOffset, to: Date()) ?? Date()
    }

    private var monthGrid: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(displayedMonth, format: .dateTime.month(.wide).year())
                    .font(.ui(12.5, .semibold)).foregroundStyle(.white)
                Spacer()
                stepper("chevron.left") { state.calendarMonthOffset -= 1 }
                stepper("chevron.right") { state.calendarMonthOffset += 1 }
            }

            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 7), spacing: 4) {
                ForEach(weekdayInitials, id: \.self) { initial in
                    Text(initial).font(.ui(9.5)).foregroundStyle(Tokens.textMuted)
                }
                ForEach(monthDays, id: \.self) { day in
                    dayCell(day)
                }
            }
        }
    }

    private var weekdayInitials: [String] {
        let symbols = calendar.veryShortWeekdaySymbols
        let first = calendar.firstWeekday - 1
        return Array(symbols[first...] + symbols[..<first])
    }

    /// Dates for the visible grid; nil-equivalent leading blanks are represented by
    /// dates from the previous month and drawn dimmed.
    private var monthDays: [Date] {
        guard let interval = calendar.dateInterval(of: .month, for: displayedMonth) else { return [] }
        let firstWeekday = calendar.component(.weekday, from: interval.start)
        let leading = (firstWeekday - calendar.firstWeekday + 7) % 7
        guard let gridStart = calendar.date(byAdding: .day, value: -leading, to: interval.start) else { return [] }
        return (0..<42).compactMap { calendar.date(byAdding: .day, value: $0, to: gridStart) }
    }

    private func dayCell(_ day: Date) -> some View {
        let isSelected = calendar.isDate(day, inSameDayAs: state.selectedCalendarDate)
        let isToday = calendar.isDateInToday(day)
        let inMonth = calendar.isDate(day, equalTo: displayedMonth, toGranularity: .month)
        let hasEvents = state.events.contains { calendar.isDate($0.start, inSameDayAs: day) }

        return Button {
            state.selectedCalendarDate = calendar.startOfDay(for: day)
        } label: {
            VStack(spacing: 2) {
                Text("\(calendar.component(.day, from: day))")
                    .font(.ui(10.5, isSelected || isToday ? .semibold : .regular))
                    .foregroundStyle(
                        isSelected ? Color(hex: 0x111111)
                            : isToday ? settings.theme.accent
                            : inMonth ? Color.white : Tokens.textMuted
                    )
                    .frame(width: 24, height: 24)
                    .background(isSelected ? Color.white : .clear, in: Circle())

                Circle()
                    .fill(hasEvents ? settings.theme.accent : .clear)
                    .frame(width: 2, height: 2)
            }
        }
        .buttonStyle(.plain)
    }

    private func stepper(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(Tokens.textSecondary)
                .frame(width: 28, height: 28)
                .background(Tokens.card, in: RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }

    // MARK: Day

    private var selectedEvents: [MeetingEvent] {
        state.events
            .filter { calendar.isDate($0.start, inSameDayAs: state.selectedCalendarDate) }
            .sorted { $0.start < $1.start }
    }

    private var dayList: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(state.selectedCalendarDate.formatted(.dateTime.weekday(.wide).day().month(.wide)))

            if selectedEvents.isEmpty {
                EmptyStateView(title: "Nothing scheduled.", hint: nil)
            } else {
                ForEach(selectedEvents) { event in
                    HStack(spacing: 10) {
                        Group {
                            if event.isLive {
                                Text("LIVE").font(.ui(9.5, .semibold)).foregroundStyle(settings.theme.accent)
                            } else {
                                Text(event.start, format: .dateTime.hour().minute())
                                    .font(.mono(11)).foregroundStyle(Tokens.textTertiary)
                            }
                        }
                        .frame(width: 44, alignment: .trailing)

                        Rectangle().fill(event.calendarColor).frame(width: 2, height: 28)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(event.title).font(.ui(12.5, .medium)).foregroundStyle(.white).lineLimit(1)
                            if let location = event.location, !location.isEmpty {
                                Text(location).font(.ui(10.5)).foregroundStyle(Tokens.textMuted).lineLimit(1)
                            }
                        }

                        Spacer()

                        if let joinURL = event.joinURL {
                            Button("Join") { NSWorkspace.shared.open(joinURL) }
                                .buttonStyle(JoinButtonStyle())
                        }
                    }
                }
            }
        }
    }
}

struct JoinButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.ui(11, .semibold))
            .foregroundStyle(.white)
            .padding(.vertical, 5)
            .padding(.horizontal, 11)
            .background(Color(hex: 0x3D8BFF), in: RoundedRectangle(cornerRadius: 7))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}
