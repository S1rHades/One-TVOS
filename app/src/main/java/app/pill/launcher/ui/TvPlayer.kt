package app.pill.launcher.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.playerTime
import app.pill.launcher.tr
import kotlinx.coroutines.delay

/** One audio or subtitle choice. */
data class TrackChoice(val id: String, val label: String, val selected: Boolean)

/** What the player screen asks of the video underneath (Media3 on the TV, a stand-in in tests). */
interface VideoControls {
    val position: Long
    val duration: Long
    val buffered: Long
    val playing: Boolean
    val buffering: Boolean
    /** Why the video can't play; null while it can. */
    val error: String?
    val speed: Float
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun setSpeed(speed: Float)
    fun audioTracks(): List<TrackChoice>
    fun textTracks(): List<TrackChoice>
    fun selectAudio(id: String)
    /** A subtitle track, or none with null. */
    fun selectText(id: String?)
}

/** The playback speeds offered, as Netflix does. */
val PlayerSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)

/** Netflix's red. */
private val PlayerRed = Color(0xFFE50914)

/** How long the controls stay up after the last press while the video plays. */
private const val ControlsMs = 5000L

/**
 * The launcher's own video player, worked as Netflix is with a remote. Hidden controls
 * come up on any key. On the progress bar, OK plays or pauses and Left / Right skip ten
 * seconds; held, they scrub further and further, and the video jumps when the key is let go.
 * Down goes to the buttons (Audio & Subtitles, Speed), Up back to the bar. Back closes a
 * panel, then hides the controls, then leaves. The remote's ⏯ ⏪ ⏩ work throughout.
 */
class TvPlayer(val title: String, private val video: VideoControls, private val onExit: () -> Unit) {
    enum class Focus { Bar, Buttons }
    enum class Panel { None, Tracks, Speed }

    var controlsVisible by mutableStateOf(true)
        private set
    var focus by mutableStateOf(Focus.Bar)
        private set
    /** The focused button: 0 Audio & Subtitles, 1 Speed. */
    var button by mutableIntStateOf(0)
        private set
    var panel by mutableStateOf(Panel.None)
        private set
    /** In the Audio & Subtitles panel: 0 audio, 1 subtitles; and the row in that column. */
    var column by mutableIntStateOf(0)
        private set
    var row by mutableIntStateOf(0)
        private set
    /** Where Left / Right are taking the video, before the jump; null when not scrubbing. */
    var scrubTarget by mutableStateOf<Long?>(null)
        private set
    /** Bumped on each press, so the controls hide a while after the last one. */
    var touched by mutableIntStateOf(0)
        private set
    /** Bumped on play or pause, for the big icon that flashes in the middle. */
    var flashes by mutableIntStateOf(0)
        private set

    // The video's state as last read (the screen reads it a few times a second)
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var buffered by mutableLongStateOf(0L)
        private set
    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    /** Reads the video's state again. */
    fun refresh() {
        position = video.position
        duration = video.duration
        buffered = video.buffered
        playing = video.playing
        buffering = video.buffering
        error = video.error
        speed = video.speed
    }

    fun audio(): List<TrackChoice> = video.audioTracks()

    /** Subtitles, with Off first. */
    fun subtitles(): List<TrackChoice> {
        val list = video.textTracks()
        return listOf(TrackChoice("", tr("Off"), list.none { it.selected })) + list
    }

    private fun show() {
        controlsVisible = true
        touched++
    }

    /** The controls go away after a while unused, while the video plays and no panel is open. */
    fun idle() {
        if (playing && panel == Panel.None && scrubTarget == null) {
            controlsVisible = false
            focus = Focus.Bar
        }
    }

    private fun togglePlay() {
        if (video.playing) video.pause() else video.play()
        flashes++
        refresh()
    }

    private fun skipBy(ms: Long) {
        refresh()
        val d = if (duration > 0) duration else Long.MAX_VALUE
        video.seekTo((position + ms).coerceIn(0, d))
        refresh()
    }

