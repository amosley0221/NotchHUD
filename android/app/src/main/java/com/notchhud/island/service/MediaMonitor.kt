package com.notchhud.island.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.notchhud.island.core.IslandState
import com.notchhud.island.core.MediaInfo

/**
 * Now playing via MediaSessionManager. Needs notification-listener access, which
 * the island already asks for, so there is no extra permission to explain.
 */
class MediaMonitor(private val context: Context) {

    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(context, IslandNotificationListener::class.java)

    private var controller: MediaController? = null
    private var lastTrackKey: String? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            IslandState.setMedia(null)
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }

    fun start() {
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            attach(manager.getActiveSessions(component))
        }
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun attach(sessions: List<MediaController>?) {
        val next = sessions?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions?.firstOrNull()
        if (next?.sessionToken == controller?.sessionToken) { publish(); return }
        controller?.unregisterCallback(callback)
        controller = next
        controller?.registerCallback(callback)
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) { IslandState.setMedia(null); return }
        val md = c.metadata
        val ps = c.playbackState
        if (md == null) { IslandState.setMedia(null); return }

        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (title.isBlank()) { IslandState.setMedia(null); return }

        val info = MediaInfo(
            title = title,
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            app = appLabel(c.packageName),
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            positionMs = ps?.position ?: 0L,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            artwork = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
        )
        IslandState.setMedia(info)

        // Track change fires its own transient, but only once per track.
        val key = "${info.title}|${info.artist}"
        if (info.playing && key != lastTrackKey) {
            lastTrackKey = key
            NotificationRouter.systemTransient(
                left = info.title,
                right = info.artist.ifBlank { info.app },
                color = ServiceRuntime.settings?.theme?.accent ?: androidx.compose.ui.graphics.Color.White,
                durationMs = 1800L,
            )
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext() ?: Unit
    fun previous() = controller?.transportControls?.skipToPrevious() ?: Unit

    private fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))
}
