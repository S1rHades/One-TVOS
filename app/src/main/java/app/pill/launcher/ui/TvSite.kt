package app.pill.launcher.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.SiteCard
import app.pill.launcher.SiteRow
import app.pill.launcher.SiteScan
import app.pill.launcher.SiteScreenKind
import app.pill.launcher.kind
import app.pill.launcher.tr
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** What the TV app view asks of the hidden page it draws. */
interface SiteActions {
    /** Opens [card] on the hidden page: its link, or a click on it. */
    fun open(card: SiteCard)

    /** Opens a page of the site's menu, as the new first screen. */
    fun tab(card: SiteCard)

    /** The hidden page goes back to [screen]'s page. */
    fun backTo(screen: SiteScreen)

    /** Starts the video of [screen], in the launcher's player. */
    fun play(screen: SiteScreen)

    /** Shows the page itself, to steer with the pointer; [pop] closes this screen when Back comes back from it. */
    fun showPage(pop: Boolean)

    fun reload()
    fun leave()
}

/** One screen of the TV app view: a page of the site, as rows or as details, or still loading ([kind] null). */
class SiteScreen(val url: String?, val preview: SiteCard?, val autoPlay: Boolean = false, val first: Boolean = false) {
    var scan by mutableStateOf<SiteScan?>(null)
    var kind by mutableStateOf<SiteScreenKind?>(null)

    /** The focused line: on rows, the menu (-2), the banner (-1) or a row (0…); on details, the buttons (-1) or a row. */
    var line by mutableIntStateOf(-1)
    /** The focused card in each row. */
    val cols = mutableStateMapOf<Int, Int>()
    /** The focused banner, and its button (0 Play, 1 More info); the focused details button (0 Play, 1 Open as web page). */
    var hero by mutableIntStateOf(0)
    var button by mutableIntStateOf(0)

    val rows: List<SiteRow> get() = scan?.rows.orEmpty()
    fun col(row: Int) = cols[row] ?: 0
}

/**
 * The remote's side of a website shown as a TV app: screens the launcher draws from what the
 * hidden page holds (rows of cards, a details screen), moved through with the arrows, OK to
 * open, Back to go up a level, and a held OK (or Menu) for the menu.
 */
class TvSite(val name: String, private val actions: SiteActions) {
    val stack = mutableStateListOf<SiteScreen>()

    /** The site's menu (from its first page) and the page of it shown now. */
    var nav by mutableStateOf<List<SiteCard>>(emptyList())
        private set
    var tab by mutableIntStateOf(0)
        private set
    /** The focused menu item, while the menu line is focused. */
    var navFocus by mutableIntStateOf(0)
        private set

    var menuOpen by mutableStateOf(false)
        private set
    var menuItem by mutableIntStateOf(0)
        private set

    /** Waiting for a video to start. */
    var starting by mutableStateOf(false)

    /** Something to tell, shown for a moment (no video found, say). */
    var notice by mutableStateOf<String?>(null)

    private var okDown = false
    private var okHeld = false

    val top: SiteScreen? get() = stack.lastOrNull()

    /** The first screen, shown from the saved page at once when there is one. */
    fun start(url: String, saved: SiteScan?) {
        stack.clear()
        val s = SiteScreen(url, null, first = true)
        stack += s
        if (saved != null) show(s, saved)
    }

    /**
     * A page was read. It fills the screen waiting for it, or freshens the one shown when
     * it is the same page (a site filling in its rows late); else it is left alone.
     */
    fun scanned(scan: SiteScan) {
        val s = top ?: return
        val loading = s.kind == null
        val same = s.scan?.url?.let { sameUrl(it, scan.url) } == true || s.url?.let { sameUrl(it, scan.url) } == true
        // The first screen takes its page wherever the site sent it (a saved screen for "/" whose page is now "/home", say)
        if (!loading && !same && !(s.first && stack.size == 1)) return
        // A late reading of the screen before this one isn't this one's
        if (loading && stack.size > 1 && stack[stack.size - 2].scan?.url?.let { sameUrl(it, scan.url) } == true && s.url != null && !sameUrl(s.url, scan.url)) return
        show(s, scan)
        if (loading) {
            when (s.kind) {
                SiteScreenKind.Page -> actions.showPage(pop = true)
                SiteScreenKind.Details -> if (s.autoPlay) actions.play(s)
                else -> {}
            }
        }
    }

