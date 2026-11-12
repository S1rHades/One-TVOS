package app.pill.launcher.ui

import app.pill.launcher.rowStep
import app.pill.launcher.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import app.pill.launcher.FileKind
import app.pill.launcher.FileShare
import app.pill.launcher.SharedFile
import app.pill.launcher.fileSize
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FileCardShape = RoundedCornerShape(16.dp)
private val FileCardHeight = 118.dp
/** Files keep arriving this long after the launcher goes behind another app, when Keep receiving is on. */
const val KeepReceivingMs = 15 * 60 * 1000L

/**
 * Files, a page of its own: on the left, receiving (a code to scan, files arriving with
 * their progress, the space left); on the right, everything on the TV as cards, with
 * chips by kind. OK opens a file (photos in the launcher's own viewer); holding OK
 * offers Install, Send to phone and Delete. The share runs while the page is open, and
 * sharing runs whenever the launcher is on screen (and 15 minutes after, with Keep
 * receiving on), so a phone can send without this page open.
 */
@Composable
fun FilesPage(
    share: FileShare,
    keepReceiving: Boolean,
    onKeepReceiving: (Boolean) -> Unit,
    /** Opens a file with the TV's apps (an APK with Android's installer); a message when it can't. */
    onOpenFile: (File) -> String?,
    onDismiss: () -> Unit,
) {
    val state by share.state.collectAsState()
    // Started as the page opens, then checked every few seconds: if the TV's address changes
    // (a new lease from the router), the server moves to it, so the code always leads somewhere
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { share.start() }
            delay(5_000)
        }
    }
    var filter by remember { mutableStateOf<FileKind?>(null) }
    var menuFor by remember { mutableStateOf<Pair<SharedFile, Boolean>?>(null) }
    var sending by remember { mutableStateOf<SharedFile?>(null) }
    var viewing by remember { mutableStateOf<SharedFile?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3200)
            message = null
        }
    }
    val kinds = FileKind.entries.filter { k -> state.files.any { it.kind == k } }
    if (filter != null && filter !in kinds) filter = null
    val shown = state.files.filter { filter == null || it.kind == filter }
    val chipFocus = remember { FocusRequester() }
    val firstCard = remember { FocusRequester() }
    val keepFocus = remember { FocusRequester() }

    fun open(f: SharedFile) {
        val file = share.file(f.name) ?: return
        if (f.kind == FileKind.Photo) viewing = f else message = onOpenFile(file)
    }

    OverlayPage(
        onDismiss = {
            onDismiss()
        },
        width = 880.dp,
        framed = false,
    ) { _ ->
        PageTitle(tr("Files"), tr("From phones and computers on your home network"))
        Row(Modifier.fillMaxWidth().height(PageBodyHeight + 30.dp)) {
            PageCard(step = 1, modifier = Modifier.width(290.dp).fillMaxHeight()) {
                ReceivePanel(state = state, keepReceiving = keepReceiving, onKeepReceiving = onKeepReceiving, keepFocus = keepFocus)
            }
            Spacer(Modifier.width(18.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().reveal(2)) {
                if (state.files.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().glass(PageCardShape).padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilesIcon(Palette.TextSecondary, 40.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(tr("Nothing on the TV yet"), color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr("Scan the code with your phone's camera, then choose Send to TV. Photos, videos, apps and documents all work."),
                            color = Palette.TextSecondary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    ChipRow(
                        labels = listOf(tr("All %1\$s", state.files.size)) + kinds.map { it.title },
                        selected = if (filter == null) 0 else kinds.indexOf(filter) + 1,
                        onSelect = { filter = if (it == 0) null else kinds[it - 1] },
                        firstFocus = chipFocus,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 14.dp),
                    )
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .focusGroup()
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        for ((r, row) in shown.chunked(3).withIndex()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                for ((c, f) in row.withIndex()) {
                                    key(f.name) {
                                        FileCard(
                                            file = f,
                                            image = share.file(f.name),
                                            onOpen = { open(f) },
                                            onMenu = { held -> menuFor = f to held },
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(if (r == 0 && c == 0) Modifier.focusRequester(firstCard) else Modifier),
                                        )
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        PageFooter(tr("OK opens a file · Hold OK to install, send to a phone or delete"))
                    }
                }
            }
            PageMessage(message, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }
        }
        LaunchedEffect(state.files.isEmpty()) {
            delay(Motion.Enter.toLong())
            runCatching { if (state.files.isEmpty()) keepFocus.requestFocus() else firstCard.requestFocus() }
        }
    }

    // Over the page: the file's menu, the code to save it on a phone, a photo
    menuFor?.let { (f, held) ->
        MenuCard(
            okHeld = held,
            header = {
                FileGlyph(f.kind, f.name, Modifier.size(44.dp))
                Spacer(Modifier.width(14.dp))
                Text(f.name, color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            choices = buildList {
                add(MenuChoice(if (f.kind == FileKind.App) tr("Install") else tr("Open"), if (f.kind == FileKind.App) tr("Android asks to confirm") else null) { open(f) })
                add(MenuChoice(tr("Send to phone"), tr("Scan a code to save it on your phone")) { sending = f })
                add(MenuChoice(tr("Delete"), warning = true) { share.delete(f.name) })
            },
            onDismiss = { menuFor = null },
        )
    }
    sending?.let { f -> SendToPhone(f, share.linkFor(f.name), onDismiss = { sending = null }) }
    viewing?.let { start ->
        val photos = state.files.filter { it.kind == FileKind.Photo }
        PhotoViewer(photos, photos.indexOf(start).coerceAtLeast(0), fileFor = { share.file(it.name) }, onDismiss = { viewing = null })
    }
}

/** A short message over the bottom of the page (why a file can't open), sliding in and out. */
@Composable
private fun PageMessage(message: String?, modifier: Modifier) {
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        var last by remember { mutableStateOf("") }
        message?.let { last = it }
        ToastChip(last)
    }
}

@Composable
private fun ColumnScope.ReceivePanel(state: app.pill.launcher.ShareState, keepReceiving: Boolean, onKeepReceiving: (Boolean) -> Unit, keepFocus: FocusRequester) {
    PageLabel(tr("Receive"))
    Spacer(Modifier.height(12.dp))
    val link = state.link
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (state.running && link != null) {
            QrCode(link, Modifier.size(132.dp))
        } else {
            Box(Modifier.size(132.dp).background(Color(0x14FFFFFF), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                FilesIcon(Palette.TextTertiary, 36.dp)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        if (state.running && link != null) tr("Scan with your phone") else (state.problem ?: tr("Starting…")),
        color = Palette.TextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.running && state.address != null) {
        Text(
            tr("or open %1\$s:%2\$s · code %3\$s", state.address, state.port, state.code),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // The last phone that reached the TV, and how that went: whether a scan gets here at all
    Text(
        state.lastVisit?.let { v ->
            tr("Last phone: ") + ageText(java.time.Instant.ofEpochMilli(v.at)).lowercase() + tr(" from %1\$s · %2\$s · %3\$s", v.from, v.asked, v.outcome)
        } ?: if (state.running) tr("No phone has reached the TV yet") else "",
        color = if (state.lastVisit?.outcome?.startsWith("failed") == true) Color(0xFFFFC078) else Palette.TextTertiary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
    // A scan that never arrives is the phone's doing. The commonest cause on a new phone:
    // Android 17 keeps Chrome off the home network until it is allowed
    if (state.running && state.lastVisit == null) {
        Text(
            tr("Phone says the site can't be reached? On the phone: Settings → Apps → Chrome → Permissions → Nearby devices → Allow. It must be on this Wi-Fi, without a VPN."),
            color = Palette.TextTertiary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    for (t in state.transfers.take(2)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Color(0x14FFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${(100 * t.read / t.total.coerceAtLeast(1)).toInt()}%", color = Palette.TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            ProgressLine(t.read.toFloat() / t.total.coerceAtLeast(1), Modifier.fillMaxWidth())
            Spacer(Modifier.height(5.dp))
            Text(tr("%1\$s of %2\$s · from %3\$s", fileSize(t.read), fileSize(t.total), t.from), color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1)
        }
    }
    Spacer(Modifier.weight(1f))
    if (state.totalBytes > 0) {
        Text(tr("%1\$s free on the TV", fileSize(state.freeBytes)), color = Palette.TextTertiary, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        ProgressLine(1f - state.freeBytes.toFloat() / state.totalBytes, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }
    PageButton(
        if (keepReceiving) tr("Keep receiving: 15 min") else tr("Keep receiving: Off"),
        onClick = { onKeepReceiving(!keepReceiving) },
        modifier = Modifier.fillMaxWidth().focusRequester(keepFocus),
    )
}

/** A file as a card: a preview (photos), a play mark (videos), or its type, then its name and size. */
@Composable
private fun FileCard(file: SharedFile, image: File?, onOpen: () -> Unit, onMenu: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val input = rememberTileInput(onOpen, onMenu)
    val active = input.active
    val scale by animateFloatAsState(if (active) 1.06f else 1f, FocusSpring, label = "fileScale")
    val ring by animateFloatAsState(if (active) 1f else 0f, tween(Motion.Quick), label = "fileRing")
    val lift by animateDpAsState(if (active) 18.dp else 0.dp, label = "fileLift")
    Box(
        modifier
            .zIndex(if (active) 1f else 0f)
            .height(FileCardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = lift.toPx()
                shape = FileCardShape
                clip = true
            }
            .glass(FileCardShape)
            .then(input.modifier),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(kindColor(file.kind).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                if (file.kind == FileKind.Photo && image != null) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    FileGlyph(file.kind, file.name, Modifier.size(40.dp))
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(file.name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(fileSize(file.size), fileDay(file.modified)).joinToString(" · "),
                    color = Palette.TextTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ring }.border(2.5.dp, Palette.Focus, FileCardShape))
    }
}

private fun fileDay(ms: Long): String {
    val day = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    return when (day) {
        today -> tr("Today")
        today.minusDays(1) -> tr("Yesterday")
        else -> DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(day)
    }
}

private fun kindColor(kind: FileKind): Color = when (kind) {
    FileKind.Photo -> Color(0xFF74C0FC)
    FileKind.Video -> Color(0xFFFFD43B)
    FileKind.App -> Color(0xFF8CE99A)
    FileKind.Document -> Color(0xFFDEE2E6)
}

/** A file's mark in its kind's colour: a play triangle for videos, else the file's type. */
@Composable
private fun FileGlyph(kind: FileKind, name: String, modifier: Modifier) {
    val color = kindColor(kind)
    Box(modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (kind == FileKind.Video) {
            Canvas(Modifier.size(18.dp)) {
                val s = size.minDimension
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.2f, s * 0.1f)
                    lineTo(s * 0.9f, s * 0.5f)
                    lineTo(s * 0.2f, s * 0.9f)
                    close()
                }
                drawPath(path, color)
            }
        } else {
            val ext = name.substringAfterLast('.', "").uppercase().take(4).ifEmpty { "FILE" }
            Text(ext, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** [text] as a QR code, dark on a white rounded square. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }.getOrNull()
    }
    Box(modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(10.dp)) {
        if (matrix != null) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.minDimension / matrix.width
                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        if (matrix[x, y]) drawRect(Palette.Ink, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
            }
        }
    }
}

/** A code that opens [link] on a phone, to save [file] there. */
@Composable
private fun SendToPhone(file: SharedFile, link: String?, onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { close.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Palette.Scrim), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(360.dp).shadow(32.dp, RoundedCornerShape(26.dp)).glass(RoundedCornerShape(26.dp)).padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (link != null) {
                    QrCode(link, Modifier.size(170.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(tr("Scan to save %1\$s", file.name), color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(tr("Your phone must be on the same network"), color = Palette.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                } else {
                    Text(tr("Sharing isn't running. Connect the TV to your home network first."), color = Palette.TextPrimary, fontSize = 16.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(18.dp))
                PageButton(tr("Done"), onClick = onDismiss, modifier = Modifier.focusRequester(close))
            }
        }
    }
}

/** Photos from the TV, full screen: Left and Right move between them, Back closes. */
@Composable
private fun PhotoViewer(photos: List<SharedFile>, start: Int, fileFor: (SharedFile) -> File?, onDismiss: () -> Unit) {
    var at by remember { mutableIntStateOf(start) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { focus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        // The next photo is to the right where text runs left to right, to the left where it runs right to left
                        Key.DirectionRight -> { at = (at + rowStep(1)).coerceIn(0, photos.lastIndex); true }
                        Key.DirectionLeft -> { at = (at + rowStep(-1)).coerceIn(0, photos.lastIndex); true }
                        else -> false
                    }
                }
                .focusRequester(focus)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            val photo = photos.getOrNull(at)
            AnimatedVisibility(visible = photo != null, enter = fadeIn(), exit = fadeOut()) {
                AsyncImage(model = photo?.let(fileFor), contentDescription = photo?.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (photos.size > 1) {
                AnimatedVisibility(
                    visible = true,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ) {
                    Text(
                        tr("%1\$s of %2\$s", at + 1, photos.size),
                        color = Palette.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}
