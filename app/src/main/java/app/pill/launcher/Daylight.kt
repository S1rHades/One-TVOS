package app.pill.launcher

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Today's sunrise and sunset; null in polar day or night. */
data class SunTimes(val sunrise: ZonedDateTime, val sunset: ZonedDateTime)

/**
 * Sunrise and sunset for a place and day, from the standard sunrise equation (the
 * NOAA / Almanac method): accurate to a minute or two, and needs no network.
 */
fun sunTimes(lat: Double, lon: Double, date: LocalDate, zone: ZoneId): SunTimes? {
    val zenith = Math.toRadians(90.833) // the Sun's upper edge, with refraction
    fun event(rising: Boolean): ZonedDateTime? {
        val n = date.dayOfYear
        val lngHour = lon / 15
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sin(Math.toRadians(m)) + 0.020 * sin(Math.toRadians(2 * m)) + 282.634)
        var ra = norm(Math.toDegrees(atan2(0.91764 * sin(Math.toRadians(l)), cos(Math.toRadians(l)))))
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(zenith) - sinDec * sin(Math.toRadians(lat))) / (cosDec * cos(Math.toRadians(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15
        val utcHours = ((h + ra - 0.06571 * t - 6.622 - lngHour) % 24 + 24) % 24
        val utc = date.atStartOfDay(ZoneId.of("UTC")).plusSeconds((utcHours * 3600).toLong())
        // The UTC moment can fall on the neighbouring UTC day; keep it on the local date
        val local = utc.withZoneSameInstant(zone)
        return when {
            local.toLocalDate().isBefore(date) -> local.plusDays(1)
            local.toLocalDate().isAfter(date) -> local.minusDays(1)
            else -> local
        }
    }
    val rise = event(rising = true) ?: return null
    val set = event(rising = false) ?: return null
    return SunTimes(rise, set)
}

private fun norm(degrees: Double) = ((degrees % 360) + 360) % 360