    private fun show(s: SiteScreen, scan: SiteScan) {
        val fresh = s.scan == null
        s.scan = scan
        val k = scan.kind(start = s.first)
        // A screen already showing rows keeps them while its page is between readings
        if (s.kind == SiteScreenKind.Browse && k != SiteScreenKind.Browse && !fresh) return
        s.kind = k
        if (s.first && stack.size == 1 && tab == 0 && scan.nav.isNotEmpty()) nav = scan.nav
        // Focus where it was, inside what there is now
        // A page of the menu keeps focus on the menu; others start on the banner or first row
        if (fresh && !(s.line == -2 && hasMenu(s))) s.line = firstLine(s)
        s.line = s.line.coerceIn(topLine(s), (s.rows.size - 1).coerceAtLeast(topLine(s)))
        if (s.line == -1 && s.kind == SiteScreenKind.Browse && scan.heroes.isEmpty()) s.line = firstLine(s)
        s.hero = s.hero.coerceIn(0, (scan.heroes.size - 1).coerceAtLeast(0))
        for (r in s.rows.indices) s.cols[r] = s.col(r).coerceIn(0, s.rows[r].cards.size - 1)
    }

    private fun hasMenu(s: SiteScreen) = s.first && nav.isNotEmpty()

    /** The line focus starts on: the banner, else the first row; the buttons on details. */
    private fun firstLine(s: SiteScreen): Int = when {
        s.kind == SiteScreenKind.Details -> -1
        s.scan?.heroes?.isNotEmpty() == true -> -1
        s.rows.isNotEmpty() -> 0
        hasMenu(s) -> -2
        else -> -1
    }

    /** The highest line that can be focused. */
    private fun topLine(s: SiteScreen): Int = when {
        s.kind == SiteScreenKind.Details -> -1
        hasMenu(s) -> -2
        s.scan?.heroes?.isNotEmpty() == true -> -1
        else -> 0
    }

    /** Lines in order, from the top. */
    private fun lines(s: SiteScreen): List<Int> {
        val out = ArrayList<Int>()
        if (s.kind == SiteScreenKind.Details) out += -1
        else {
            if (hasMenu(s)) out += -2
            if (s.scan?.heroes?.isNotEmpty() == true) out += -1
        }
        out += s.rows.indices
        return out
    }

    /** The card focus is on, for the spotlight: the banner's or a row's. */
    fun focused(s: SiteScreen): SiteCard? = when {
        s.line >= 0 -> s.rows.getOrNull(s.line)?.cards?.getOrNull(s.col(s.line))
        else -> s.scan?.heroes?.getOrNull(s.hero)
    }

    private fun open(card: SiteCard, autoPlay: Boolean) {
        stack += SiteScreen(card.url, card, autoPlay)
        actions.open(card)
    }

    /** Closes the top screen, back to the one before (or out of the site from the first). */
    fun pop() {
        if (stack.size <= 1) {
            actions.leave()
            return
        }
        stack.removeAt(stack.size - 1)
        starting = false
        top?.let(actions::backTo)
    }

