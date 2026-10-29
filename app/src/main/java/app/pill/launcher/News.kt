package app.pill.launcher

import android.content.Context
import android.text.Html
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class NewsSource(val title: String, val feed: String) {
    AlJazeera("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml"),
    RT("RT", "https://www.rt.com/rss/"),
}

/**
 * Topics the headlines are filtered by. Neither source has feeds for these, so a story
 * belongs to a topic when its section or words say so. [keywords] are matched as whole
 * words (a trailing * allows any ending), ignoring case.
 */
enum class NewsTopic(val title: String, val sections: Set<String>, keywords: List<String>) {
    Algeria("Algeria", emptySet(), listOf("algeria*", "algiers", "tebboune", "fennecs", "oran", "constantine", "kabylie")),
    Russia("Russia", setOf("russia"), listOf("russia*", "moscow", "kremlin", "putin", "lavrov", "peskov", "medvedev", "ruble*", "rouble*", "duma")),
    AfricaMiddleEast(
        "Africa & Middle East", setOf("africa"),
        listOf(
            "africa*", "middle east", "maghreb", "sahel", "morocc*", "tunisia*", "libya*", "egypt*", "sudan*", "mali", "niger", "nigeria*",
            "ethiopia*", "somalia*", "kenya*", "congo*", "senegal*", "gaza", "israel*", "palestin*", "lebanon", "lebanese", "syria*",
            "iran*", "iraq*", "yemen*", "saudi*", "qatar*", "emirat*", "jordan*", "turkey", "turkish", "erdogan", "hamas", "hezbollah",
        ),
    ),
    Economy("Economy", setOf("economy", "business"), listOf("econom*", "inflation", "oil", "gas", "opec", "markets", "stocks", "bank*", "trade", "tariff*", "gdp", "currenc*", "crypto*", "bitcoin", "investor*")),
    Sport("Sport", setOf("sport", "sports"), listOf("football", "fifa", "uefa", "afcon", "olympic*", "world cup", "tennis", "formula one", "f1")),
    World(
        "International politics", setOf("politics", "world"),
        listOf(
            "united nations", "un", "security council", "nato", "eu", "european union", "summit", "sanction*", "diplomat*", "embass*",
            "foreign minister", "foreign ministry", "president", "prime minister", "election*", "parliament*", "treaty", "ceasefire", "truce",
            "peace talks", "negotiat*", "war", "invasion", "military", "troops", "government", "coup", "brics", "g7", "g20", "white house",
            "trump", "ukrain*", "zelensky*", "china", "chinese", "beijing", "washington", "tariff*",
        ),
    ),
    ;

    private val pattern = Regex(
        keywords.joinToString("|", prefix = "\\b(?:", postfix = ")") { word ->
            Regex.escape(word.removeSuffix("*")).replace(" ", "\\E\\s+\\Q") + if (word.endsWith("*")) "\\w*" else "\\b"
        },
        RegexOption.IGNORE_CASE,
    )

    fun matches(headline: Headline): Boolean =
        headline.section in sections || pattern.containsMatchIn(headline.title) || pattern.containsMatchIn(headline.summary)

    companion object {
        val Defaults = setOf(Algeria, Russia, World)
    }
}

data class Headline(
    val title: String,
    val link: String,
    val summary: String,
    val published: Instant?,
    val source: NewsSource,
    /** The site section from the link (news, russia, business, sports...), lower case. */
    val section: String?,
)

/** A headline picked for the card, with the topic it was picked for (null: top stories). */
data class PickedHeadline(val headline: Headline, val topic: NewsTopic?)

/** Each source's stories, and why a source couldn't be read (null when fine). */
data class News(
    val headlines: Map<NewsSource, List<Headline>>,
    val errors: Map<NewsSource, String>,
    val fetchedAt: Instant,
)

/**
 * The newest stories in [topics], at most [limit], each labelled with its first matching
 * topic in [NewsTopic] order (the specific ones before International politics). When none
 * match, the newest stories of all, labelled as top stories. Sources take turns at the top.
 */
fun pickHeadlines(news: News, topics: Set<NewsTopic>, limit: Int = 12): List<PickedHeadline> {
    val all = news.headlines.values.flatten()
        .distinctBy { it.title.lowercase() }
        .sortedByDescending { it.published ?: Instant.EPOCH }
    val wanted = NewsTopic.entries.filter { it in topics }
    val picked = all.mapNotNull { h -> wanted.firstOrNull { it.matches(h) }?.let { PickedHeadline(h, it) } }
    return (picked.ifEmpty { all.map { PickedHeadline(it, null) } }).take(limit)
}

/**
 * Headlines from Al Jazeera English and RT English. Al Jazeera comes from its RSS feed, or
 * from the copy on GitHub when the TV can't reach it. RT is only ever read directly: it is
 * blocked in the EU and elsewhere, and is not republished. Each source's last good list is
 * kept for offline. Call off the main thread.
 */
