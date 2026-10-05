package io.github.krank56.webmote.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.StatusLayout
import io.github.krank56.webmote.ui.common.TvScaffold
import io.github.krank56.webmote.ui.common.tvName

/**
 * The status of a pairing attempt on the pairing screen: connecting, waiting for the prompt, or why
 * it failed.
 */
@Composable
fun PairingStatusCard(
    connection: ConnectionState,
    target: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = connection in setOf(
        ConnectionState.PairingDeclined,
        ConnectionState.Off,
        ConnectionState.CertificateMismatch,
    )
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (connection) {
                ConnectionState.AwaitingPrompt -> StatusRow(
                    icon = Icons.Rounded.Tv,
                    title = stringResource(R.string.pairing_prompt_title),
                    body = stringResource(R.string.pairing_prompt_body),
                )
                ConnectionState.PairingDeclined -> {
                    StatusRow(Icons.Rounded.ErrorOutline, stringResource(R.string.pairing_declined), body = null)
                    RetryButton(R.string.action_try_again, onRetry)
                }
                ConnectionState.Off -> {
                    StatusRow(Icons.Rounded.ErrorOutline, stringResource(R.string.pairing_unreachable, target), body = null)
                    RetryButton(R.string.action_try_again, onRetry)
                }
                ConnectionState.CertificateMismatch ->
                    StatusRow(Icons.Rounded.ErrorOutline, stringResource(R.string.pairing_certificate), body = null)
                ConnectionState.Connecting, ConnectionState.Disconnected, ConnectionState.Connected -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Text(stringResource(R.string.pairing_connecting, target), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, body: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RetryButton(label: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick) { Text(stringResource(label)) }
    }
}

/** Shown when the active TV asks to be paired again: accept the prompt on the TV. */
@Composable
fun PairingProgressScreen(host: SessionHost, tvs: List<SavedTv>, state: TvState) {
    val name = tvName(tvs, state.tvId)
    TvScaffold(host, tvs, state) {
        StatusLayout(
            icon = Icons.Rounded.Tv,
            title = stringResource(R.string.pairing_prompt_title_named, name),
            body = stringResource(R.string.pairing_prompt_body),
            progress = true,
        )
    }
}
