package io.github.krank56.webmote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.main.MainScreen
import io.github.krank56.webmote.ui.pairing.PairingProgressScreen
import io.github.krank56.webmote.ui.pairing.PairingScreen
import io.github.krank56.webmote.ui.status.ConnectingScreen
import io.github.krank56.webmote.ui.status.ProblemScreen

/** What fills the window: the screen the active TV's state calls for. */
private sealed interface Screen {
    data object FirstPairing : Screen
    data object Main : Screen
    data object Connecting : Screen
    data object Pairing : Screen
    data object Problem : Screen
}

/** The app's root: picks the screen from the saved TVs and the session's state. */
@Composable
fun WebmoteApp(host: SessionHost) {
    val session = host.session
    val tvs by host.registry.tvs.collectAsStateWithLifecycle()
    val state by session.state.collectAsStateWithLifecycle()

    val screen = stateScreen(tvs, state)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.FirstPairing -> PairingScreen(host = host, state = state)
                Screen.Main -> MainScreen(host = host, tvs = tvs, state = state)
                Screen.Connecting -> ConnectingScreen(host = host, tvs = tvs, state = state)
                Screen.Pairing -> PairingProgressScreen(host = host, tvs = tvs, state = state)
                Screen.Problem -> ProblemScreen(host = host, tvs = tvs, state = state)
            }
        }
    }
}

private fun stateScreen(tvs: List<SavedTv>, state: TvState): Screen {
    if (tvs.isEmpty()) return Screen.FirstPairing
    return when (state.connection) {
        ConnectionState.Connected -> Screen.Main
        ConnectionState.Connecting, ConnectionState.Disconnected -> Screen.Connecting
        ConnectionState.AwaitingPrompt, ConnectionState.AwaitingPin -> Screen.Pairing
        ConnectionState.Off,
        ConnectionState.NeedsPairing,
        ConnectionState.CertificateMismatch,
        ConnectionState.PairingDeclined,
        ConnectionState.Unsupported -> Screen.Problem
    }
}
