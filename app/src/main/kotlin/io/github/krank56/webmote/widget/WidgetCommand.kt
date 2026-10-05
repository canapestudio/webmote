package io.github.krank56.webmote.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.service.toggleMute
import io.github.krank56.webmote.service.togglePower
import io.github.krank56.webmote.sessionHost

/** A widget button. Each runs on the active TV through the session host, starting the keep-alive service. */
internal enum class WidgetCommand(private val run: SessionHost.() -> Unit) {
    Power({ togglePower() }),
    VolumeDown({ perform { it.volumeDown() } }),
    VolumeUp({ perform { it.volumeUp() } }),
    Mute({ toggleMute() }),
    Up({ press(RemoteButton.Up) }),
    Down({ press(RemoteButton.Down) }),
    Left({ press(RemoteButton.Left) }),
    Right({ press(RemoteButton.Right) }),
    Ok({ press(RemoteButton.Ok) }),
    Back({ press(RemoteButton.Back) }),
    Home({ press(RemoteButton.Home) }),
    ;

    val action: Action get() = actionRunCallback<WidgetCommandCallback>(actionParametersOf(KEY to name))

    fun runOn(host: SessionHost) = host.run()

    companion object {
        val KEY = ActionParameters.Key<String>("command")
    }
}

private fun SessionHost.press(button: RemoteButton) = perform { it.press(button) }

/** Runs a [WidgetCommand]. Public: Glance creates it by name. */
class WidgetCommandCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val name = parameters[WidgetCommand.KEY] ?: return
        val command = WidgetCommand.entries.firstOrNull { it.name == name } ?: return
        command.runOn(context.sessionHost)
    }
}
