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
    Stack("Widget stack", "Several widgets in one place, taking turns"),
}

/** The header has room for this many cards beside the buttons. */
const val MaxWidgets = 4

val DefaultWidgets = listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Fennecs)

/** What a new widget stack holds, in order. */
val DefaultStack = listOf(WidgetKind.Headlines, WidgetKind.Daylight, WidgetKind.System)

/** Seconds each widget in a stack shows before the next slides up. */
const val StackTurnSec = 8

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

/** A stack's widgets as stored: names in order; unknown names, repeats and the stack itself are dropped. */
fun parseStack(stored: String?): List<WidgetKind>? = stored?.let { text ->
    text.split(",").mapNotNull { name -> WidgetKind.entries.firstOrNull { it.name == name } }
        .filter { it != WidgetKind.Stack }.distinct()
}

/** Every widget on screen, the ones inside a stack included: these are the ones that need data. */
fun LauncherSettings.activeWidgets(): Set<WidgetKind> =
    (widgets - WidgetKind.Stack).toSet() + if (WidgetKind.Stack in widgets) stack else emptyList()

/** What the stack shows: its widgets, minus any that are also on the home screen by themselves. */
fun LauncherSettings.stackShown(): List<WidgetKind> = stack.filter { it !in widgets }

/**
 * [kind] turned on or off on the home screen. A widget inside the stack comes out of it
 * when turned on by itself, so it never shows twice.
 */
fun LauncherSettings.withRowToggled(kind: WidgetKind): LauncherSettings {
    val row = widgets.toggled(kind)
    if (row == widgets) return this
    val on = kind in row
    return copy(widgets = row, stack = if (on && WidgetKind.Stack in row) stack - kind else stack)
}

/** [kind] added to the end of the stack, or taken out. Adding one takes it off the home screen by itself. */
fun LauncherSettings.withStackToggled(kind: WidgetKind): LauncherSettings = when {
    kind == WidgetKind.Stack -> this
    kind in stack -> copy(stack = stack - kind)
    else -> copy(stack = stack + kind, widgets = widgets - kind)
}

/** [kind] moved [by] places in the stack, within its ends. */
fun LauncherSettings.withStackMoved(kind: WidgetKind, by: Int): LauncherSettings {
    val from = stack.indexOf(kind)
    if (from < 0) return this
    val to = (from + by).coerceIn(0, stack.lastIndex)
    if (to == from) return this
    return copy(stack = stack.toMutableList().apply { removeAt(from); add(to, kind) })
}