    fun key(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        val code = e.keyCode
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A
        if (down) notice = null
        if (menuOpen) return menuKey(code, down, ok)
        if (code == KeyEvent.KEYCODE_MENU) {
            if (down && e.repeatCount == 0) openMenu()
            return true
        }
        // OK acts when let go; held, it opens the menu
        if (ok) {
            if (down) {
                if (e.repeatCount == 0) {
                    okDown = true
                    okHeld = false
                } else if (okDown && !okHeld && e.repeatCount >= 2) {
                    okHeld = true
                    openMenu()
                }
                return true
            }
            val wasHeld = okHeld
            val wasDown = okDown
            okDown = false
            okHeld = false
            if (!wasHeld && wasDown) press()
            return true
        }
        val s = top ?: return false
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (down) return true
            back(s)
            return true
        }
        if (!down) return code in arrows
        if (starting || s.kind == null) return code in arrows
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                val ls = lines(s)
                val i = ls.indexOf(s.line).coerceAtLeast(0) + if (code == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1
                if (i in ls.indices) {
                    s.line = ls[i]
                    if (s.line == -2) navFocus = tab
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val d = if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                when {
                    s.line == -2 -> navFocus = (navFocus + d).coerceIn(0, nav.size - 1)
                    s.line == -1 && s.kind == SiteScreenKind.Details -> s.button = (s.button + d).coerceIn(0, 1)
                    s.line == -1 -> {
                        val b = s.button + d
                        val heroes = s.scan?.heroes.orEmpty()
                        // Past the buttons' ends: the banner's next (or last) picture
                        when {
                            b in 0..1 -> s.button = b
                            heroes.size > 1 -> {
                                s.hero = (s.hero + d + heroes.size) % heroes.size
                                s.button = 0
                            }
                        }
                    }
                    else -> {
                        val n = s.rows.getOrNull(s.line)?.cards?.size ?: 0
                        s.cols[s.line] = (s.col(s.line) + d).coerceIn(0, (n - 1).coerceAtLeast(0))
                    }
                }
                return true
            }
        }
        return false
    }

    private val arrows = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)

    /** Back goes up one level: from a row to the top of the screen, from the top to the screen before, then out. */
    private fun back(s: SiteScreen) {
        when {
            starting -> starting = false
            s.kind != null && s.line != topLine(s) && lines(s).size > 1 -> {
                s.line = topLine(s)
                if (s.line == -2) navFocus = tab
                s.button = 0
            }
            stack.size == 1 && tab != 0 && nav.isNotEmpty() -> {
                tab = 0
                navFocus = 0
                goTab(nav[0])
            }
            else -> pop()
        }
    }

    private fun goTab(card: SiteCard) {
        stack.clear()
        stack += SiteScreen(card.url, null, first = true).also { it.line = -2 }
        actions.tab(card)
    }

    private fun press() {
        val s = top ?: return
        if (starting || s.kind == null) return
        when (s.kind) {
            SiteScreenKind.Details -> when {
                s.line >= 0 -> focused(s)?.let { open(it, false) }
                s.button == 0 -> actions.play(s)
                else -> actions.showPage(pop = false)
            }
            SiteScreenKind.Browse -> when (s.line) {
                -2 -> nav.getOrNull(navFocus)?.let {
                    if (navFocus != tab) {
                        tab = navFocus
                        goTab(it)
                    }
                }
                -1 -> focused(s)?.let { open(it, autoPlay = s.button == 0) }
                else -> focused(s)?.let { open(it, false) }
            }
            else -> {}
        }
    }

    /** The menu: Open as web page, Reload, Back to the launcher. */
    val menuItems: List<String> get() = listOf(tr("Open as web page"), tr("Reload"), tr("Back to the launcher"))

    private fun openMenu() {
        menuOpen = true
        menuItem = 0
    }

    private fun menuKey(code: Int, down: Boolean, ok: Boolean): Boolean {
        if (ok) {
            // The press that opened it is let go first
            if (!down && !okHeld) {
                menuOpen = false
                when (menuItem) {
                    0 -> actions.showPage(pop = false)
                    1 -> actions.reload()
                    else -> actions.leave()
                }
            }
            if (!down) {
                okHeld = false
                okDown = false
            }
            return true
        }
        if (!down) return true
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> menuItem = (menuItem - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> menuItem = (menuItem + 1).coerceAtMost(menuItems.size - 1)
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MENU -> menuOpen = false
        }
        return true
    }
}

/** Two addresses for the same page: alike but for a trailing slash and the part after '#'. */
fun sameUrl(a: String, b: String): Boolean = a.substringBefore('#').trimEnd('/') == b.substringBefore('#').trimEnd('/')

