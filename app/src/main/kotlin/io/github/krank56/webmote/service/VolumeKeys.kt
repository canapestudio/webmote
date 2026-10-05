package io.github.krank56.webmote.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import io.github.krank56.webmote.MainActivity
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.TvSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * While the TV is connected and the volume-keys setting is on, holds a MediaSession with a remote
 * volume provider: Android then sends the phone's volume keys and the system volume panel to the TV,
 * with the app closed or the phone locked. Releasing it gives the keys back to the phone.
 */
internal class VolumeKeys(private val context: Context, private val host: SessionHost) {

    /** Runs until cancelled; call it on the main thread. */
    suspend fun run() {
        val connected = host.session.state.map { it.connection == ConnectionState.Connected }.distinctUntilChanged()
        // A TV that reports no level (sound on a soundbar) gets ± only: the panel shows no level it can't know.
        val levelReported = host.session.state.map { it.capabilities.volumeLevel != Capability.Unavailable }.distinctUntilChanged()
        combine(connected, host.settings.volumeKeys, levelReported) { isConnected, enabled, absolute ->
            if (isConnected && enabled) absolute else null
        }
            .distinctUntilChanged()
            .collectLatest { absolute -> if (absolute != null) routeToTv(absolute) }
    }

    private suspend fun routeToTv(absolute: Boolean) = coroutineScope {
        val tv = host.session
        val provider = TvVolume(tv, this, absolute, tv.state.value.volume.level ?: 0)
        val media = MediaSession(context, TAG).apply {
            // MediaSession hands volume adjustments to the provider through its callback handler, and
            // drops them while no callback is set.
            setCallback(object : MediaSession.Callback() {})
            setPlaybackToRemote(provider)
            // Android only sends volume keys to a session whose playback is active.
            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build(),
            )
            host.snapshot().tvName?.let { name ->
                setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, name).build())
            }
            setSessionActivity(
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            isActive = true
        }
        RemoteVolume.publish(media.sessionToken)
        try {
            tv.state.mapNotNull { it.volume.level }.distinctUntilChanged().collect { provider.currentVolume = it.coerceIn(0, MAX_VOLUME) }
        } finally {
            RemoteVolume.publish(null)
            media.isActive = false
            media.release()
        }
    }

    private class TvVolume(private val tv: TvSession, private val scope: CoroutineScope, absolute: Boolean, level: Int) :
        VolumeProvider(
            if (absolute) VOLUME_CONTROL_ABSOLUTE else VOLUME_CONTROL_RELATIVE,
            MAX_VOLUME,
            level.coerceIn(0, MAX_VOLUME),
        ) {
        private var release: Job? = null

        override fun onAdjustVolume(direction: Int) {
            when {
                direction > 0 -> tv.volumeUp()
                direction < 0 -> tv.volumeDown()
            }
        }

        /** The system volume panel's slider. It reports no release, so a pause in the drag counts as one. */
        override fun onSetVolumeTo(volume: Int) {
            val level = volume.coerceIn(0, MAX_VOLUME)
            currentVolume = level
            tv.dragVolume(level)
            release?.cancel()
            release = scope.launch {
                delay(SLIDER_RELEASE)
                tv.setVolume(level)
            }
        }
    }

    private companion object {
        const val TAG = "Webmote"
        const val MAX_VOLUME = 100
        val SLIDER_RELEASE = 300.milliseconds
    }
}
