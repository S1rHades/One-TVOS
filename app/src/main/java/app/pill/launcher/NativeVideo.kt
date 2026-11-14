package app.pill.launcher

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.pill.launcher.ui.TrackChoice
import app.pill.launcher.ui.VideoControls
import java.util.Locale

/**
 * A website's video, played by the launcher's own player (Media3): asked for with the
 * headers the site's player used (who asked, the cookies) and the viewer's browser name,
 * so the server answers it as it did the site. It takes the TV's audio focus, which pauses
 * the site's own player.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class NativeVideo(context: Context, media: FoundMedia, userAgent: String, startMs: Long) : VideoControls {
    val player: ExoPlayer

    override var error: String? = null
        private set

    init {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(media.headers)
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = playbackProblem(e)
            }
        })
        val item = MediaItem.Builder().setUri(media.url).apply {
            when (media.kind) {
                MediaKind.Hls -> setMimeType(MimeTypes.APPLICATION_M3U8)
                MediaKind.Dash -> setMimeType(MimeTypes.APPLICATION_MPD)
                MediaKind.File -> {}
            }
        }.build()
        player.setMediaItem(item, startMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = true
    }

    override val position: Long get() = player.currentPosition
    override val duration: Long get() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
    override val buffered: Long get() = player.bufferedPosition
    override val playing: Boolean get() = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
    override val buffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_IDLE && error == null
    override val speed: Float get() = player.playbackParameters.speed

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    override fun pause() = player.pause()
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    private fun groups(type: Int): List<Tracks.Group> = player.currentTracks.groups.filter { it.type == type && it.isSupported }

    private fun choices(type: Int): List<TrackChoice> = groups(type).flatMapIndexed { g, group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { t ->
            val f = group.getTrackFormat(t)
            val language = f.language?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { c -> c.titlecase() } }
            val label = listOfNotNull(f.label, language).distinct().joinToString(" · ").ifBlank { tr("Track %1\$s", g + 1) }
            TrackChoice("$g:$t", label, group.isTrackSelected(t))
        }
    }

    override fun audioTracks(): List<TrackChoice> = choices(C.TRACK_TYPE_AUDIO)
    override fun textTracks(): List<TrackChoice> = choices(C.TRACK_TYPE_TEXT)

    private fun choose(type: Int, id: String) {
        val (g, t) = id.split(':').map { it.toIntOrNull() ?: return }
        val group = groups(type).getOrNull(g) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, t))
            .build()
    }

    override fun selectAudio(id: String) = choose(C.TRACK_TYPE_AUDIO, id)

    override fun selectText(id: String?) {
        if (id == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            choose(C.TRACK_TYPE_TEXT, id)
        }
    }

    fun release() = player.release()
}

/** Why a video didn't play, in plain words. */
fun playbackProblem(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> tr("The video's server refused the launcher's player")
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> tr("The video's server didn't answer")
    PlaybackException.ERROR_CODE_DRM_UNSPECIFIED, PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED, PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR, PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> tr("The video is protected and only plays in the site's player")
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> tr("The video comes in a form the player can't read")
    else -> e.errorCodeName.lowercase(Locale.ROOT).replace('_', ' ')
}
