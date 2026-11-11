package app.pill.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import app.pill.launcher.FileShare
import app.pill.launcher.LauncherSettings
import app.pill.launcher.UpdatePhase
import app.pill.launcher.UpdateState
import kotlin.math.roundToInt

// Settings → Notifications, Files and TV & system (with Updates): the pages and the system shortcuts.

/** Shortcuts on the TV & system page: one action here, the rest open the TV's own screens. */
enum class SystemAction(val title: String, val subtitle: String) {
    FreeMemory("Free up memory", "Close apps running in the background"),
    Network("Network & Wi-Fi", "Connection, Wi-Fi networks, VPN"),
    Display("Display", "Resolution, picture, screen saver"),
    Sound("Sound", "Audio output, volume, surround"),
    Storage("Storage", "What's using space, clear app data"),
    Update("System update", "Check for a firmware update"),
    DateTime("Date & time", "Time zone and clock format"),
    About("About this TV", "Model, Android version, security patch"),
    AllSettings("All Android TV settings", "Everything else"),
    Backup("Back up settings", "Saves your favourites, names, widgets, teams, mosque and switches as a file in Files"),
    Restore("Restore settings", "From the newest backup in Files; send one from your phone first"),
    Uninstall("Uninstall Pill Launcher", "Removes the launcher and its settings; the TV's own home screen takes over"),
}

@Composable
internal fun SystemPage(onAction: (SystemAction) -> String?, firstModifier: Modifier) {
    // The last result or problem for each row, shown under it until the panel closes
    val notes = remember { mutableStateMapOf<SystemAction, String>() }
    Group(null) {
        for (action in SystemAction.entries - SystemAction.Uninstall) {
            SettingRow(
                title = action.title,
                subtitle = notes[action] ?: action.subtitle,
                onClick = { onAction(action)?.let { notes[action] = it } ?: notes.remove(action) },
                modifier = if (action == SystemAction.entries.first()) firstModifier else Modifier,
            ) { focused -> Chevron(focused) }
        }
    }
    // Two presses, so a stray OK doesn't start it; the TV then asks to confirm as well
    var armed by remember { mutableStateOf(false) }
    Group("Pill Launcher") {
        SettingRow(
            title = SystemAction.Uninstall.title,
            subtitle = notes[SystemAction.Uninstall]
                ?: if (armed) "Press OK again to uninstall" else SystemAction.Uninstall.subtitle,
            onClick = {
                if (!armed) {
                    armed = true
                } else {
                    armed = false
                    onAction(SystemAction.Uninstall)?.let { notes[SystemAction.Uninstall] = it } ?: notes.remove(SystemAction.Uninstall)
                }
            },
            modifier = Modifier.onFocusChanged { if (!it.isFocused) armed = false },
        ) { focused -> Mark("✕", focused) }
    }
}

/**
 * What shows in the notification bar under the widgets: other apps' notifications (once
 * the TV allows reading them), each app's on or off, and Pill Launcher's own football and
 * connection notices. Updates and files always show.
 */