    /** The jump of one press while scrubbing: ten seconds, more and more as the key is held. */
    private fun scrubStep(repeat: Int): Long = when {
        repeat < 4 -> 10_000L
        repeat < 12 -> 30_000L
        else -> 60_000L
    }

    fun key(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        val code = e.keyCode
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A
        // The remote's media keys, whatever is on screen
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down && e.repeatCount == 0) {
                    when (code) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> video.play()
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> video.pause()
                        else -> togglePlay()
                    }
                    show()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down) {
                    skipBy(if (code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) 10_000L else -10_000L)
                    show()
                }
                return true
            }
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (down) return true
            when {
                panel != Panel.None -> {
                    panel = Panel.None
                    show()
                }
                controlsVisible && playing && error == null -> {
                    controlsVisible = false
                    focus = Focus.Bar
                }
                else -> onExit()
            }
            return true
        }
        if (error != null) return true
        if (panel != Panel.None) return panelKey(e, down, ok)
        // Hidden controls: OK plays or pauses, the arrows bring them back (Left / Right scrub at once)
        if (!controlsVisible) {
            if (!down) return true
            when {
                ok -> togglePlay()
                code == KeyEvent.KEYCODE_DPAD_DOWN -> focus = Focus.Buttons
                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    focus = Focus.Bar
                    show()
                    return barKey(e, down, ok)
                }
                else -> focus = Focus.Bar
            }
            show()
            return true
        }
        show()
        return if (focus == Focus.Bar) barKey(e, down, ok) else buttonsKey(e, down, ok)
    }

    private fun barKey(e: KeyEvent, down: Boolean, ok: Boolean): Boolean {
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) {
                    if (scrubTarget == null) refresh()
                    val from = scrubTarget ?: position
                    val step = scrubStep(e.repeatCount) * if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                    val d = if (duration > 0) duration else Long.MAX_VALUE
                    scrubTarget = (from + step).coerceIn(0, d)
                } else {
                    scrubTarget?.let { video.seekTo(it) }
                    scrubTarget = null
                    refresh()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (down) focus = Focus.Buttons
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> return true
        }
        if (ok) {
            if (!down) return true
            val target = scrubTarget
            if (target != null) {
                video.seekTo(target)
                scrubTarget = null
            } else {
                togglePlay()
            }
            return true
        }
        return false
    }

    private fun buttonsKey(e: KeyEvent, down: Boolean, ok: Boolean): Boolean {
        if (!down) return true
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> focus = Focus.Bar
            KeyEvent.KEYCODE_DPAD_LEFT -> button = (button - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> button = (button + 1).coerceAtMost(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> {}
            else -> if (ok) {
                if (button == 0) {
                    panel = Panel.Tracks
                    column = 0
                    row = audio().indexOfFirst { it.selected }.coerceAtLeast(0)
                } else {
                    panel = Panel.Speed
                    row = PlayerSpeeds.indexOf(video.speed).coerceAtLeast(0)
                }
            } else {
                return false
            }
        }
        return true
    }

    private fun panelKey(e: KeyEvent, down: Boolean, ok: Boolean): Boolean {
        if (!down) return true
        touched++
        val rows = when (panel) {
            Panel.Tracks -> if (column == 0) audio().size else subtitles().size
            else -> PlayerSpeeds.size
        }
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> row = (row - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> row = (row + 1).coerceAtMost((rows - 1).coerceAtLeast(0))
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (panel == Panel.Tracks) {
                column = if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) 0 else 1
                row = (if (column == 0) audio() else subtitles()).indexOfFirst { it.selected }.coerceAtLeast(0)
            }
            else -> if (ok) {
                when (panel) {
                    Panel.Tracks -> if (column == 0) {
                        audio().getOrNull(row)?.let { video.selectAudio(it.id) }
                    } else {
                        subtitles().getOrNull(row)?.let { video.selectText(it.id.ifEmpty { null }) }
                    }
                    Panel.Speed -> {
                        video.setSpeed(PlayerSpeeds[row])
                        panel = Panel.None
                    }
                    Panel.None -> {}
                }
                refresh()
            } else {
                return false
            }
        }
        return true
    }
}

