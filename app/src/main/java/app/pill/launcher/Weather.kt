package app.pill.launcher

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
    /** The forecast behind the card, for the forecast page; null when there is none. */
    val forecast: Forecast? = null,
)

/**
 * The next hours and days, with the details the card has no room for. Fields a service
 * doesn't give are null (MET Norway has no "feels like", UV or chance of rain; it gives
 * the expected rain in mm instead).
 */
data class Forecast(
    val feelsLikeC: Double?,
    val humidity: Int?,
    val windKmh: Double?,
    /** Where the wind comes from, in degrees from north. */
    val windFromDeg: Double?,
    val uvIndex: Double?,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
    val source: String,
    val fetchedAt: Instant,
)

data class HourForecast(val time: LocalDateTime, val tempC: Double, val code: Int, val isDay: Boolean, val rainChance: Int?, val rainMm: Double?)

data class DayForecast(val date: LocalDate, val code: Int, val highC: Double, val lowC: Double, val rainChance: Int?, val rainMm: Double?)

/** "NW", "E" and so on: where the wind blows from. */
fun compassPoint(degrees: Double): String =
    listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((degrees % 360) + 360) % 360 / 45 + 0.5).toInt() % 8]

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
 * A place chosen for the weather instead of the TV's approximate location: a town found
 * by name, or coordinates typed in. The forecast is for its exact point, so a small town
 * gets its own weather rather than the nearest city's.
 */
data class Place(val name: String, val region: String, val country: String, val lat: Double, val lon: Double) {
    /** "Beni Saf, Aïn Témouchent, Algeria". */
    val label: String get() = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")

    /** Which place a saved reading is for. */
    val key: String get() = String.format(Locale.ROOT, "%.4f,%.4f", lat, lon)
}

fun placeToJson(place: Place?): String? = place?.let {
    JSONObject().put("name", it.name).put("region", it.region).put("country", it.country).put("lat", it.lat).put("lon", it.lon).toString()
}

