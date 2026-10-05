package io.github.krank56.webmote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.ProvideHaptics
import io.github.krank56.webmote.ui.main.MainScreen
import io.github.krank56.webmote.ui.main.MainTab
import io.github.krank56.webmote.ui.pairing.PairingProgressScreen
import io.github.krank56.webmote.ui.pairing.PairingScreen
import io.github.krank56.webmote.ui.settings.SettingsScreen
import io.github.krank56.webmote.ui.status.ConnectingScreen
import io.github.krank56.webmote.ui.status.ProblemScreen

/** Screens opened on top of the TV's state screen, kept on a small back stack. */
enum class Route { Settings, AddTv }

/** What fills the window: an opened [Route], or the screen the active TV's state calls for. */
private sealed interface Screen {
    data class Opened(val route: Route) : Screen
    data object FirstPairing : Screen
    data object Main : Screen
    data object Connecting : Screen
    data object Pairing : Screen
    data object Problem : Screen
}

/** The app's root: picks the screen from the saved TVs, the session's state and the back stack. */
@Composable
fun WebmoteApp(host: SessionHost) {
    val session = host.session
    val tvs by host.registry.tvs.collectAsStateWithLifecycle()
    val state by session.state.collectAsStateWithLifecycle()
    val backStack = rememberBackStack()
    var mainTab by rememberSaveable { mutableStateOf(MainTab.Remote) }

    // Forgetting the last TV leaves nothing to manage: go back to pairing.
    LaunchedEffect(tvs.isEmpty()) {
        if (tvs.isEmpty()) backStack.clear()
    }
    BackHandler(enabled = backStack.isNotEmpty()) { backStack.removeAt(backStack.lastIndex) }

    val open: (Route) -> Unit = { backStack.add(it) }
    val back: () -> Unit = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) }
    val home: () -> Unit = { backStack.clear() }

    val screen = backStack.lastOrNull()?.let(Screen::Opened) ?: stateScreen(tvs, state)

    ProvideHaptics {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { target ->
                when (target) {
                    is Screen.Opened -> when (target.route) {
                        Route.Settings -> SettingsScreen(
                            host = host,
                            tvs = tvs,
                            state = state,
                            onBack = back,
                            onDone = home,
                            onOpen = open,
                        )
                        Route.AddTv -> PairingScreen(
                            host = host,
                            state = state,
                            tvs = tvs,
                            // Leaving without pairing drops any attempt and goes back to the active TV.
                            onBack = {
                                back()
                                if (state.connection != ConnectionState.Connected) session.connect()
                            },
                            onPaired = home,
                        )
                    }
                    Screen.FirstPairing -> PairingScreen(host = host, state = state, tvs = tvs, onBack = null, onPaired = {})
                    Screen.Main -> MainScreen(
                        host = host,
                        tvs = tvs,
                        state = state,
                        tab = mainTab,
                        onTabChange = { mainTab = it },
                        onOpen = open,
                    )
                    Screen.Connecting -> ConnectingScreen(host = host, tvs = tvs, state = state, onOpen = open)
                    Screen.Pairing -> PairingProgressScreen(host = host, tvs = tvs, state = state, onOpen = open)
                    Screen.Problem -> ProblemScreen(host = host, tvs = tvs, state = state, onOpen = open)
                }
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

@Composable
private fun rememberBackStack(): SnapshotStateList<Route> = rememberSaveable(
    saver = listSaver(
        save = { stack -> stack.map { it.name } },
        restore = { names -> mutableStateListOf(*names.map(Route::valueOf).toTypedArray()) },
    ),
) { mutableStateListOf() }
