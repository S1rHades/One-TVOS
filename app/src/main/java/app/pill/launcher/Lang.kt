package app.pill.launcher

import java.util.Locale

/** The languages the launcher speaks. English is what the code is written in; the others are in Translations.kt. */
enum class Language(val tag: String, val ownName: String) {
    English("en", "English"),
    French("fr", "Français"),
    Arabic("ar", "العربية"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)
    val rightToLeft: Boolean get() = this == Arabic

    companion object {
        /** The launcher's language for a TV set to [locale]: that language when the launcher speaks it, else English. */
        fun of(locale: Locale): Language = entries.firstOrNull { it.tag == locale.language } ?: English

        /** A saved choice ("fr"), or null for "follow the TV". */
        fun ofTag(tag: String?): Language? = entries.firstOrNull { it.tag == tag }
    }
}

/** The language on screen now. Set as the launcher's screen starts (see MainActivity), before anything is drawn. */
object Lang {
    @Volatile var current: Language = Language.English

    /**
     * The locale to format dates and numbers with for [language] on a TV set to [system]:
     * the TV's own when it is in that language (its region's month names and week), else
     * the language as written in Algeria for Arabic, plain for the others. Always with
     * Western digits, as Algeria writes them and as the launcher's own formats expect.
     */
    fun localeFor(language: Language, system: Locale): Locale {
        val base = when {
            system.language == language.tag -> system
            language == Language.Arabic -> Locale("ar", "DZ")
            else -> language.locale
        }
        return Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build()
    }
}

/**
 * [text] kept in its left-to-right order inside right-to-left text: a score ("2–1") or
 * a line of Latin names and numbers, which would otherwise have its numbers swapped
 * round. Unchanged where the language itself runs left to right.
 */
fun ltr(text: String): String = if (Lang.current.rightToLeft) "\u2066$text\u2069" else text

/** [step] along a row as the eye meets it: Right is +1 where text runs left to right, and -1 where it runs right to left. */
fun rowStep(step: Int): Int = if (Lang.current.rightToLeft) -step else step

/** [text] (written in English, as throughout the code) in the language on screen; English where a translation is missing. */
fun tr(text: String): String = when (Lang.current) {
    Language.English -> text
    Language.French -> FrenchText[text] ?: text
    Language.Arabic -> ArabicText[text] ?: text
}

/**
 * [text] with [args] in its numbered places ("%1$s of %2$s done"), translated first: a
 * translation may put the places in another order. A translation whose places don't fit
 * the arguments falls back to the English, so a slip in a translation never crashes.
 */
fun tr(text: String, vararg args: Any?): String {
    val translated = tr(text)
    return runCatching { translated.format(Locale.ROOT, *args) }.getOrElse { text.format(Locale.ROOT, *args) }
}
