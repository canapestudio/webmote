package io.github.krank56.webmote.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.krank56.webmote.sessionHost

/** Runs the keep-alive notification's −, mute, + and power actions on the active TV. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = context.sessionHost
        when (intent.action) {
            VOLUME_DOWN -> host.perform { it.volumeDown() }
            VOLUME_UP -> host.perform { it.volumeUp() }
            TOGGLE_MUTE -> host.toggleMute()
            POWER_OFF -> host.perform { it.powerOff() }
        }
    }

    internal companion object {
        const val VOLUME_DOWN = "io.github.krank56.webmote.action.VOLUME_DOWN"
        const val VOLUME_UP = "io.github.krank56.webmote.action.VOLUME_UP"
        const val TOGGLE_MUTE = "io.github.krank56.webmote.action.TOGGLE_MUTE"
        const val POWER_OFF = "io.github.krank56.webmote.action.POWER_OFF"

        private val actions = listOf(VOLUME_DOWN, VOLUME_UP, TOGGLE_MUTE, POWER_OFF)

        fun intent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            actions.indexOf(action),
            Intent(context, NotificationActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