@Composable
internal fun NotificationsPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    access: Boolean,
    onAccess: () -> String?,
    apps: List<Pair<String, String>>,
    firstModifier: Modifier,
) {
    var accessNote by remember { mutableStateOf<String?>(null) }
    Group(null) {
        SettingRow(
            title = "Other apps' notifications",
            subtitle = accessNote ?: when {
                !access -> "Needs Notification access for Pill Launcher · OK to allow it"
                settings.appNotices -> "In the bar under the widgets"
                else -> "Kept out of the bar"
            },
            onClick = {
                if (!access) accessNote = onAccess() else onChange(settings.copy(appNotices = !settings.appNotices))
            },
            modifier = firstModifier,
        ) { focused -> if (access) Switch(checked = settings.appNotices, focused = focused) else Chevron(focused) }
        if (access && settings.appNotices) {
            SettingRow(
                title = "Hide private messages",
                subtitle = if (settings.hidePrivate) "Messages an app marks private show as \"New notification\"" else "Messages show their text",
                onClick = { onChange(settings.copy(hidePrivate = !settings.hidePrivate)) },
            ) { focused -> Switch(checked = settings.hidePrivate, focused = focused) }
        }
        SettingRow(
            title = "Football",
            subtitle = "Kick-off reminders, goals and wins for the teams you follow",
            onClick = { onChange(settings.copy(footballNotices = !settings.footballNotices)) },
        ) { focused -> Switch(checked = settings.footballNotices, focused = focused) }
        SettingRow(
            title = "Connection",
            subtitle = "When the internet drops, and how long it was gone",
            onClick = { onChange(settings.copy(connectionNotices = !settings.connectionNotices)) },
        ) { focused -> Switch(checked = settings.connectionNotices, focused = focused) }
        SettingRow(
            title = "Prayer times",
            subtitle = "Each prayer as its time comes, for a quarter of an hour, while the prayer times widget is on",
            onClick = { onChange(settings.copy(prayerNotices = !settings.prayerNotices)) },
        ) { focused -> Switch(checked = settings.prayerNotices, focused = focused) }
    }
    if (access && settings.appNotices && apps.isNotEmpty()) {
        Group("Apps") {
            for ((pkg, name) in apps) {
                val muted = pkg in settings.mutedApps
                SettingRow(
                    title = name,
                    subtitle = if (muted) "Muted" else "Shows in the bar",
                    onClick = { onChange(settings.copy(mutedApps = if (muted) settings.mutedApps - pkg else settings.mutedApps + pkg)) },
                ) { focused -> Switch(checked = !muted, focused = focused) }
            }
        }
    }
    Note("Updates and files from your phone always show. Hold OK on a notification in the bar to dismiss it.")
}

/**
 * Files: whether sharing keeps going behind other apps, how Files tidies itself, and the
 * phones allowed in. OK on a phone removes it; signing out every phone also makes a new code.
 */
@Composable
internal fun FilesSettingsPage(settings: LauncherSettings, onChange: (LauncherSettings) -> Unit, share: FileShare?, firstModifier: Modifier) {
    Group(null) {
        SettingRow(
            title = "Keep receiving",
            subtitle = if (settings.keepReceiving) "15 minutes after you leave the launcher for another app" else "Only while the launcher is on screen",
            onClick = { onChange(settings.copy(keepReceiving = !settings.keepReceiving)) },
            modifier = firstModifier,
        ) { focused -> Switch(checked = settings.keepReceiving, focused = focused) }
        SettingRow(
            title = "Offer to delete installed APKs",
            subtitle = "Once an APK's app is installed, the notification bar asks. Nothing is deleted without you",
            onClick = { onChange(settings.copy(askInstalledApks = !settings.askInstalledApks)) },
        ) { focused -> Switch(checked = settings.askInstalledApks, focused = focused) }
        SettingRow(
            title = "Remove files after 30 days",
            subtitle = "The notification bar says so the day before",
            onClick = { onChange(settings.copy(tidyOld = !settings.tidyOld)) },
        ) { focused -> Switch(checked = settings.tidyOld, focused = focused) }
    }
    if (share == null) return
    val state by share.state.collectAsState()
    Group(null) {
        Advanced(
            summary = "Trusted devices · " + when (state.devices.size) {
                0 -> "no phone paired yet"
                1 -> "1 phone"
                else -> "${state.devices.size} phones"
            },
        ) {
        if (state.devices.isEmpty()) {
            Note("No phone is paired yet. Scan the code on the Files page to pair one.")
        }
        for (d in state.devices) {
            key(d.id) {
                SettingRow(
                    title = d.name,
                    subtitle = "Paired ${pairedDay(d.pairedAt)} · OK removes it",
                    onClick = { share.forget(d.id) },
                ) { focused -> Mark("✕", focused) }
            }
        }
        SettingRow(
            title = "Sign out every phone",
            subtitle = "Code now ${state.code}. Signing out makes a new one",
            onClick = { share.signOutAll() },
        ) { focused -> Chevron(focused) }
        }
    }
}

