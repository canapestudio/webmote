package io.github.krank56.webmote.ui.off

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.Route
import io.github.krank56.webmote.ui.common.LocalHaptics
import io.github.krank56.webmote.ui.common.Notice
import io.github.krank56.webmote.ui.common.PowerAction
import io.github.krank56.webmote.ui.common.StatusLayout
import io.github.krank56.webmote.ui.common.TvScaffold
import io.github.krank56.webmote.ui.common.tvName

/**
 * The active TV is off or unreachable: one big Turn on button. While this screen is visible the session
 * keeps probing for the TV and connects as soon as it answers. [large] gives Simple mode's larger text
 * and button.
 */
@Composable
fun TvOffScreen(
    host: SessionHost,
    tvs: List<SavedTv>,
    state: TvState,
    waking: Boolean,
    onOpen: (Route) -> Unit,
    large: Boolean = false,
) {
    val session = host.session
    val haptics = LocalHaptics.current
    val hintDismissed by host.settings.wakeHintDismissed.collectAsStateWithLifecycle()
    val name = tvName(tvs, state.tvId)
    val wakeFailed = state.connection == ConnectionState.WakeFailed

    // Poll only while the screen is actually visible, not while the app sits in the background.
    LifecycleStartEffect(session) {
        session.startOffPolling()
        onStopOrDispose { session.stopOffPolling() }
    }

    val wake = {
        haptics.press()
        host.onUserInteraction()
        session.wake()
    }

    TvScaffold(host, tvs, state, onOpen, power = PowerAction(turnsOn = true, onClick = wake)) {
        StatusLayout(
            icon = Icons.Rounded.TvOff,
            title = stringResource(if (waking) R.string.off_waking_title else R.string.off_title, name),
            body = stringResource(
                when {
                    waking -> R.string.off_waking_body
                    wakeFailed -> R.string.off_wake_failed_body
                    else -> R.string.off_body
                },
            ),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            large = large,
        ) {
            Button(
                onClick = wake,
                enabled = !waking,
                // In Simple mode it grows with a large font's label instead of cutting it off.
                modifier = Modifier
                    .widthIn(min = 240.dp)
                    .then(if (large) Modifier.heightIn(min = 88.dp) else Modifier.height(64.dp)),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                val iconSize = if (large) 36.dp else 28.dp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (waking) {
                        CircularProgressIndicator(Modifier.size(iconSize - 4.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(iconSize))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.off_turn_on),
                        style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (wakeFailed && !hintDismissed) {
                Spacer(Modifier.height(8.dp))
                WakeHint(onDismiss = { host.settings.dismissWakeHint() })
            }
        }
    }
}

/** The one-time hint about the TV setting that lets Wake-on-LAN turn it on. */
@Composable
private fun WakeHint(onDismiss: () -> Unit) {
    Notice(
        text = stringResource(R.string.off_wake_hint),
        icon = Icons.Rounded.Lightbulb,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_got_it)) }
        }
    }
}
