import SwiftUI

struct WeatherTab: View {
    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    var body: some View {
        if let weather = state.weather {
            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(weather.city).font(.ui(14, .semibold)).foregroundStyle(.white)
                    Text(Date(), format: .dateTime.weekday(.wide).day().month())
                        .font(.ui(10.5)).foregroundStyle(Tokens.textMuted)

                    HStack(spacing: 10) {
                        Image(systemName: weather.symbol)
                            .symbolRenderingMode(.multicolor)
                            .font(.system(size: 34))
                        Text("\(temp(weather.tempC))°").font(.mono(34, .medium)).foregroundStyle(.white)
                    }

                    Text(weather.condition).font(.ui(12.5, .medium)).foregroundStyle(Tokens.textSecondary)
                    Text("Feels like \(temp(weather.feelsLikeC))°").font(.ui(10.5)).foregroundStyle(Tokens.textMuted)

                    Text("H \(temp(weather.hiC))°  L \(temp(weather.loC))°  ·  \(weather.humidity)%  ·  \(Int(weather.windKph.rounded())) km/h")
                        .font(.ui(10.5)).foregroundStyle(Tokens.textTertiary)

                    if let alert = weather.alert {
                        Text(alert).font(.ui(11)).foregroundStyle(settings.theme.permission)
                    }
                }
                .frame(width: 220, alignment: .leading)

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        SectionLabel("Hourly")
                        Spacer()
                        Button(settings.useCelsius ? "°C" : "°F") { settings.useCelsius.toggle() }
                            .buttonStyle(SmallButtonStyle(filled: false))
                    }

                    HStack(alignment: .top, spacing: 0) {
                        ForEach(weather.hourly.prefix(6)) { point in
                            VStack(spacing: 4) {
                                Text(point.hour).font(.ui(10)).foregroundStyle(Tokens.textMuted)
                                Image(systemName: point.symbol)
                                    .symbolRenderingMode(.multicolor)
                                    .font(.system(size: 18))
                                Text(point.precipChance > 0 ? "\(point.precipChance)%" : " ")
                                    .font(.ui(9.5)).foregroundStyle(Color(hex: 0x7FB2FF))
                                Text("\(temp(point.tempC))°").font(.mono(11.5)).foregroundStyle(.white)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        } else {
            EmptyStateView(
                title: "Set a location",
                hint: "Settings → Weather. Nothing is shown until a real fetch succeeds."
            )
        }
    }

    private func temp(_ celsius: Double) -> Int {
        Int((settings.useCelsius ? celsius : celsius * 9 / 5 + 32).rounded())
    }
}

struct SportsTab: View {
    @EnvironmentObject private var state: HUDState
    @EnvironmentObject private var settings: Settings

    var body: some View {
        if let game = state.game {
            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    SectionLabel(game.league)
                    Text(game.period)
                        .font(.ui(10.5, .semibold))
                        .foregroundStyle(game.live ? Tokens.liveSports : Tokens.textTertiary)

                    teamRow(game.away, game.awayScore)
                    teamRow(game.home, game.homeScore)
                }
                .frame(width: 190, alignment: .leading)
                .hudCard()

                VStack(alignment: .leading, spacing: 6) {
                    SectionLabel(settings.sportsMode.label)

                    if settings.sportsMode == .score {
                        Text("Score only — enable plays in Settings.")
                            .font(.ui(11)).foregroundStyle(Tokens.textMuted)
                    } else if state.plays.isEmpty {
                        Text("No plays yet.").font(.ui(11)).foregroundStyle(Tokens.textMuted)
                    } else {
                        ForEach(state.plays.prefix(6)) { play in
                            HStack(alignment: .top, spacing: 8) {
                                Text(play.clock)
                                    .font(.mono(10))
                                    .foregroundStyle(play.scoring ? Tokens.liveSports : Tokens.textMuted)
                                    .frame(width: 42, alignment: .leading)
                                Text(play.text)
                                    .font(.ui(11))
                                    .foregroundStyle(play.scoring ? .white : Tokens.textTertiary)
                                Spacer()
                                if play.scoring {
                                    Text("\(play.awayScore)–\(play.homeScore)")
                                        .font(.mono(10)).foregroundStyle(.white)
                                }
                            }
                            .padding(.vertical, 5)
                            .overlay(alignment: .bottom) {
                                Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        } else {
            EmptyStateView(
                title: "No games today for your teams",
                hint: "Pick leagues and teams in Settings → Sports."
            )
        }
    }

    private func teamRow(_ team: String, _ score: Int) -> some View {
        HStack {
            Text(team).font(.ui(12)).foregroundStyle(Tokens.textSecondary).lineLimit(1)
            Spacer()
            Text("\(score)").font(.mono(22, .medium)).foregroundStyle(.white)
        }
    }
}
