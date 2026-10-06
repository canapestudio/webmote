package io.github.krank56.webmote.ui.simple

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.Route
import io.github.krank56.webmote.ui.common.StatusLayout
import io.github.krank56.webmote.ui.common.TvScaffold

/**
 * Simple mode's stand-in for the problem screen (the TV needs pairing again, presents another
 * certificate, declined pairing or isn't supported): one plain sentence and no technical choices. Whoever
 * set Webmote up holds the gear and re-pairs from Settings, or turns Simple mode off to see the details.
 */
@Composable
fun SimpleProblemScreen(host: SessionHost, tvs: List<SavedTv>, state: TvState, onOpen: (Route) -> Unit) {
    TvScaffold(host, tvs, state, onOpen) {
        StatusLayout(
            icon = Icons.Rounded.Build,
            title = stringResource(R.string.simple_problem),
            body = null,
            large = true,
        )
    }
}
