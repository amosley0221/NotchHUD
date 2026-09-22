import Foundation
import SwiftUI

enum AgentState: String, Codable, Sendable {
    case running, permission, done, error

    /// Priority for the glow colour: permission > error > running > done.
    var priority: Int {
        switch self {
        case .permission: 0
        case .error: 1
        case .running: 2
        case .done: 3
        }
    }
}

struct Agent: Identifiable, Codable, Equatable, Sendable {
    var id: String
    var name: String
    var project: String
    var task: String
    var state: AgentState
    var ask: String?
    var command: String?
}

struct Toast: Equatable {
    var keys: [String]
    var label: String
    var meter: Double?
    var state: String?
    var stateColor: Color?
    var duration: Double
}

struct IncomingCall: Equatable {
    var name: String
    var source: String
    var app: String
    var video: Bool
    var joinURL: URL?
}

struct NowPlaying: Equatable {
    var title: String
    var artist: String
    var app: String
    var playing: Bool
    var progress: Double
    var artwork: NSImage?

    static func == (lhs: NowPlaying, rhs: NowPlaying) -> Bool {
        lhs.title == rhs.title && lhs.artist == rhs.artist
            && lhs.playing == rhs.playing && abs(lhs.progress - rhs.progress) < 0.01
    }
}

struct MeetingEvent: Identifiable, Equatable {
    var id: String
    var title: String
    var start: Date
    var end: Date
    var calendarColor: Color
    var joinURL: URL?
    var location: String?

    var isLive: Bool { (start...end).contains(Date()) }
}

struct ReminderItem: Identifiable, Equatable {
    var id: String
    var text: String
    var done: Bool
    var listName: String
    var due: Date?
}

struct WeatherSnapshot: Equatable {
    var city: String
    var tempC: Double
    var feelsLikeC: Double
    var hiC: Double
    var loC: Double
    var condition: String
    var symbol: String
    var humidity: Int
    var windKph: Double
    var alert: String?
    var hourly: [HourPoint]
}

struct HourPoint: Identifiable, Equatable {
    var id: String { hour }
    var hour: String
    var tempC: Double
    var symbol: String
    var precipChance: Int
}

struct Game: Identifiable, Equatable {
    var id: String
    var league: String
    var home: String
    var away: String
    var homeScore: Int
    var awayScore: Int
    var period: String
    var state: String

    var live: Bool { state == "in" }
}

struct Play: Identifiable, Equatable {
    var id: String
    var clock: String
    var text: String
    var scoring: Bool
    var homeScore: Int
    var awayScore: Int
}

struct NotificationItem: Identifiable, Equatable {
    enum Kind: String { case email, teams, imessage, system }
    var id: String
    var kind: Kind
    var title: String
    var body: String
    var date: Date
}

struct Bookmark: Identifiable, Codable, Equatable {
    var id: String
    var label: String
    var url: URL
    var colorHex: UInt32

    var host: String { url.host?.replacingOccurrences(of: "www.", with: "") ?? url.absoluteString }
    var color: Color { Color(hex: colorHex) }
}

enum SportsMode: String, CaseIterable, Codable, Identifiable {
    case score, scoring, playByPlay
    var id: String { rawValue }

    var label: String {
        switch self {
        case .score: "Score only"
        case .scoring: "Scoring plays"
        case .playByPlay: "Play-by-play"
        }
    }
}

enum SlotKind: String, CaseIterable, Codable, Identifiable {
    case agents, media, weather, meeting, sports, focus, inbox, clock
    var id: String { rawValue }

    var label: String {
        switch self {
        case .agents: "Agents"
        case .media: "Media"
        case .weather: "Weather"
        case .meeting: "Meeting"
        case .sports: "Sports"
        case .focus: "Focus"
        case .inbox: "Inbox"
        case .clock: "Clock"
        }
    }
}

enum HUDTab: String, CaseIterable, Identifiable {
    case overview, agents, inbox, reminders, calendar, weather, sports, media, focus, bookmarks, shelf
    var id: String { rawValue }

    var label: String {
        switch self {
        case .overview: "Overview"
        case .agents: "Agents"
        case .inbox: "Inbox"
        case .reminders: "Reminders"
        case .calendar: "Calendar"
        case .weather: "Weather"
        case .sports: "Sports"
        case .media: "Media"
        case .focus: "Focus"
        case .bookmarks: "Bookmarks"
        case .shelf: "Shelf"
        }
    }

    var symbol: String {
        switch self {
        case .overview: "square.grid.2x2"
        case .agents: "sparkles"
        case .inbox: "bubble.left"
        case .reminders: "checklist"
        case .calendar: "calendar"
        case .weather: "cloud.sun"
        case .sports: "sportscourt"
        case .media: "music.note"
        case .focus: "timer"
        case .bookmarks: "bookmark"
        case .shelf: "tray"
        }
    }
}

enum Module: String, CaseIterable, Codable, Identifiable {
    case agents, shortcuts, systemHUD, weather, media, meetings, sports
    case email, teams, imessage, calls, reminders, pomodoro, bookmarks, clipboard, shelf

    var id: String { rawValue }

    var label: String {
        switch self {
        case .agents: "Agent status"
        case .shortcuts: "Shortcut feedback"
        case .systemHUD: "System HUD"
        case .weather: "Weather"
        case .media: "Now playing"
        case .meetings: "Meetings"
        case .sports: "Sports scores"
        case .email: "Email"
        case .teams: "Teams messages"
        case .imessage: "iMessage"
        case .calls: "Calls"
        case .reminders: "Reminders"
        case .pomodoro: "Pomodoro"
        case .bookmarks: "Bookmarks"
        case .clipboard: "Clipboard"
        case .shelf: "Files shelf"
        }
    }
}

enum HUDMode: Equatable {
    case compact
    case toast(Toast)
    case call(IncomingCall)
    case expanded
}