private fun pairedDay(ms: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault())
        .format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()))

/** One line on where updates stand, for the category list and the check row. */
internal fun updateSummary(update: UpdateState, version: String): String {
    val release = update.release
    val v = if (version.isNotEmpty()) "Version $version" else "This version"
    return when (update.phase) {
        UpdatePhase.Checking -> "Checking GitHub…"
        UpdatePhase.Downloading -> "Downloading ${release?.version} · ${(update.progress * 100).roundToInt()}%"
        UpdatePhase.Installing -> "Installing ${release?.version}"
        UpdatePhase.Available -> "${release?.version} is ready to install"
        UpdatePhase.Failed -> update.message ?: "Couldn't check for updates"
        UpdatePhase.UpToDate -> "$v · up to date" + (update.checkedAt?.let { " · checked ${ageText(it).lowercase()}" } ?: "")
        UpdatePhase.Idle -> "$v · not checked yet"
    }
}

/**
 * Settings → Updates: check GitHub for a newer Pill Launcher, install it (with the same
 * progress as the card on the home screen), and whether to check by itself. What's new
 * in the newer version is listed under it.
 */
@Composable
internal fun UpdatesPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    update: UpdateState,
    version: String,
    onCheck: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    firstModifier: Modifier,
) {
    val release = update.release
    Group(null) {
        SettingRow(
            title = "Check for updates",
            subtitle = updateSummary(update, version).takeIf { release == null || update.phase == UpdatePhase.Checking }
                ?: "Version $version · ${release?.version} is out",
            onClick = onCheck,
            enabled = update.phase != UpdatePhase.Downloading && update.phase != UpdatePhase.Installing,
            modifier = firstModifier,
        ) { focused -> Chevron(focused) }
        if (release != null) {
            SettingRow(
                title = when (update.phase) {
                    UpdatePhase.Downloading -> "Downloading ${release.version}"
                    UpdatePhase.Installing -> "Installing ${release.version}"
                    UpdatePhase.Failed -> "Try ${release.version} again"
                    else -> "Update to ${release.version}"
                },
                subtitle = when (update.phase) {
                    UpdatePhase.Downloading -> "OK cancels"
                    UpdatePhase.Installing -> "Confirm on the screen Android shows"
                    else -> update.message ?: "About ${(release.sizeBytes / 1_000_000).coerceAtLeast(1)} MB · settings and favourites stay"
                },
                onClick = { if (update.phase == UpdatePhase.Downloading) onCancel() else onStart() },
                enabled = update.phase != UpdatePhase.Installing,
            ) { focused ->
                if (update.phase == UpdatePhase.Downloading) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${(update.progress * 100).roundToInt()}%", color = if (focused) Palette.Ink else Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        ProgressLine(update.progress, Modifier.width(72.dp))
                    }
                } else {
                    DownloadIcon(if (focused) Palette.Ink else Palette.TextPrimary, 20.dp)
                }
            }
        }
        SettingRow(
            title = "Check automatically",
            subtitle = if (settings.checkUpdates) "Every few hours, and a card on the home screen when one is out" else "Only when you check here",
            onClick = { onChange(settings.copy(checkUpdates = !settings.checkUpdates)) },
        ) { focused -> Switch(checked = settings.checkUpdates, focused = focused) }
    }
    // Rows that take focus, so Down scrolls through everything (plain text can't be reached)
    if (release != null && release.notes.isNotEmpty()) {
        Group("What's new in ${release.version}") {
            for (line in release.notes) {
                SettingRow(title = line, subtitle = null, onClick = {}, small = true) { }
            }
        }
    }
    Group(null) {
        SettingRow(
            title = "About updates",
            subtitle = "From Pill Launcher's releases on GitHub, signed with the same key, so settings and favourites stay. Android asks you to confirm each install, and the first time to allow Pill Launcher to install apps.",
            onClick = {},
            subtitleLines = 4,
        ) { }
    }
}
