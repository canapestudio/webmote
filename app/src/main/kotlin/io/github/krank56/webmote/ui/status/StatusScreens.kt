package io.github.krank56.webmote.ui.status

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.Route
import io.github.krank56.webmote.ui.common.LocalHaptics
import io.github.krank56.webmote.ui.common.StatusLayout
import io.github.krank56.webmote.ui.common.TvScaffold
import io.github.krank56.webmote.ui.common.tvName
import kotlinx.coroutines.delay

private const val RETRY_AFTER_MS = 8_000L

/** A light placeholder while the session connects to the active TV. */
@Composable
fun ConnectingScreen(host: SessionHost, tvs: List<SavedTv>, state: TvState, onOpen: (Route) -> Unit) {
    val name = tvName(tvs, state.tvId)
    // Offer a manual retry only if connecting takes unusually long.
    var showRetry by remember { mutableStateOf(false) }
    LaunchedEffect(state.connection) {
        showRetry = false
        delay(RETRY_AFTER_MS)
        showRetry = true
    }
    TvScaffold(host, tvs, state, onOpen) {
        StatusLayout(
            icon = Icons.Rounded.Tv,
            title = stringResource(R.string.connecting_title, name),
            body = null,
            progress = true,
        ) {
            if (showRetry) {
                TextButton(onClick = {
                    host.session.connect()
                }) { Text(stringResource(R.string.action_try_again)) }
            }
        }
    }
}

/**
 * The active TV can't be used until the user acts: it rejected the saved key, presented another
 * certificate, declined pairing, or is too old.
 */
@Composable
fun ProblemScreen(host: SessionHost, tvs: List<SavedTv>, state: TvState, onOpen: (Route) -> Unit) {
    val haptics = LocalHaptics.current
    val name = tvName(tvs, state.tvId)
    val repair = {
        haptics.press()
        host.session.repair()
    }
    val buttonModifier = Modifier.widthIn(min = 200.dp)
    TvScaffold(host, tvs, state, onOpen) {
        when (state.connection) {
            ConnectionState.CertificateMismatch -> StatusLayout(
                icon = Icons.Rounded.GppMaybe,
                title = stringResource(R.string.problem_certificate_title),
                body = stringResource(R.string.problem_certificate_body, name),
                iconContainer = MaterialTheme.colorScheme.errorContainer,
                iconContent = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Button(onClick = repair, modifier = buttonModifier) { Text(stringResource(R.string.action_repair_trust)) }
            }
            ConnectionState.PairingDeclined -> StatusLayout(
                icon = Icons.Rounded.Block,
                title = stringResource(R.string.problem_declined_title),
                body = stringResource(R.string.problem_declined_body, name),
            ) {
                Button(onClick = repair, modifier = buttonModifier) { Text(stringResource(R.string.action_repair)) }
            }
            ConnectionState.Unsupported -> StatusLayout(
                icon = Icons.Rounded.TvOff,
                title = stringResource(R.string.problem_unsupported_title),
                body = stringResource(R.string.problem_unsupported_body, name),
                iconContainer = MaterialTheme.colorScheme.errorContainer,
                iconContent = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                OutlinedButton(onClick = { onOpen(Route.AddTv) }, modifier = buttonModifier) {
                    Text(stringResource(R.string.action_add_another_tv))
                }
            }
            // NeedsPairing, and anything else that lands here.
            else -> StatusLayout(
                icon = Icons.Rounded.LinkOff,
                title = stringResource(R.string.problem_needs_pairing_title),
                body = stringResource(R.string.problem_needs_pairing_body, name),
            ) {
                Button(onClick = repair, modifier = buttonModifier) {
                    Text(stringResource(R.string.action_repair))
                }
            }
        }
    }
}
