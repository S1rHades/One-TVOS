package app.pill.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Current conditions. Temperatures are in °C; [WeatherMode] decides how they are shown. */
data class Weather(
    val tempC: Double,
    val highC: Double,
    val lowC: Double,
    /** WMO weather interpretation code, as returned by Open-Meteo. */
    val code: Int,
    val isDay: Boolean,
    val place: String,
)

enum class Sky { Clear, PartlyCloudy, Cloudy, Fog, Rain, Snow, Storm }

fun skyFor(code: Int): Sky = when (code) {
    0, 1 -> Sky.Clear
    2 -> Sky.PartlyCloudy
    3 -> Sky.Cloudy
    45, 48 -> Sky.Fog
    in 51..67, in 80..82 -> Sky.Rain
    in 71..77, 85, 86 -> Sky.Snow
    in 95..99 -> Sky.Storm
    else -> Sky.Cloudy
}

fun describe(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 66, 80, 81 -> "Rain"
    65, 67, 82 -> "Heavy rain"
    71, 73, 77, 85 -> "Snow"
    75, 86 -> "Heavy snow"
    95, 96, 99 -> "Thunderstorms"
    else -> "Cloudy"
}

/** Whole degrees in the chosen unit, e.g. "24°". */
fun formatTemp(celsius: Double, mode: WeatherMode): String {
    val value = if (mode == WeatherMode.Fahrenheit) celsius * 9 / 5 + 32 else celsius
    return "${Math.round(value)}°"
}

/**
 * Fetches the weather for the TV's approximate location, found from its IP address.
 * No API keys: GeoJS for the location and Open-Meteo for the forecast. Fails with the
 * network or parsing error. Call off the main thread.
 */
fun fetchWeather(): Result<Weather> = runCatching {
    val geo = JSONObject(httpGet("https://get.geojs.io/v1/ip/geo.json"))
    val lat = geo.getString("latitude").toDouble()
    val lon = geo.getString("longitude").toDouble()
    val url = String.format(
        Locale.ROOT,
        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1",
        lat, lon,
    )
    val json = JSONObject(httpGet(url))
    val current = json.getJSONObject("current")
    val daily = json.getJSONObject("daily")
    Weather(
        tempC = current.getDouble("temperature_2m"),
        highC = daily.getJSONArray("temperature_2m_max").getDouble(0),
        lowC = daily.getJSONArray("temperature_2m_min").getDouble(0),
        code = current.getInt("weather_code"),
        isDay = current.optInt("is_day", 1) == 1,
        place = geo.optString("city"),
    )
}

private fun httpGet(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 8000
    connection.readTimeout = 8000
    return try {
        check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