/**
 * The player's screen: the video, and over it, when the controls are up, the title at the
 * top and at the bottom the red progress bar with the time left and the buttons; a spinner
 * while it loads, a big play or pause sign when it changes, and the Audio & Subtitles and
 * Speed panels.
 */
@Composable
fun TvPlayerScreen(player: TvPlayer, video: @Composable () -> Unit) {
    // The video's state, read a few times a second
    LaunchedEffect(player) {
        while (true) {
            player.refresh()
            delay(250)
        }
    }
    LaunchedEffect(player.touched, player.playing, player.controlsVisible) {
        if (player.controlsVisible) {
            delay(ControlsMs)
            player.idle()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        video()
        if (player.buffering && player.error == null) {
            CircularProgressIndicator(color = PlayerRed, strokeWidth = 4.dp, modifier = Modifier.align(Alignment.Center).size(64.dp))
        }
        PlayFlash(player)
        AnimatedVisibility(player.controlsVisible && player.panel == TvPlayer.Panel.None && player.error == null, enter = fadeIn(), exit = fadeOut()) {
            Controls(player)
        }
        if (player.panel == TvPlayer.Panel.Tracks) TracksPanel(player)
        if (player.panel == TvPlayer.Panel.Speed) SpeedPanel(player)
        player.error?.let { problem ->
            Column(Modifier.align(Alignment.Center).padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("This video can't play here"), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(problem, color = Color(0xB3FFFFFF), fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(24.dp))
                Text(tr("Back returns to the site, where its own player plays it"), color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

/** The big sign in the middle when the video plays or pauses: it shows what it is doing now, and fades. */
@Composable
private fun PlayFlash(player: TvPlayer) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(player.flashes) {
        if (player.flashes == 0) return@LaunchedEffect
        alpha.snapTo(1f)
        delay(350)
        alpha.animateTo(0f, tween(450))
    }
    if (alpha.value > 0f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(110.dp).alpha(alpha.value).clip(RoundedCornerShape(55.dp)).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                PlayIcon(playing = player.playing, size = 52)
            }
        }
    }
}

/** Play (a triangle) or pause (two bars), drawn. */
@Composable
private fun PlayIcon(playing: Boolean, size: Int, color: Color = Color.White) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        if (playing) {
            val bar = w * 0.28f
            drawRoundRect(color, Offset(w * 0.14f, h * 0.1f), Size(bar, h * 0.8f), CornerRadius(bar * 0.2f))
            drawRoundRect(color, Offset(w * 0.58f, h * 0.1f), Size(bar, h * 0.8f), CornerRadius(bar * 0.2f))
        } else {
            val p = Path().apply {
                moveTo(w * 0.2f, h * 0.08f)
                lineTo(w * 0.9f, h * 0.5f)
                lineTo(w * 0.2f, h * 0.92f)
                close()
            }
            drawPath(p, color)
        }
    }
}

@Composable
private fun Controls(player: TvPlayer) {
    Box(Modifier.fillMaxSize()) {
        // A dim over a paused picture, as Netflix does
        if (!player.playing) Box(Modifier.fillMaxSize().background(Color(0x55000000)))
        // The title, over a fade from the top
        Box(
            Modifier.fillMaxWidth().height(170.dp)
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))),
        ) {
            Text(
                player.title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 56.dp, top = 36.dp, end = 56.dp),
            )
        }
        // The progress bar and the buttons, over a fade from the bottom
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 56.dp, end = 56.dp, top = 90.dp, bottom = 36.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { PlayIcon(playing = player.playing, size = 26) }
                Spacer(Modifier.width(18.dp))
                ProgressBar(player, Modifier.weight(1f))
                Spacer(Modifier.width(18.dp))
                val shown = player.scrubTarget ?: player.position
                Text(
                    if (player.duration > 0) "-" + playerTime(player.duration - shown) else playerTime(shown),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerButton(tr("Audio & Subtitles"), focused = player.focus == TvPlayer.Focus.Buttons && player.button == 0)
                PlayerButton(tr("Speed (%1\$s)", speedText(player.speed)), focused = player.focus == TvPlayer.Focus.Buttons && player.button == 1)
            }
        }
    }
}

