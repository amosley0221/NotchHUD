package com.notchhud.island.data

import com.notchhud.island.core.HourlyPoint
import com.notchhud.island.core.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Open-Meteo rather than WeatherKit: no developer-account entitlement, no API key,
 * and the same fields the spec needs (current, hi/lo, humidity, wind, 6-hour strip).
 * Returns null when there is no location or the fetch fails — the UI then says
 * "Set a location" instead of inventing a forecast.
 */
class WeatherRepository {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(lat: Double, lon: Double, city: String): Weather? = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=$lat&longitude=$lon")
            append("&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code")
            append("&hourly=temperature_2m,precipitation_probability,weather_code")
            append("&daily=temperature_2m_max,temperature_2m_min")
            append("&timezone=auto&forecast_days=2")
        }
        val body = Http.getString(url) ?: return@withContext null

        runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val current = root["current"]!!.jsonObject
            val daily = root["daily"]!!.jsonObject
            val hourly = root["hourly"]!!.jsonObject

            val code = current["weather_code"]!!.jsonPrimitive.content.toInt()
            val times = hourly["time"]!!.jsonArray.map { it.jsonPrimitive.content }
            val temps = hourly["temperature_2m"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() }
            val precip = hourly["precipitation_probability"]!!.jsonArray.map { it.jsonPrimitive.content.toIntOrNull() ?: 0 }
            val codes = hourly["weather_code"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }

            // Start the strip at the next whole hour rather than at midnight.
            val nowIso = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"))
            val start = times.indexOfFirst { it >= nowIso }.coerceAtLeast(0)

            val points = (start until minOf(start + 6, times.size)).map { i ->
                HourlyPoint(
                    hour = times[i].substringAfter("T").substringBeforeLast(":").let { formatHour(it) },
                    tempC = temps[i],
                    symbol = symbolFor(codes[i]),
                    precipChance = precip.getOrElse(i) { 0 },
                )
            }

            Weather(
                city = city,
                tempC = current["temperature_2m"]!!.jsonPrimitive.content.toDouble(),
                feelsLikeC = current["apparent_temperature"]!!.jsonPrimitive.content.toDouble(),
                hiC = daily["temperature_2m_max"]!!.jsonArray[0].jsonPrimitive.content.toDouble(),
                loC = daily["temperature_2m_min"]!!.jsonArray[0].jsonPrimitive.content.toDouble(),
                condition = conditionFor(code),
                symbol = symbolFor(code),
                humidity = current["relative_humidity_2m"]!!.jsonPrimitive.content.toInt(),
                windKph = current["wind_speed_10m"]!!.jsonPrimitive.content.toDouble(),
                hourly = points,
            )
        }.getOrNull()
    }

    suspend fun geocode(query: String): Triple<Double, Double, String>? = withContext(Dispatchers.IO) {
        val body = Http.getString(
            "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" +
                java.net.URLEncoder.encode(query, "UTF-8")
        ) ?: return@withContext null
        runCatching {
            val r = json.parseToJsonElement(body).jsonObject["results"]!!.jsonArray[0].jsonObject
            Triple(
                r["latitude"]!!.jsonPrimitive.content.toDouble(),
                r["longitude"]!!.jsonPrimitive.content.toDouble(),
                r["name"]!!.jsonPrimitive.content,
            )
        }.getOrNull()
    }

    private fun formatHour(hhmm: String): String {
        val h = hhmm.substringBefore(":").toIntOrNull() ?: return hhmm
        val suffix = if (h < 12) "AM" else "PM"
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return "$h12$suffix"
    }

    /** WMO weather codes → a condition string and a glyph. */
    private fun conditionFor(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "—"
    }

    private fun symbolFor(code: Int): String = when (code) {
        0, 1 -> "☀"
        2 -> "⛅"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..57 -> "🌦"
        in 61..67 -> "🌧"
        in 71..77 -> "❄"
        in 80..82 -> "🌧"
        85, 86 -> "🌨"
        in 95..99 -> "⛈"
        else -> "☁"
    }
}

fun Double.toDisplayTemp(celsius: Boolean): Int =
    if (celsius) Math.round(this).toInt() else Math.round(this * 9 / 5 + 32).toInt()
