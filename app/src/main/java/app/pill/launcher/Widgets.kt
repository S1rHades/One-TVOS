package app.pill.launcher

/** The cards that can sit in the header, in their default order. */
enum class WidgetKind(val title: String, val description: String) {
    Clock("Clock", "Time and date"),
    Weather("Weather", "Conditions for your approximate area"),
    // Stored by name: this one keeps its pre-2.5 name, so saved layouts carry over
    Fennecs("Football", "Algeria and the teams you follow: next match, latest result, match day"),
    Daylight("Sunrise & sunset", "Today's daylight, with the sun's place in it"),
    System("System info", "Connection and free storage"),
    Headlines("Headlines", "Al Jazeera English and RT, by topic"),
}

/** The header has room for this many cards beside the buttons. */
const val MaxWidgets = 4

val DefaultWidgets = listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Fennecs)

/** Stored as names in display order; unknown names are dropped and the limit applied. */
fun parseWidgets(stored: String?): List<WidgetKind>? = stored?.let { text ->
    text.split(",").mapNotNull { name -> WidgetKind.entries.firstOrNull { it.name == name } }.distinct().take(MaxWidgets)
}

fun List<WidgetKind>.toStored(): String = joinToString(",") { it.name }

/** [kind] turned on (at the end) or off. Turning one on past the limit changes nothing. */
fun List<WidgetKind>.toggled(kind: WidgetKind): List<WidgetKind> = when {
    kind in this -> this - kind
    size >= MaxWidgets -> this
    else -> this + kind
}

/** [kind] moved [by] places along the header, within its ends. */
fun List<WidgetKind>.moved(kind: WidgetKind, by: Int): List<WidgetKind> {
    val from = indexOf(kind)
    if (from < 0) return this
    val to = (from + by).coerceIn(0, lastIndex)
    if (to == from) return this
    return toMutableList().apply { removeAt(from); add(to, kind) }
}
