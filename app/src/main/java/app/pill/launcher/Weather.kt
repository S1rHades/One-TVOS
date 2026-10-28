package app.pill.launcher

import org.json.JSONObject
import java.io.IOException
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
    /** Where the reading is for, also used for sunrise and sunset. */
    val lat: Double = Double.NaN,
    val lon: Double = Double.NaN,
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
    val place = geo.optString("city")
    // Some networks cannot reach Open-Meteo at all; MET Norway covers the whole world too
    runCatching { openMeteo(lat, lon, place).copy(lat = lat, lon = lon) }.getOrElse { first ->
        runCatching { metNorway(lat, lon, place).copy(lat = lat, lon = lon) }.getOrElse { second ->
            // Name both failures so the message in Settings says what each service did
            throw IOException("Open-Meteo: ${shortName(first)}, MET Norway: ${shortName(second)}")
        }
    }
}

/** The root cause's class name, e.g. "SocketTimeoutException". */
private fun shortName(error: Throwable): String = generateSequence(error) { it.cause }.last()::class.java.simpleName

private fun openMeteo(lat: Double, lon: Double, place: String): Weather {
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
    return Weather(
        tempC = current.getDouble("temperature_2m"),
        highC = daily.getJSONArray("temperature_2m_max").getDouble(0),
        lowC = daily.getJSONArray("temperature_2m_min").getDouble(0),
        code = current.getInt("weather_code"),
        isDay = current.optInt("is_day", 1) == 1,
        place = place,
    )
}

/**
 * MET Norway's free Locationforecast API. It asks for an identifying User-Agent.
 * The high and low are taken over the next 24 hours.
 */
private fun metNorway(lat: Double, lon: Double, place: String): Weather {
    val url = String.format(
        Locale.ROOT,
        "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.3f&lon=%.3f",
        lat, lon,
    )
    val series = JSONObject(httpGet(url, "PillLauncher/1.5 github.com/S1rHades/One-TVOS"))
        .getJSONObject("properties").getJSONArray("timeseries")
    val temps = (0 until minOf(24, series.length())).map {
        series.getJSONObject(it).getJSONObject("data").getJSONObject("instant")
            .getJSONObject("details").getDouble("air_temperature")
    }
    val symbol = series.getJSONObject(0).getJSONObject("data")
        .optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
        ?: "cloudy"
    return Weather(
        tempC = temps.first(),
        highC = temps.max(),
        lowC = temps.min(),
        code = wmoCodeFor(symbol),
        isDay = !symbol.endsWith("_night"),
        place = place,
    )
}

/** Maps a MET Norway symbol (e.g. "lightrainshowers_day") to the nearest WMO code. */
private fun wmoCodeFor(symbol: String): Int {
    val s = symbol.substringBefore('_')
    return when {
        "thunder" in s -> 95
        "snow" in s || "sleet" in s -> if ("heavy" in s) 75 else 71
        "rain" in s -> when {
            "heavy" in s -> 65
            "light" in s -> 51
            else -> 61
        }
        s == "fog" -> 45
        s == "clearsky" -> 0
        s == "fair" -> 1
        s == "partlycloudy" -> 2
        else -> 3
    }
}

internal fun httpGet(url: String, userAgent: String? = null): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 8000
    connection.readTimeout = 8000
    userAgent?.let { connection.setRequestProperty("User-Agent", it) }
    return try {
        check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
