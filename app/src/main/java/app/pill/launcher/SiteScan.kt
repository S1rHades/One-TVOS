package app.pill.launcher

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/**
 * One thing a site shows: a film, a story, a product. [url] is where its link goes; a card
 * that is a button rather than a link has only [path], where it is on the page, to click it
 * there.
 */
data class SiteCard(
    val title: String,
    val subtitle: String = "",
    val image: String? = null,
    val url: String? = null,
    val path: String? = null,
    val description: String = "",
    /** Its picture's shape, width over height, as the site shows it; 0 when not known. */
    val ratio: Float = 0f,
)

/** One of a row's tabs (Films | Series): what it says, where it is on the page, and whether it is the one showing. */
data class SiteTab(val title: String, val path: String, val on: Boolean)

/**
 * A row of cards under a heading, with the shape the site gives its pictures ([ratio], width
 * over height) and its tabs, when it has some.
 */
data class SiteRow(val title: String, val ratio: Float, val cards: List<SiteCard>, val tabs: List<SiteTab> = emptyList()) {
    /** Posters, taller than wide. */
    val tall: Boolean get() = ratio in 0.1f..0.9f

    /** The shape its cards are drawn in: the site's own, kept to sensible bounds (16:9 when not known). */
    val shape: Float get() = if (ratio <= 0f) 16f / 9f else ratio.coerceIn(0.5f, 4f)
}

/** What a page says about the one thing it is about: from its page facts (schema.org, OpenGraph) and main heading. */
data class SiteDetail(
    val title: String,
    val description: String = "",
    val image: String? = null,
    val facts: List<String> = emptyList(),
    /** The page facts say it is a film, an episode or a video. */
    val media: Boolean = false,
    /** A video, or a video player, is on the page. */
    val video: Boolean = false,
    /** Where its play button is on the page. */
    val play: String? = null,
)

/** A page as the scanner (assets/tvscan.js) read it. */
data class SiteScan(
    val url: String,
    val site: String,
    val nav: List<SiteCard> = emptyList(),
    val heroes: List<SiteCard> = emptyList(),
    val rows: List<SiteRow> = emptyList(),
    val detail: SiteDetail = SiteDetail(""),
    /** The site's search address, with {q} for the words. */
    val search: String? = null,
    /** Without one: the search box to type into, or the button or page that brings it. */
    val searchInput: String? = null,
    val searchButton: String? = null,
    val searchPage: String? = null,
    /** Its sign-in link or button, when no one is signed in. */
    val login: SiteCard? = null,
    val signedIn: Boolean = false,
) {
    /** The site can be searched somehow. */
    val searchable: Boolean get() = search != null || searchInput != null || searchButton != null || searchPage != null

    val cardCount: Int get() = rows.sumOf { it.cards.size } + heroes.size
}

/** How the TV app view shows a page. */
enum class SiteScreenKind {
    /** A banner and rows of cards. */
    Browse,

    /** One thing, with Play and More like this. */
    Details,

    /** Nothing it can draw (sign-in, forms, checkout): the page itself. */
    Page,
}

/**
 * How to show [scan]. The site's first page with a few cards is always rows; a page about
 * one video (its page facts, a player on it, or a play button where there are few cards) is
 * a details screen; a page of rows is rows; anything else is the page itself.
 */
fun SiteScan.kind(start: Boolean): SiteScreenKind {
    val cards = cardCount
    if (start && cards >= 4) return SiteScreenKind.Browse
    val aboutOne = detail.media || detail.video || (detail.play != null && cards < 12)
    return when {
        aboutOne && detail.title.isNotBlank() -> SiteScreenKind.Details
        cards >= 4 && rows.isNotEmpty() -> SiteScreenKind.Browse
        else -> SiteScreenKind.Page
    }
}

/**
 * A search address learnt from a search typed into the site: the page it ended on, with
 * the words swapped for {q}; null when the words aren't in its address.
 */
fun learnSearch(url: String, words: String): String? {
    val w = words.trim()
    if (w.isEmpty()) return null
    val forms = listOf(java.net.URLEncoder.encode(w, "UTF-8"), java.net.URLEncoder.encode(w, "UTF-8").replace("+", "%20"), Uri.encode(w))
    for (f in forms.distinct()) {
        val i = url.indexOf(f)
        if (i >= 0) return url.substring(0, i) + "{q}" + url.substring(i + f.length)
    }
    return null
}

/** Types [words] into the search box at [path] and sends it, as a person would (the site's own script sees the typing and the Enter). */
fun typeScript(path: String, words: String): String {
    val p = JSONObject.quote(path)
    val w = JSONObject.quote(words)
    return "(function(p,w){var e=document.querySelector(p);if(!e)return false;e.focus();" +
        "var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');if(d&&d.set)d.set.call(e,w);else e.value=w;" +
        "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));" +
        "['keydown','keypress','keyup'].forEach(function(t){e.dispatchEvent(new KeyboardEvent(t,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}))});" +
        "if(e.form){try{if(e.form.requestSubmit)e.form.requestSubmit();else e.form.submit()}catch(x){}}return true;})($p,$w)"
}

/** A page's address for a search on the site, from its search [template]. */
fun searchUrl(template: String, words: String): String =
    template.replace("{q}", java.net.URLEncoder.encode(words.trim(), "UTF-8"))

