package app.pill.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.pill.launcher.FoundLink
import app.pill.launcher.LinkAdder
import app.pill.launcher.LinkKind
import app.pill.launcher.LinkPhase
import app.pill.launcher.hostOf
import app.pill.launcher.tr
import app.pill.launcher.webTile

/**
 * Home screen → Add an app or website: a link typed here or sent from a phone is checked,
 * then becomes a web shortcut, an app installed from its APK, or the app's Play Store page.
 * Below, the web shortcuts made so far, each removed with two presses of OK.
 */
@Composable
internal fun AddLinkPage(links: LinkAdder?, firstModifier: Modifier) {
    if (links == null) {
        Group(null) { Note(tr("Not available in safe mode")) }
        return
    }
    val state by links.state.collectAsState()
    val offered by links.offered.collectAsState()
    val changed by links.changed.collectAsState()
    var text by remember { mutableStateOf(state.link) }
    // A link sent from a phone (or a page's download): filled in and checked at once
    LaunchedEffect(offered) {
        val link = offered ?: return@LaunchedEffect
        text = link
        links.offered.value = null
        links.check(link)
    }

    // A link found: OK on what to do with it is the next press, so it takes the focus (and comes into view)
    val act = remember { FocusRequester() }
    LaunchedEffect(state.phase, state.found) {
        if (state.phase == LinkPhase.Found && state.found != null) {
            delay(120)
            runCatching { act.requestFocus() }
        }
    }

    Group(tr("Link")) {
        TeamSearchField(
            text,
            { text = it },
            hint = tr("Web address, e.g. youtube.com/tv"),
            modifier = firstModifier,
            onGo = { links.check(text) },
        )
        SettingRow(
            title = tr("Check this link"),
            subtitle = tr("A website, an app to install (.apk) or a Play Store page"),
            onClick = { links.check(text) },
            enabled = text.isNotBlank(),
            dimmed = text.isBlank(),
        ) { focused -> Chevron(focused) }
        Note(tr("Typing is easier on a phone: open Files on the phone and use Send a link"))
    }

    if (state.phase != LinkPhase.Idle) {
        Group(tr("What it is")) {
            val found = state.found
            when (state.phase) {
                LinkPhase.Checking -> Note(tr("Looking at %1\$s…", hostOf(state.link)))
                LinkPhase.Downloading -> SettingRow(
                    title = tr("Downloading · %1\$s%%", (state.progress * 100).toInt()),
                    subtitle = tr("OK to stop"),
                    onClick = { links.stopDownload() },
                ) { focused -> Mark("×", focused) }
                LinkPhase.Installing -> Note(tr("Android asks you to confirm the install on screen"))
                else -> {}
            }
            if (found != null && state.phase != LinkPhase.Checking) FoundRow(found)
            if (found != null && (state.phase == LinkPhase.Found || state.phase == LinkPhase.Failed)) {
                SettingRow(
                    title = when (found.kind) {
                        LinkKind.Website -> tr("Add to the home screen")
                        LinkKind.Apk -> tr("Download and install")
                        LinkKind.PlayStore -> tr("Open in the Play Store")
                    },
                    subtitle = when (found.kind) {
                        LinkKind.Website -> tr("A tile in Favorites that opens the site full screen")
                        LinkKind.Apk -> tr("Android asks you to confirm · it joins Favorites once installed")
                        LinkKind.PlayStore -> tr("Install it there · it joins Favorites once installed")
                    },
                    onClick = { links.add() },
                    modifier = Modifier.focusRequester(act),
                ) { focused -> Mark("+", focused) }
            }
            state.message?.let { Note(it) }
        }
    }

    // Read again whenever one is added or removed
    val shortcuts = remember(changed) { links.list() }
    if (shortcuts.isNotEmpty()) {
        var armed by remember { mutableStateOf<String?>(null) }
        Group(tr("Web shortcuts")) {
            for (s in shortcuts) {
                key(s.id) {
                    SettingRow(
                        title = s.name,
                        subtitle = if (armed == s.id) tr("OK again to remove it") else hostOf(s.url) + tr(" · OK to remove"),
                        onClick = {
                            if (armed == s.id) {
                                links.remove(s.id)
                                armed = null
                            } else {
                                armed = s.id
                            }
                        },
                    ) { focused -> Mark("−", focused) }
                }
            }
            Note(tr("Rename one, or move it along Favorites, by holding OK on its tile"))
        }
    }
}

/** What a checked link is: its icon, name and kind. */
@Composable
private fun FoundRow(found: FoundLink) {
    val icon = remember(found) { webTile(found.icon, found.name).asImageBitmap() }
    SettingRow(
        title = found.name,
        subtitle = when (found.kind) {
            LinkKind.Website -> tr("Website · %1\$s", hostOf(found.url))
            LinkKind.Apk -> if (found.sizeBytes > 0) tr("App to install · %1\$s · from %2\$s", formatBytes(found.sizeBytes), hostOf(found.url))
            else tr("App to install · from %1\$s", hostOf(found.url))
            LinkKind.PlayStore -> tr("App on the Play Store")
        },
        onClick = {},
        small = false,
    ) { _ ->
        if (found.kind == LinkKind.Website) {
            Image(icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(Squircle))
        } else {
            Box(Modifier)
        }
    }
}
