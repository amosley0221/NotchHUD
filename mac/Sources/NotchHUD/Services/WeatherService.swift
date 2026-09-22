import CoreLocation
import Foundation

/// Open-Meteo rather than WeatherKit: WeatherKit needs a paid-developer
/// entitlement and a provisioned bundle id, which an open-source, self-built app
/// cannot assume. Same fields, no key.
@MainActor
final class WeatherService: NSObject, ObservableObject {

    static let shared = WeatherService()

    private let locationManager = CLLocationManager()
    private var task: Task<Void, Never>?

    override private init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyReduced
    }

    func start() {
        requestLocationIfNeeded()
        task?.cancel()
        task = Task { [weak self] in
            while !Task.isCancelled {
                await self?.refresh()
                try? await Task.sleep(for: .seconds(15 * 60))
            }
        }
    }

    func requestLocationIfNeeded() {
        guard Settings.shared.isEnabled(.weather) else { return }
        if !Settings.shared.hasLocation {
            locationManager.requestWhenInUseAuthorization()
            locationManager.requestLocation()
        }
    }

    func refresh() async {
        let settings = Settings.shared
        guard settings.isEnabled(.weather), settings.hasLocation else {
            HUDState.shared.weather = nil
            return
        }
        HUDState.shared.weather = await fetch(
            lat: settings.weatherLat,
            lon: settings.weatherLon,
            city: settings.weatherCity
        )
    }

    func geocode(_ query: String) async -> (lat: Double, lon: Double, name: String)? {
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://geocoding-api.open-meteo.com/v1/search?count=1&name=\(encoded)"),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let results = root["results"] as? [[String: Any]],
              let first = results.first,
              let lat = first["latitude"] as? Double,
              let lon = first["longitude"] as? Double,
              let name = first["name"] as? String
        else { return nil }
        return (lat, lon, name)
    }

    private func fetch(lat: Double, lon: Double, city: String) async -> WeatherSnapshot? {
        let endpoint = """
        https://api.open-meteo.com/v1/forecast?latitude=\(lat)&longitude=\(lon)\
        &current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code\
        &hourly=temperature_2m,precipitation_probability,weather_code\
        &daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2
        """
        guard let url = URL(string: endpoint),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let current = root["current"] as? [String: Any],
              let daily = root["daily"] as? [String: Any],
              let hourly = root["hourly"] as? [String: Any],
              let temp = current["temperature_2m"] as? Double,
              let code = current["weather_code"] as? Int
        else { return nil }

        let times = hourly["time"] as? [String] ?? []
        let temps = hourly["temperature_2m"] as? [Double] ?? []
        let precip = hourly["precipitation_probability"] as? [Int] ?? []
        let codes = hourly["weather_code"] as? [Int] ?? []

        // Start the strip at the next whole hour, not at midnight.
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:00"
        let nowKey = formatter.string(from: Date())
        let start = times.firstIndex { $0 >= nowKey } ?? 0

        let points: [HourPoint] = (start..<min(start + 6, times.count)).compactMap { index in
            guard index < temps.count, index < codes.count else { return nil }
            let hourPart = times[index].split(separator: "T").last.map(String.init) ?? ""
            return HourPoint(
                hour: prettyHour(hourPart),
                tempC: temps[index],
                symbol: Self.symbol(for: codes[index]),
                precipChance: index < precip.count ? precip[index] : 0
            )
        }

        return WeatherSnapshot(
            city: city.isEmpty ? String(format: "%.2f, %.2f", lat, lon) : city,
            tempC: temp,
            feelsLikeC: current["apparent_temperature"] as? Double ?? temp,
            hiC: (daily["temperature_2m_max"] as? [Double])?.first ?? temp,
            loC: (daily["temperature_2m_min"] as? [Double])?.first ?? temp,
            condition: Self.condition(for: code),
            symbol: Self.symbol(for: code),
            humidity: current["relative_humidity_2m"] as? Int ?? 0,
            windKph: current["wind_speed_10m"] as? Double ?? 0,
            alert: nil,
            hourly: points
        )
    }

    private func prettyHour(_ hhmm: String) -> String {
        guard let hour = Int(hhmm.split(separator: ":").first.map(String.init) ?? "") else { return hhmm }
        let suffix = hour < 12 ? "AM" : "PM"
        let display = hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour)
        return "\(display)\(suffix)"
    }

    /// WMO codes → SF Symbols.
    static func symbol(for code: Int) -> String {
        switch code {
        case 0: "sun.max"
        case 1: "sun.max"
        case 2: "cloud.sun"
        case 3: "cloud"
        case 45, 48: "cloud.fog"
        case 51...57: "cloud.drizzle"
        case 61...67: "cloud.rain"
        case 71...77: "cloud.snow"
        case 80...82: "cloud.heavyrain"
        case 85, 86: "cloud.snow"
        case 95...99: "cloud.bolt.rain"
        default: "cloud"
        }
    }

    static func condition(for code: Int) -> String {
        switch code {
        case 0: "Clear"
        case 1: "Mostly clear"
        case 2: "Partly cloudy"
        case 3: "Overcast"
        case 45, 48: "Fog"
        case 51...57: "Drizzle"
        case 61...67: "Rain"
        case 71...77: "Snow"
        case 80...82: "Showers"
        case 85, 86: "Snow showers"
        case 95...99: "Thunderstorm"
        default: "—"
        }
    }
}

extension WeatherService: CLLocationManagerDelegate {
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor in
            let settings = Settings.shared
            settings.weatherLat = location.coordinate.latitude
            settings.weatherLon = location.coordinate.longitude
            if settings.weatherCity.isEmpty {
                let placemarks = try? await CLGeocoder().reverseGeocodeLocation(location)
                settings.weatherCity = placemarks?.first?.locality ?? ""
            }
            await self.refresh()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        NSLog("NotchHUD: location failed — \(error.localizedDescription)")
    }
}