class NewsFeed(context: Context) {
    private val prefs = context.getSharedPreferences("news", Context.MODE_PRIVATE)

    fun cached(): News? {
        val lists = NewsSource.entries.mapNotNull { source ->
            prefs.getString(source.name, null)?.let { runCatching { source to headlinesFromJson(it, source) }.getOrNull() }
        }.toMap()
        if (lists.isEmpty()) return null
        return News(lists, emptyMap(), Instant.ofEpochMilli(prefs.getLong("at", 0)))
    }

    fun fetch(sources: Collection<NewsSource> = NewsSource.entries): News {
        val saved = cached()?.headlines.orEmpty()
        val lists = mutableMapOf<NewsSource, List<Headline>>()
        val errors = mutableMapOf<NewsSource, String>()
        for (source in sources) {
            val result = runCatching { parseRss(httpGet(source.feed, NewsUserAgent), source).also { check(it.isNotEmpty()) { "Empty feed" } } }
                .recoverCatching { direct ->
                    if (source != NewsSource.AlJazeera) throw direct
                    runCatching { headlinesFromJson(httpGet(MirrorBase + "news/aljazeera.json"), source) }
                        .getOrElse { mirror -> throw IOException("Direct: ${describeError(direct)}; GitHub copy: ${describeError(mirror)}") }
                }
            result.onSuccess {
                lists[source] = it
                prefs.edit().putString(source.name, headlinesToJson(it)).putLong("at", System.currentTimeMillis()).apply()
            }
            result.onFailure { error ->
                errors[source] = if (source == NewsSource.RT) "not available on this network (${describeError(error)})" else error.message ?: describeError(error)
                saved[source]?.let { lists[source] = it }
            }
        }
        return News(lists, errors, Instant.now())
    }
}

private const val NewsUserAgent = "Mozilla/5.0 (Linux; Android TV) PillLauncher/2.5"

/**
 * An RSS 2.0 feed's items: title, link, date, and the description as plain text (images,
 * tags and "Read Full Article" tails removed). Stops quietly at broken XML, keeping the
 * items read so far, since feeds sometimes carry a stray character.
 */
fun parseRss(xml: String, source: NewsSource): List<Headline> {
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(xml))
    }
    val items = mutableListOf<Headline>()
    var fields: MutableMap<String, String>? = null
    runCatching {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> fields = mutableMapOf()
                    "title", "link", "description", "pubDate" -> fields?.let { f -> f[parser.name] = parser.nextText() }
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    fields?.let { f -> headline(f, source)?.let(items::add) }
                    fields = null
                }
            }
        }
    }
    return items
}

private fun headline(f: Map<String, String>, source: NewsSource): Headline? {
    val title = plain(f["title"] ?: return null).takeIf { it.isNotBlank() } ?: return null
    val link = f["link"]?.trim().orEmpty()
    return Headline(
        title = title,
        link = link,
        summary = plain(f["description"].orEmpty()).replace(Regex("\\s*Read Full Article at RT\\.com\\s*$"), "").take(400),
        published = f["pubDate"]?.let { runCatching { ZonedDateTime.parse(it.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() },
        source = source,
        section = sectionOf(link),
    )
}

/** The first path segment of an article link: "news", "russia", "business", "sports"... */
fun sectionOf(link: String): String? =
    Regex("^https?://[^/]+/([a-z-]+)/").find(link)?.groupValues?.get(1)

@Suppress("DEPRECATION") // fromHtml(String, Int) needs Android 7; this launcher supports 8.0 too, but the old one is fine
private fun plain(html: String): String {
    val text = Html.fromHtml(html.replace(Regex("<img[^>]*>", RegexOption.IGNORE_CASE), "")).toString()
    // Entities double-escaped in some feeds come through once more
    return Html.fromHtml(text.replace("<", "&lt;")).toString().replace(Regex("\\s+"), " ").replace("￼", "").trim()
}

/** The shape the GitHub copy (and the saved list) uses. */
fun headlinesFromJson(json: String, source: NewsSource): List<Headline> {
    val items: JSONArray = JSONObject(json).getJSONArray("items")
    return (0 until items.length()).map { items.getJSONObject(it) }.map { o ->
        val link = o.optString("link")
        Headline(
            title = o.getString("title"),
            link = link,
            summary = o.optString("summary"),
            published = o.optString("published").takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() },
            source = source,
            section = o.optString("section").takeIf { it.isNotBlank() } ?: sectionOf(link),
        )
    }
}

private fun headlinesToJson(list: List<Headline>): String = JSONObject().put(
    "items",
    JSONArray(
        list.map { h ->
            JSONObject().put("title", h.title).put("link", h.link).put("summary", h.summary)
                .put("published", h.published?.toString() ?: "").put("section", h.section ?: "")
        },
    ),
).toString()
