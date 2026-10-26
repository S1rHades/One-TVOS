package app.pill.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.AppEntry
import app.pill.launcher.LauncherSettings
import kotlinx.coroutines.delay

@Composable
fun LauncherScreen(
    apps: List<AppEntry>,
    pins: List<String>,
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    var panelOpen by remember { mutableStateOf(false) }
    val pillFocus = remember { FocusRequester() }
    val pinnedApps = pins.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }

    // Initial focus on the settings pill, and return focus to it when the panel closes
    LaunchedEffect(panelOpen) {
        if (!panelOpen) {
            delay(200)
            runCatching { pillFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F1A))) {
        BackgroundSlideshow(
            urls = BackgroundSources.urls,
            intervalMs = settings.intervalSec * 1000L,
            enabled = settings.slideshow,
        )
        BackgroundScrim()

        // 48dp / 27dp margins keep content inside the TV overscan-safe area
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TimeWidget()
                SettingsPill(onClick = { panelOpen = true }, focusRequester = pillFocus)
            }

            Spacer(Modifier.height(24.dp))

            SectionTitle("Favorites")
            if (pinnedApps.isEmpty()) {
                Text(
                    "Hold OK on any app below to pin it here",
                    color = Color(0xB3FFFFFF),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 22.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pinnedApps, key = { it.pkg }) { app ->
                        AppTile(app, 108.dp, true, { onOpen(app) }, { onTogglePin(app) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("All apps")

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(apps, key = { it.pkg }) { app ->
                    AppTile(app, 80.dp, app.pkg in pins, { onOpen(app) }, { onTogglePin(app) })
                }
            }
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

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
}