private val Ink = Color(0xFF0C0E13)
private val Muted = Color(0xFF9AA1B2)
private val Card2 = Color(0xFF1B1F28)

/** The TV app view: the top screen of [site], drawn over the hidden page. */
@Composable
fun TvSiteScreen(site: TvSite, icon: androidx.compose.ui.graphics.ImageBitmap?) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        val s = site.top
        if (s != null) {
            AnimatedContent(s, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "screen") { screen ->
                when (screen.kind) {
                    SiteScreenKind.Browse -> BrowseScreen(site, screen, icon)
                    SiteScreenKind.Details -> DetailsScreen(site, screen)
                    else -> LoadingScreen(site, screen)
                }
            }
        }
        if (site.starting) Starting()
        site.notice?.let { Notice(it, Modifier.align(Alignment.BottomCenter)) }
        if (site.menuOpen) SiteMenu(site)
    }
}

/** A site's picture, asked for as the site's own page would (with it as the page that asked). */
@Composable
private fun SitePicture(url: String?, referer: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (url == null) return
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(250).apply { if (referer != null) addHeader("Referer", referer) }.build(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

/** A big picture behind a screen, faded into the dark on the left and at the bottom. */
@Composable
private fun BoxScope.Backdrop(image: String?, referer: String?, height: Float = 1f) {
    Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(height)) {
        AnimatedContent(image, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) }, label = "backdrop") { img ->
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1D3A63), Color(0xFF0B1525)))))
                SitePicture(img, referer, Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink, 0.45f to Ink.copy(alpha = 0.55f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Ink)))
    }
}