private fun speedText(speed: Float): String = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "x"

/** The red bar: what has played, what has loaded behind it in grey, and a knob when the bar has the focus. */
@Composable
private fun ProgressBar(player: TvPlayer, modifier: Modifier) {
    val focused = player.focus == TvPlayer.Focus.Bar
    BoxWithConstraints(modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
        val d = player.duration.coerceAtLeast(1)
        val shown = player.scrubTarget ?: player.position
        val played = (shown.toFloat() / d).coerceIn(0f, 1f)
        val loaded = (player.buffered.toFloat() / d).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(if (focused) 7.dp else 4.dp)) {
            val h = size.height
            val r = CornerRadius(h / 2)
            drawRoundRect(Color(0x4DFFFFFF), size = size, cornerRadius = r)
            drawRoundRect(Color(0x80FFFFFF), size = Size(size.width * loaded, h), cornerRadius = r)
            drawRoundRect(PlayerRed, size = Size(size.width * played, h), cornerRadius = r)
        }
        if (focused) {
            val knob = 18.dp
            Box(Modifier.offset(x = maxWidth * played - knob / 2).size(knob).clip(RoundedCornerShape(knob / 2)).background(PlayerRed))
            // Where the video will jump to, while scrubbing
            player.scrubTarget?.let { target ->
                Text(
                    playerTime(target),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = (maxWidth * played - 34.dp).coerceIn(0.dp, maxWidth - 68.dp), y = (-38).dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerButton(label: String, focused: Boolean) {
    Text(
        label,
        color = if (focused) Color.Black else Color(0xCCFFFFFF),
        fontSize = 18.sp,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** Audio on the left, subtitles on the right, over the dimmed video. */
@Composable
private fun TracksPanel(player: TvPlayer) {
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)).padding(horizontal = 120.dp, vertical = 70.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(90.dp)) {
            TrackColumn(tr("Audio"), player.audio().ifEmpty { listOf(TrackChoice("-", tr("Original"), true)) }, focusedRow = if (player.column == 0) player.row else -1)
            TrackColumn(tr("Subtitles"), player.subtitles(), focusedRow = if (player.column == 1) player.row else -1)
        }
        Text(tr("Back to the video"), color = Color(0x99FFFFFF), fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun TrackColumn(title: String, items: List<TrackChoice>, focusedRow: Int) {
    Column(Modifier.width(340.dp)) {
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        items.take(9).forEachIndexed { i, t -> ChoiceLine(t.label, t.selected, i == focusedRow) }
    }
}

@Composable
private fun ChoiceLine(label: String, selected: Boolean, focused: Boolean) {
    Row(
        Modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(6.dp)).background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "✓" else "", color = Color.White, fontSize = 20.sp, modifier = Modifier.width(30.dp))
        Text(
            label,
            color = if (focused || selected) Color.White else Color(0xB3FFFFFF),
            fontSize = 21.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpeedPanel(player: TvPlayer) {
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)).padding(horizontal = 120.dp, vertical = 70.dp)) {
        Column(Modifier.width(340.dp)) {
            Text(tr("Playback speed"), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            PlayerSpeeds.forEachIndexed { i, s ->
                ChoiceLine(speedText(s) + if (s == 1f) tr(" (normal)") else "", player.speed == s, i == player.row)
            }
        }
        Text(tr("Back to the video"), color = Color(0x99FFFFFF), fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomStart))
    }
}
