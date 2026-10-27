package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.AppEntry
import app.pill.launcher.LauncherSettings
import kotlinx.coroutines.delay

private val FavoriteIcon = 108.dp
private val GridIcon = 84.dp

/** Height of the favourites row (one tile), kept fixed so pinning the first app does not shift the grid. */
private val FavoritesRowHeight = FavoriteIcon + 60.dp

@Composable
fun LauncherScreen(
    apps: List<AppEntry>?,
    pins: List<String>,
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    var panelOpen by remember { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var toastVisible by remember { mutableStateOf(false) }
    // After an unpin from the favourites row, the package that should take focus (null = first app)
    var refocus by remember { mutableStateOf<Refocus?>(null) }

    val pillFocus = remember { FocusRequester() }
    val firstAppFocus = remember { FocusRequester() }
    val favoriteFocus = remember { HashMap<String, FocusRequester>() }
    val pinnedApps = apps.orEmpty().let { list -> pins.mapNotNull { pkg -> list.firstOrNull { it.pkg == pkg } } }

    fun togglePin(app: AppEntry, fromFavorites: Boolean) {
        val wasPinned = app.pkg in pins
        if (wasPinned && fromFavorites) {
            val i = pinnedApps.indexOfFirst { it.pkg == app.pkg }
            val neighbour = pinnedApps.getOrNull(i + 1) ?: pinnedApps.getOrNull(i - 1)
            refocus = Refocus(neighbour?.pkg)
        }
        onTogglePin(app)
        toast = Toast(if (wasPinned) "Removed ${app.label} from Favorites" else "Added ${app.label} to Favorites")
    }

    // First launch: put focus on the first favourite, else the first app
    LaunchedEffect(apps != null) {
        if (apps == null || initialFocusDone) return@LaunchedEffect
        delay(150)
        val target = pinnedApps.firstOrNull()?.let { favoriteFocus[it.pkg] }
            ?: firstAppFocus.takeIf { apps.isNotEmpty() }
            ?: pillFocus
        runCatching { target.requestFocus() }
        initialFocusDone = true
    }

    // Return focus to the pill when the settings panel closes
    LaunchedEffect(panelOpen) {
        if (!panelOpen && initialFocusDone) {
            delay(100)
            runCatching { pillFocus.requestFocus() }
        }
    }

    // Keep focus in the favourites row after the focused favourite is unpinned
    LaunchedEffect(refocus) {
        val target = refocus ?: return@LaunchedEffect
        delay(50)
        runCatching { (target.pkg?.let { favoriteFocus[it] } ?: firstAppFocus).requestFocus() }
        refocus = null
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            toastVisible = true
            delay(2200)
            toastVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        BackgroundSlideshow(
            urls = BackgroundSources.urls,
            intervalMs = settings.intervalSec * 1000L,
            enabled = settings.slideshow,
        )
        BackgroundScrim()

        // One scrolling page: moving down into the app grid scrolls the clock and favourites away
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = tileWidth(GridIcon)),
            contentPadding = PaddingValues(
                start = Spacing.ScreenH,
                end = Spacing.ScreenH,
                top = Spacing.ScreenV,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fullWidth("header") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TimeWidget()
                    SettingsPill(onClick = { panelOpen = true }, focusRequester = pillFocus)
                }
            }

            fullWidth("favoritesTitle") {
                SectionTitle("Favorites", hint = if (pinnedApps.isNotEmpty()) "Hold OK to unpin" else null)
            }
            fullWidth("favorites") {
                Box(Modifier.fillMaxWidth().height(FavoritesRowHeight), contentAlignment = Alignment.CenterStart) {
                    if (pinnedApps.isEmpty()) {
                        EmptyFavorites(loading = apps == null)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(pinnedApps, key = { it.pkg }) { app ->
                                val requester = favoriteFocus.getOrPut(app.pkg) { FocusRequester() }
                                AppTile(
                                    app = app,
                                    iconSize = FavoriteIcon,
                                    showPinBadge = false,
                                    onOpen = { onOpen(app) },
                                    onTogglePin = { togglePin(app, fromFavorites = true) },
                                    modifier = Modifier.focusRequester(requester),
                                )
                            }
                        }
                    }
                }
            }

            fullWidth("appsTitle") {
                SectionTitle(
                    "All apps",
                    hint = apps?.let { if (it.size == 1) "1 app" else "${it.size} apps" },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            itemsIndexed(apps.orEmpty(), key = { _, app -> app.pkg }) { index, app ->
                AppTile(
                    app = app,
                    iconSize = GridIcon,
                    showPinBadge = app.pkg in pins,
                    onOpen = { onOpen(app) },
                    onTogglePin = { togglePin(app, fromFavorites = false) },
                    // Fill the cell so the icon is centred in it
                    modifier = Modifier.fillMaxWidth().let { if (index == 0) it.focusRequester(firstAppFocus) else it },
                )
            }
        }

        AnimatedVisibility(
            visible = toastVisible,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            ToastChip(toast?.message.orEmpty())
        }

        if (panelOpen) {
            SettingsPanel(
                settings = settings,
                onChange = onSettingsChange,
                onOpenSystemSettings = onOpenSystemSettings,
                onDismiss = { panelOpen = false },
            )
        }
    }
}

/** A short confirmation message. A new instance restarts the hide timer even for the same text. */
private class Toast(val message: String)

private class Refocus(val pkg: String?)

/** An item that spans the whole grid row (header, section titles, favourites). */
private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.Tile),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            style = TextStyle(
                color = Palette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = TextShadow,
            ),
        )
        if (hint != null) {
            Spacer(Modifier.width(14.dp))
            Text(
                hint,
                style = TextStyle(color = Palette.TextTertiary, fontSize = 15.sp, shadow = TextShadow),
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun EmptyFavorites(loading: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .padding(horizontal = Spacing.Tile)
            .clip(shape)
            .background(Palette.Glass.copy(alpha = 0.12f))
            .border(1.5.dp, Palette.Hairline, shape)
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        Text(
            if (loading) "Loading apps…" else "Hold OK on any app below to pin it here",
            style = TextStyle(color = Palette.TextSecondary, fontSize = 17.sp, shadow = TextShadow),
        )
    }
}

@Composable
private fun ToastChip(message: String) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(Palette.Panel)
            .border(1.dp, Palette.Hairline, shape)
            .padding(horizontal = 26.dp, vertical = 14.dp),
    ) {
        Text(message, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
