package io.github.krank56.webmote.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.TvScaffold

/** The connected TV's remote, under the shared top bar: volume − and +. */
@Composable
fun MainScreen(host: SessionHost, tvs: List<SavedTv>, state: TvState) {
    val session = host.session
    TvScaffold(host = host, tvs = tvs, state = state) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = session::volumeDown, modifier = Modifier.size(80.dp)) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.remote_volume_down),
                    modifier = Modifier.size(32.dp),
                )
            }
            FilledTonalIconButton(onClick = session::volumeUp, modifier = Modifier.size(80.dp)) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.remote_volume_up),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