fun placeFromJson(text: String?): Place? = runCatching {
    val o = JSONObject(text ?: return null)
    Place(o.optString("name"), o.optString("region"), o.optString("country"), o.getDouble("lat"), o.getDouble("lon"))
        .takeIf { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
}.getOrNull()

/**
 * Open-Meteo's place search (GeoNames underneath, down to small towns and villages). An
 * answer with no "results" means nothing matched.
 */
fun parsePlaces(body: String): List<Place> {
    val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { i ->
        val r = results.optJSONObject(i) ?: return@mapNotNull null
        val lat = r.optDouble("latitude").takeUnless { it.isNaN() } ?: return@mapNotNull null
        val lon = r.optDouble("longitude").takeUnless { it.isNaN() } ?: return@mapNotNull null
        Place(r.optString("name"), r.optString("admin1"), r.optString("country"), lat, lon).takeIf { it.name.isNotBlank() }
    }
}

/** "35.30, -1.38" (latitude, longitude) typed in as a place of its own; null for anything else. */
fun coordinatesPlace(text: String): Place? {
    val m = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*[, ]\s*(-?\d{1,3}(?:\.\d+)?)\s*$""").find(text) ?: return null
    val lat = m.groupValues[1].toDouble()
    val lon = m.groupValues[2].toDouble()
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return Place(String.format(Locale.ROOT, "%.3f, %.3f", lat, lon), "", "", lat, lon)
}

/**
 * Towns matching [query], best first. Open-Meteo's place search first, as typed and then
 * without accents or with its hyphens and spaces swapped ("Béni-Saf", "beni saf"); if that
 * finds nothing, OpenStreetMap's (Nominatim), which knows small towns everywhere. Call off
 * the main thread; throws only when neither search can be reached.
 */
fun searchPlaces(query: String): List<Place> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    coordinatesPlace(q)?.let { return listOf(it) }
    val spellings = listOf(q, searchKey(q).trim(), q.replace(' ', '-'), q.replace('-', ' ')).filter { it.length >= 2 }.distinct()
    var problem: Throwable? = null
    for (name in spellings) {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&format=json&name=" + URLEncoder.encode(name, "UTF-8")
        val found = runCatching { parsePlaces(httpGet(url)) }.onFailure { problem = it }.getOrNull().orEmpty()
        if (found.isNotEmpty()) return found
    }
    val osm = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=8&accept-language=en&q=" + URLEncoder.encode(q, "UTF-8")
    return runCatching { parseOsmPlaces(httpGet(osm, OsmUserAgent)) }
        .getOrElse { e -> problem?.let { throw it } ?: throw e }
}

/** Nominatim asks every app to name itself. */
private const val OsmUserAgent = "PillLauncher (Android TV launcher; https://github.com/S1rHades/One-TVOS)"

/** Nominatim's answer: towns, villages and cities, with their region and country. */
fun parseOsmPlaces(body: String): List<Place> {
    val results = org.json.JSONArray(body)
    return (0 until results.length()).mapNotNull { i ->
        val r = results.optJSONObject(i) ?: return@mapNotNull null
        val lat = r.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
        val lon = r.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
        val address = r.optJSONObject("address")
        val name = r.optString("name").ifBlank {
            listOf("town", "city", "village", "municipality").firstNotNullOfOrNull { k -> address?.optString(k)?.takeIf { it.isNotBlank() } }.orEmpty()
        }
        Place(name, address?.optString("state").orEmpty(), address?.optString("country").orEmpty(), lat, lon).takeIf { it.name.isNotBlank() }
    }.distinctBy { it.label }
}

/**
 * Fetches the weather for a chosen [Place], or else for the TV's approximate location,
 * found from its IP address.
 * No API keys: GeoJS for the location and Open-Meteo for the forecast, or MET Norway when
 * Open-Meteo can't be reached. The answer is saved, so the card and the forecast page have
 * the last reading at once after a restart or while offline. Call off the main thread.
 */
class WeatherSource(context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    /** The last reading for [place] (null: the approximate location), re-read from the saved answer; marked by its fetch time. */
    fun cached(place: Place? = null): Weather? = runCatching {
        // A reading for somewhere else isn't shown: "Finding your area…" until the new one comes
        if (prefs.getString("for", Automatic) != (place?.key ?: Automatic)) return null
        val body = prefs.getString("body", null) ?: return null
        val at = Instant.ofEpochMilli(prefs.getLong("at", 0))
        val place = prefs.getString("place", "").orEmpty()
        val lat = prefs.getFloat("lat", Float.NaN).toDouble()
        val lon = prefs.getFloat("lon", Float.NaN).toDouble()
        when (prefs.getString("service", null)) {
            OpenMeteo -> parseOpenMeteo(body, place, lat, lon, at)
            MetNorway -> parseMetNorway(body, place, lat, lon, at, ZoneId.systemDefault())
            else -> null
        }
    }.getOrNull()

    fun fetch(chosen: Place? = null): Result<Weather> = runCatching {
        val (lat, lon, place) = if (chosen != null) {
            Triple(chosen.lat, chosen.lon, chosen.name)
        } else {
            val geo = JSONObject(httpGet("https://get.geojs.io/v1/ip/geo.json"))
            Triple(geo.getString("latitude").toDouble(), geo.getString("longitude").toDouble(), geo.optString("city"))
        }
        val now = Instant.now()
        // Some networks cannot reach Open-Meteo at all; MET Norway covers the whole world too
        val (service, body) = runCatching { OpenMeteo to httpGet(openMeteoUrl(lat, lon)) }.getOrElse { first ->
            runCatching { MetNorway to httpGet(metNorwayUrl(lat, lon), MetUserAgent) }.getOrElse { second ->
                // Name both failures so the message in Settings says what each service did
                throw IOException("Open-Meteo: ${shortName(first)}, MET Norway: ${shortName(second)}")
            }
        }
        val weather = when (service) {
            OpenMeteo -> parseOpenMeteo(body, place, lat, lon, now)
            else -> parseMetNorway(body, place, lat, lon, now, ZoneId.systemDefault())
        }
        prefs.edit()
            .putString("service", service).putString("body", body).putLong("at", now.toEpochMilli())
            .putString("place", place).putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat())
            .putString("for", chosen?.key ?: Automatic)
            .apply()
        weather
    }
}

private const val OpenMeteo = "open-meteo"
/** Saved readings for the approximate location (from before places could be chosen, too). */
private const val Automatic = "auto"
private const val MetNorway = "met-norway"
private const val MetUserAgent = "PillLauncher/2.5 github.com/S1rHades/One-TVOS"

/** The root cause's class name, e.g. "SocketTimeoutException". */
private fun shortName(error: Throwable): String = generateSequence(error) { it.cause }.last()::class.java.simpleName

private fun openMeteoUrl(lat: Double, lon: Double) = String.format(
    Locale.ROOT,
    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,uv_index" +
        "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
        "&timezone=auto&forecast_days=7&forecast_hours=24",
    lat, lon,
)

private fun metNorwayUrl(lat: Double, lon: Double) = String.format(
    Locale.ROOT,
    "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.3f&lon=%.3f",
    lat, lon,
)

/** Open-Meteo's answer. Its times are local to the place (timezone=auto). */
fun parseOpenMeteo(body: String, place: String, lat: Double, lon: Double, fetchedAt: Instant): Weather {
    val json = JSONObject(body)
    val current = json.getJSONObject("current")
    val daily = json.getJSONObject("daily")
    fun org.json.JSONArray.doubleAt(i: Int): Double? = if (isNull(i)) null else optDouble(i).takeUnless { it.isNaN() }
    fun org.json.JSONArray.intAt(i: Int): Int? = if (isNull(i)) null else optInt(i)
    val hours = json.optJSONObject("hourly")?.let { h ->
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val codes = h.getJSONArray("weather_code")
        val rain = h.optJSONArray("precipitation_probability")
        val day = h.optJSONArray("is_day")
        (0 until times.length()).mapNotNull { i ->
            HourForecast(
                time = LocalDateTime.parse(times.getString(i)),
                tempC = temps.doubleAt(i) ?: return@mapNotNull null,
                code = codes.intAt(i) ?: 3,
                isDay = day?.intAt(i)?.let { it == 1 } ?: true,
                rainChance = rain?.intAt(i),
                rainMm = null,
            )
        }
    }.orEmpty()
    val days = run {
        val dates = daily.getJSONArray("time")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val codes = daily.optJSONArray("weather_code")
        val rain = daily.optJSONArray("precipitation_probability_max")
        (0 until dates.length()).mapNotNull { i ->
            DayForecast(
                date = LocalDate.parse(dates.getString(i)),
                code = codes?.intAt(i) ?: 3,
                highC = highs.doubleAt(i) ?: return@mapNotNull null,
                lowC = lows.doubleAt(i) ?: return@mapNotNull null,
                rainChance = rain?.intAt(i),
                rainMm = null,
            )
        }
    }
    fun currentDouble(key: String) = current.optDouble(key).takeUnless { it.isNaN() }
    return Weather(
        tempC = current.getDouble("temperature_2m"),
        highC = days.firstOrNull()?.highC ?: current.getDouble("temperature_2m"),
        lowC = days.firstOrNull()?.lowC ?: current.getDouble("temperature_2m"),
        code = current.getInt("weather_code"),
        isDay = current.optInt("is_day", 1) == 1,
        place = place,
        lat = lat,
        lon = lon,
        forecast = Forecast(
            feelsLikeC = currentDouble("apparent_temperature"),
            humidity = currentDouble("relative_humidity_2m")?.let { Math.round(it).toInt() },
            windKmh = currentDouble("wind_speed_10m"),
            windFromDeg = currentDouble("wind_direction_10m"),
            uvIndex = currentDouble("uv_index"),
            hours = hours,
            days = days,
            source = "Open-Meteo",
            fetchedAt = fetchedAt,
        ),
    )
}

/**
 * MET Norway's free Locationforecast API (it asks for an identifying User-Agent). Its
 * times are UTC: hourly for about two days, then every 6 hours. The card's high and low
 * are taken over the next 24 hours; days are grouped in [zone].
 */
fun parseMetNorway(body: String, place: String, lat: Double, lon: Double, fetchedAt: Instant, zone: ZoneId): Weather {
    val series = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
    class Step(val at: Instant, val temp: Double, val humidity: Double?, val windMs: Double?, val windFrom: Double?, val symbol: String?, val longSymbol: String?, val rainMm: Double?)
    val steps = (0 until series.length()).mapNotNull { i ->
        val entry = series.getJSONObject(i)
        val data = entry.getJSONObject("data")
        val details = data.getJSONObject("instant").getJSONObject("details")
        val oneHour = data.optJSONObject("next_1_hours")
        val sixHours = data.optJSONObject("next_6_hours")
        val at = Instant.parse(entry.getString("time"))
        val next = if (i + 1 < series.length()) Instant.parse(series.getJSONObject(i + 1).getString("time")) else null
        // Rain over the gap to the next step, so the hourly and 6-hourly parts add up without overlap
        val hourly = next != null && Duration.between(at, next) <= Duration.ofHours(1)
        Step(
            at = at,
            temp = details.optDouble("air_temperature").takeUnless { it.isNaN() } ?: return@mapNotNull null,
            humidity = details.optDouble("relative_humidity").takeUnless { it.isNaN() },
            windMs = details.optDouble("wind_speed").takeUnless { it.isNaN() },
            windFrom = details.optDouble("wind_from_direction").takeUnless { it.isNaN() },
            symbol = (oneHour ?: sixHours)?.optJSONObject("summary")?.optString("symbol_code")?.takeIf { it.isNotBlank() },
            longSymbol = (sixHours ?: oneHour)?.optJSONObject("summary")?.optString("symbol_code")?.takeIf { it.isNotBlank() },
            rainMm = (if (hourly) oneHour else sixHours ?: oneHour)?.optJSONObject("details")?.optDouble("precipitation_amount")?.takeUnless { it.isNaN() },
        )
    }
    check(steps.isNotEmpty()) { "No forecast steps" }
    val first = steps.first()
    val today = steps.filter { Duration.between(first.at, it.at) < Duration.ofHours(24) }
    val symbol = first.symbol ?: "cloudy"
    val hours = steps.filter { Duration.between(first.at, it.at) < Duration.ofHours(24) }.map { step ->
        val local = step.at.atZone(zone).toLocalDateTime()
        HourForecast(local, step.temp, wmoCodeFor(step.symbol ?: "cloudy"), step.symbol?.endsWith("_night") != true, null, step.rainMm)
    }
    val days = steps.groupBy { it.at.atZone(zone).toLocalDate() }.entries.take(7).map { (date, day) ->
        // The day's weather is the 6-hour outlook from around midday, else the first one there is
        val noon = day.minByOrNull { Math.abs(it.at.atZone(zone).hour - 12) }
        DayForecast(
            date = date,
            code = wmoCodeFor(noon?.longSymbol ?: day.firstNotNullOfOrNull { it.longSymbol } ?: "cloudy"),
            highC = day.maxOf { it.temp },
            lowC = day.minOf { it.temp },
            rainChance = null,
            rainMm = day.mapNotNull { it.rainMm }.sum(),
        )
    }
    return Weather(
        tempC = first.temp,
        highC = today.maxOf { it.temp },
        lowC = today.minOf { it.temp },
        code = wmoCodeFor(symbol),
        isDay = !symbol.endsWith("_night"),
        place = place,
        lat = lat,
        lon = lon,
        forecast = Forecast(
            feelsLikeC = null,
            humidity = first.humidity?.let { Math.round(it).toInt() },
            windKmh = first.windMs?.let { it * 3.6 },
            windFromDeg = first.windFrom,
            uvIndex = null,
            hours = hours,
            days = days,
            source = "MET Norway",
            fetchedAt = fetchedAt,
        ),
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
