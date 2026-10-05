package io.github.krank56.webmote.service

import android.media.session.MediaController
import android.media.session.MediaSession
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Routes the phone's volume keys to the TV through the keep-alive service's MediaSession. */
object RemoteVolume {
    /** The service's remote-volume session, while the TV is connected and the volume-keys setting is on. */
    private val token = MutableStateFlow<MediaSession.Token?>(null)

    internal fun publish(session: MediaSession.Token?) {
        token.value = session
    }

    /** While [activity] is in the foreground, its volume keys go to the TV through the same MediaSession. */
    fun attach(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    token.collect { session -> activity.mediaController = session?.let { MediaController(activity, it) } }
                } finally {
                    activity.mediaController = null
                }
            }
        }
    }
}
