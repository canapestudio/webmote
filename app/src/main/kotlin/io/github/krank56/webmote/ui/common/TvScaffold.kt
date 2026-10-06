package io.github.krank56.webmote.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.Route
import io.github.krank56.webmote.ui.simple.SimpleTopBar

/**
 * A screen about the active TV's state (off, connecting, pairing problems): the shared top bar, so the
 * user can still switch TVs or reach Settings, over [content]. In Simple mode the bar is
 * [SimpleTopBar]: large TV buttons, and a Settings gear that has to be held.
 */
@Composable
fun TvScaffold(
    host: SessionHost,
    tvs: List<SavedTv>,
    state: TvState,
    onOpen: (Route) -> Unit,
    power: PowerAction? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val registryActiveId by host.registry.activeTvId.collectAsStateWithLifecycle()
    val simpleMode by host.settings.simpleMode.collectAsStateWithLifecycle()
    val activeTvId = state.tvId ?: registryActiveId
    val switchTo: (String) -> Unit = { id ->
        host.onUserInteraction()
        host.session.switchTo(id)
    }
    Scaffold(
        topBar = {
            if (simpleMode) {
                SimpleTopBar(tvs, activeTvId, onSwitch = switchTo, onSettings = { onOpen(Route.Settings) })
            } else {
                TvTopBar(
                    tvs = tvs,
                    activeTvId = activeTvId,
                    onSwitch = switchTo,
                    onSettings = { onOpen(Route.Settings) },
                    power = power,
                )
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            content()
        }
    }
}