@Composable
private fun BrowseScreen(site: TvSite, s: SiteScreen, icon: androidx.compose.ui.graphics.ImageBitmap?) {
    val scan = s.scan ?: return
    val spot = site.focused(s)
    val referer = scan.url
    Box(Modifier.fillMaxSize()) {
        Backdrop(spot?.image, referer, height = 0.72f)
        Column(Modifier.fillMaxSize()) {
            TopBar(site, s, icon)
            // The spotlight: what focus is on, large
            Column(Modifier.padding(start = 48.dp, top = 14.dp, end = 48.dp).width(420.dp).height(178.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val title = spot?.title?.ifBlank { null } ?: site.name
                Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val more = spot?.description?.ifBlank { null } ?: spot?.subtitle?.ifBlank { null }
                if (more != null) Text(more, color = Color(0xFFCFD3DC), fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (s.line == -1 && scan.heroes.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteButton("▶  " + tr("Play"), s.button == 0)
                        SiteButton(tr("More info"), s.button == 1)
                        if (scan.heroes.size > 1) Dots(scan.heroes.size, s.hero)
                    }
                }
            }
            Rows(s, referer, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TopBar(site: TvSite, s: SiteScreen, icon: androidx.compose.ui.graphics.ImageBitmap?) {
    Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
        if (site.nav.isEmpty() || !s.first) {
            Text(site.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        } else {
            site.nav.forEachIndexed { i, item ->
                val focused = s.line == -2 && site.navFocus == i
                val current = site.tab == i
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (focused) Color.White else Color.Transparent).padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        item.title,
                        color = when {
                            focused -> Ink
                            current -> Color.White
                            else -> Muted
                        },
                        fontSize = 15.sp,
                        fontWeight = if (current || focused) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The rows, the focused one first in view, its focused card grown and ringed. */
@Composable
private fun Rows(s: SiteScreen, referer: String, modifier: Modifier) {
    val rows = s.rows
    val list = rememberLazyListState()
    LaunchedEffect(s.line, rows.size) {
        if (rows.isNotEmpty()) list.animateScrollToItem(s.line.coerceIn(0, rows.size - 1))
    }
    LazyColumn(state = list, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 200.dp), userScrollEnabled = false) {
        itemsIndexed(rows) { r, row ->
            CardRow(row, r, s, referer)
        }
    }
}

@Composable
private fun CardRow(row: SiteRow, r: Int, s: SiteScreen, referer: String) {
    val focusedRow = s.line == r
    val col = s.col(r)
    val state = rememberLazyListState()
    LaunchedEffect(col) { state.animateScrollToItem((col - 1).coerceAtLeast(0)) }
    val w: Dp = if (row.tall) 112.dp else 196.dp
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(
            row.title.ifBlank { if (s.kind == SiteScreenKind.Details) tr("More like this") else tr("More") },
            color = if (focusedRow) Color.White else Muted,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
            maxLines = 1,
        )
        LazyRow(state = state, contentPadding = PaddingValues(start = 48.dp, end = 120.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), userScrollEnabled = false) {
            itemsIndexed(row.cards) { c, card ->
                SiteCardView(card, focusedRow && c == col, row.tall, w, referer)
            }
        }
    }
}

@Composable
private fun SiteCardView(card: SiteCard, focused: Boolean, tall: Boolean, w: Dp, referer: String) {
    val grow by animateFloatAsState(if (focused) 1.08f else 1f, tween(160), label = "grow")
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.padding(vertical = 8.dp, horizontal = 4.dp).width(w).aspectRatio(if (tall) 2f / 3f else 16f / 9f).scale(grow)
            .clip(shape).background(Card2)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier),
    ) {
        // The title shows until the picture covers it, or always when there is none
        Text(card.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        SitePicture(card.image, referer, Modifier.fillMaxSize())
    }
}

@Composable
private fun SiteButton(label: String, focused: Boolean) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(6.dp)).background(if (focused) Color.White else Color.White.copy(alpha = 0.16f)).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) Ink else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Dots(n: Int, on: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(start = 8.dp)) {
        repeat(n) { i -> Box(Modifier.size(if (i == on) 8.dp else 6.dp).clip(RoundedCornerShape(50)).background(if (i == on) Color.White else Muted.copy(alpha = 0.6f))) }
    }
}

@Composable
private fun DetailsScreen(site: TvSite, s: SiteScreen) {
    val scan = s.scan ?: return
    val d = scan.detail
    val referer = scan.url
    val image = d.image ?: s.preview?.image
    Box(Modifier.fillMaxSize()) {
        Backdrop(image, referer)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 48.dp, top = 44.dp, end = 48.dp).width(470.dp).height(260.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(site.name, color = Muted, fontSize = 14.sp, maxLines = 1)
                Text(d.title.ifBlank { s.preview?.title.orEmpty() }, color = Color.White, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (d.facts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        d.facts.forEach { f ->
                            Text(f, color = Muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.border(1.dp, Color(0xFF3A3F4B), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                val text = d.description.ifBlank { s.preview?.description.orEmpty() }
                if (text.isNotBlank()) Text(text, color = Color(0xFFCFD3DC), fontSize = 15.sp, lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SiteButton("▶  " + tr("Play"), s.line == -1 && s.button == 0)
                    SiteButton(tr("Open as web page"), s.line == -1 && s.button == 1)
                }
            }
            Rows(s, referer, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoadingScreen(site: TvSite, s: SiteScreen) {
    val p = s.preview
    Box(Modifier.fillMaxSize()) {
        Backdrop(p?.image, null)
        Column(Modifier.align(Alignment.CenterStart).padding(start = 48.dp).width(470.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(p?.title?.ifBlank { null } ?: site.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                Text(tr("Loading"), color = Muted, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Starting() {
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Color(0xFFE50914), strokeWidth = 4.dp, modifier = Modifier.size(44.dp))
            Text(tr("Starting the video"), color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Notice(text: String, modifier: Modifier) {
    Box(modifier.padding(bottom = 36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xEE1B1F28)).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(text, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun SiteMenu(site: TvSite) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.padding(end = 48.dp).width(300.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF21B1F28)).padding(vertical = 10.dp),
        ) {
            Text(site.name, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), maxLines = 1)
            site.menuItems.forEachIndexed { i, label ->
                val f = site.menuItem == i
                Text(
                    label,
                    color = if (f) Ink else Color.White,
                    fontSize = 16.sp,
                    fontWeight = if (f) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp)).background(if (f) Color.White else Color.Transparent).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
