import Foundation

/// ESPN's public scoreboard/summary JSON — no key, no account. A game only
/// appears when a followed team is actually in the pre/in/post window; there is
/// no fixture data anywhere in this path.
@MainActor
final class SportsService {

    static let shared = SportsService()

    struct LeaguePath { let sport: String; let league: String }

    static let leagues: [String: LeaguePath] = [
        "NFL": .init(sport: "football", league: "nfl"),
        "NBA": .init(sport: "basketball", league: "nba"),
        "WNBA": .init(sport: "basketball", league: "wnba"),
        "MLB": .init(sport: "baseball", league: "mlb"),
        "NHL": .init(sport: "hockey", league: "nhl"),
        "NCAAF": .init(sport: "football", league: "college-football"),
        "NCAAB": .init(sport: "basketball", league: "mens-college-basketball"),
        "MLS": .init(sport: "soccer", league: "usa.1"),
        "EPL": .init(sport: "soccer", league: "eng.1"),
    ]

    private var task: Task<Void, Never>?
    private var seenPlayIDs: Set<String> = []

    private init() {}

    func start() {
        task?.cancel()
        task = Task { [weak self] in
            while !Task.isCancelled {
                await self?.tick()
                let live = HUDState.shared.game?.live ?? false
                try? await Task.sleep(for: .seconds(live ? 12 : 300))
            }
        }
    }

    func stop() { task?.cancel() }

    func teams(in leagueCode: String) async -> [(id: String, name: String)] {
        guard let path = Self.leagues[leagueCode],
              let url = URL(string: "https://site.api.espn.com/apis/site/v2/sports/\(path.sport)/\(path.league)/teams"),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let sports = root["sports"] as? [[String: Any]],
              let leagues = sports.first?["leagues"] as? [[String: Any]],
              let entries = leagues.first?["teams"] as? [[String: Any]]
        else { return [] }

        return entries.compactMap { entry in
            guard let team = entry["team"] as? [String: Any],
                  let id = team["id"] as? String,
                  let name = team["displayName"] as? String else { return nil }
            return (id, name)
        }
    }

    private func tick() async {
        let settings = Settings.shared
        guard settings.isEnabled(.sports), !settings.leagues.isEmpty, !settings.teams.isEmpty else {
            HUDState.shared.game = nil
            HUDState.shared.plays = []
            return
        }

        var candidates: [Game] = []
        for code in settings.leagues {
            candidates.append(contentsOf: await scoreboard(code: code, followed: settings.teams))
        }

        let game = candidates.min { lhs, rhs in
            rank(lhs.state) < rank(rhs.state)
        }
        HUDState.shared.game = game

        guard let game, game.live, settings.sportsMode != .score else { return }

        let plays = await self.plays(league: game.league, eventID: game.id)
        let fresh = plays.filter { !seenPlayIDs.contains($0.id) }

        if !seenPlayIDs.isEmpty {
            let interesting = settings.sportsMode == .scoring ? fresh.filter(\.scoring) : fresh
            // Several plays can land in one poll; show the newest rather than a burst.
            if let play = interesting.first {
                HUDState.shared.show(
                    Toast(
                        keys: [game.league],
                        label: "\(play.awayScore)–\(play.homeScore) · \(play.text)",
                        meter: nil,
                        state: "Live",
                        stateColor: Tokens.liveSports,
                        duration: play.scoring ? 2.4 : 1.8
                    ),
                    module: .sports
                )
            }
        }

        seenPlayIDs = Set(plays.map(\.id))
        HUDState.shared.plays = plays
    }

    private func rank(_ state: String) -> Int {
        switch state { case "in": 0; case "pre": 1; default: 2 }
    }

    private func scoreboard(code: String, followed: Set<String>) async -> [Game] {
        guard let path = Self.leagues[code],
              let url = URL(string: "https://site.api.espn.com/apis/site/v2/sports/\(path.sport)/\(path.league)/scoreboard"),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let events = root["events"] as? [[String: Any]]
        else { return [] }

        let now = Date()
        return events.compactMap { event -> Game? in
            guard let id = event["id"] as? String,
                  let competitions = event["competitions"] as? [[String: Any]],
                  let competition = competitions.first,
                  let competitors = competition["competitors"] as? [[String: Any]]
            else { return nil }

            let home = competitors.first { $0["homeAway"] as? String == "home" }
            let away = competitors.first { $0["homeAway"] as? String == "away" }
            guard let home, let away else { return nil }

            let homeID = (home["team"] as? [String: Any])?["id"] as? String
            let awayID = (away["team"] as? [String: Any])?["id"] as? String
            // Real data only: a game the user does not follow is not our business.
            guard followed.contains(homeID ?? "") || followed.contains(awayID ?? "") else { return nil }

            let status = (competition["status"] ?? event["status"]) as? [String: Any]
            let type = status?["type"] as? [String: Any]
            guard let state = type?["state"] as? String else { return nil }

            let start = (event["date"] as? String).flatMap(ISO8601DateFormatter().date(from:)) ?? now
            let inWindow: Bool = switch state {
            case "in": true
            case "pre": start.timeIntervalSince(now) > 0 && start.timeIntervalSince(now) <= 3600
            case "post": now.timeIntervalSince(start) < 6 * 3600
            default: false
            }
            guard inWindow else { return nil }

            func teamName(_ competitor: [String: Any]) -> String {
                let team = competitor["team"] as? [String: Any]
                return (team?["shortDisplayName"] as? String) ?? (team?["displayName"] as? String) ?? "?"
            }

            return Game(
                id: id,
                league: code,
                home: teamName(home),
                away: teamName(away),
                homeScore: Int(home["score"] as? String ?? "0") ?? 0,
                awayScore: Int(away["score"] as? String ?? "0") ?? 0,
                period: type?["shortDetail"] as? String ?? "",
                state: state
            )
        }
    }

    private func plays(league: String, eventID: String) async -> [Play] {
        guard let path = Self.leagues[league],
              let url = URL(string: "https://site.api.espn.com/apis/site/v2/sports/\(path.sport)/\(path.league)/summary?event=\(eventID)"),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let raw = root["plays"] as? [[String: Any]]
        else { return [] }

        return raw.reversed().prefix(50).map { entry in
            Play(
                id: entry["id"] as? String ?? UUID().uuidString,
                clock: (entry["clock"] as? [String: Any])?["displayValue"] as? String ?? "",
                text: entry["text"] as? String ?? "",
                scoring: entry["scoringPlay"] as? Bool ?? false,
                homeScore: entry["homeScore"] as? Int ?? 0,
                awayScore: entry["awayScore"] as? Int ?? 0
            )
        }
    }
}
