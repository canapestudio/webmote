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

/**
 * A screen about the active TV's state (off, connecting, pairing problems): the shared top bar with
 * the TV's name, over [content].
 */
@Composable
fun TvScaffold(
    host: SessionHost,
    tvs: List<SavedTv>,
    state: TvState,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val registryActiveId by host.registry.activeTvId.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TvTopBar(tvs = tvs, activeTvId = state.tvId ?: registryActiveId) },
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            content()
        }
    }
}