/**
 * The scanner's answer, as it comes from the page (a JSON text, or that text quoted again,
 * as evaluateJavascript hands it over); null when it isn't one.
 */
fun parseSiteScan(raw: String?): SiteScan? = runCatching {
    var text = raw?.trim() ?: return null
    if (text.startsWith("\"")) text = JSONTokener(text).nextValue() as? String ?: return null
    val o = JSONObject(text)
    fun JSONObject.str(k: String) = optString(k, "").takeIf { !isNull(k) }.orEmpty().trim()
    fun JSONObject.strOrNull(k: String) = str(k).takeIf { it.isNotEmpty() }
    fun card(c: JSONObject) = SiteCard(
        title = c.str("title"),
        subtitle = c.str("sub"),
        image = c.strOrNull("image")?.takeIf { it.startsWith("http") },
        url = c.strOrNull("url")?.takeIf { it.startsWith("http") },
        path = c.strOrNull("path"),
        description = c.str("desc"),
        ratio = c.optDouble("ratio", 0.0).toFloat().takeIf { it.isFinite() } ?: 0f,
    )
    fun cards(a: JSONArray?) = (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONObject(it)?.let(::card) }
        .filter { it.url != null || it.path != null }
    val rows = o.optJSONArray("rows").let { a ->
        (0 until (a?.length() ?: 0)).mapNotNull { i ->
            val r = a!!.optJSONObject(i) ?: return@mapNotNull null
            val tabs = r.optJSONArray("tabs").let { t ->
                (0 until (t?.length() ?: 0)).mapNotNull { k -> t!!.optJSONObject(k)?.let { SiteTab(it.str("title"), it.str("path"), it.optBoolean("on")) } }
                    .filter { it.title.isNotEmpty() && it.path.isNotEmpty() }
            }
            val ratio = r.optDouble("ratio", 0.0).toFloat().takeIf { it.isFinite() && it > 0f } ?: if (r.optBoolean("tall")) 2f / 3f else 0f
            SiteRow(r.str("title"), ratio, cards(r.optJSONArray("cards")), if (tabs.size >= 2) tabs else emptyList()).takeIf { it.cards.isNotEmpty() }
        }
    }
    val d = o.optJSONObject("detail") ?: JSONObject()
    val facts = d.optJSONArray("facts").let { a -> (0 until (a?.length() ?: 0)).map { a!!.optString(it).trim() }.filter { it.isNotEmpty() } }
    SiteScan(
        url = o.str("url"),
        site = o.str("site"),
        nav = o.optJSONArray("nav").let { a -> (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONObject(it)?.let(::card) }.filter { it.url != null && it.title.isNotEmpty() } },
        heroes = cards(o.optJSONArray("heroes")),
        rows = rows,
        detail = SiteDetail(
            title = d.str("title"),
            description = d.str("desc"),
            image = d.strOrNull("image")?.takeIf { it.startsWith("http") },
            facts = facts,
            media = d.optBoolean("media"),
            video = d.optBoolean("video"),
            play = d.strOrNull("play"),
        ),
        search = o.strOrNull("search")?.takeIf { it.startsWith("http") && it.contains("{q}") },
        searchInput = o.strOrNull("searchInput"),
        searchButton = o.strOrNull("searchButton"),
        searchPage = o.strOrNull("searchPage")?.takeIf { it.startsWith("http") },
        login = o.optJSONObject("login")?.let(::card)?.takeIf { it.url != null || it.path != null },
        signedIn = o.optBoolean("signedIn"),
    )
}.getOrNull()

/** A script that scrolls the page down and back, a little at a time, so rows and pictures that load on scrolling are there for the scanner. */
const val PrimeScript = "(function(){var y=0,h=document.documentElement.scrollHeight,s=innerHeight*0.9,n=0;" +
    "function step(){y+=s;n++;window.scrollTo(0,y);if(y<h&&n<14)setTimeout(step,120);else setTimeout(function(){window.scrollTo(0,0)},150);}step();})()"

/** The scanner, read once from the app's files. */
fun scanScript(context: Context): String = context.assets.open("tvscan.js").bufferedReader().use { it.readText() }

/** A shortcut's first page as last read, so its TV app opens at once next time. */
class SiteScans(context: Context) {
    private val dir = File(context.filesDir, "sitescans")

    fun load(id: String): SiteScan? = runCatching { parseSiteScan(File(dir, "$id.json").readText()) }.getOrNull()

    fun save(id: String, json: String) {
        runCatching {
            dir.mkdirs()
            val text = if (json.trim().startsWith("\"")) JSONTokener(json).nextValue() as String else json
            File(dir, "$id.json").writeText(text)
        }
    }

    fun forget(id: String) {
        File(dir, "$id.json").delete()
    }
}

/** Finds the element at [path] (from the scanner), scrolls it into view and says where its middle is, in the page's pixels at its scale, or null. */
fun locateScript(path: String): String {
    val p = JSONObject.quote(path)
    return "(function(){var e=document.querySelector($p);if(!e)return null;e.scrollIntoView({block:'center',inline:'center'});" +
        "var r=e.getBoundingClientRect(),k=(window.visualViewport&&visualViewport.scale)||1;return JSON.stringify({x:(r.left+r.width/2)*k,y:(r.top+r.height/2)*k});})()"
}
