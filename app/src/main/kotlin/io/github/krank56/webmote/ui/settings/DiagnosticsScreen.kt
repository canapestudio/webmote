package io.github.krank56.webmote.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.BackScaffold
import io.github.krank56.webmote.ui.common.SectionHeader

/**
 * What Webmote knows about the active TV: identity, which features work, its Wake-on-LAN MACs and the
 * connection state. Features it hasn't learned yet show as unknown, never as working.
 */
@Composable
fun DiagnosticsScreen(tvs: List<SavedTv>, state: TvState, onBack: () -> Unit) {
    val record = tvs.firstOrNull { it.id == state.tvId }
    val caps = state.capabilities
    val learned = record?.capabilities
    // The live session is the authority; fall back to what the registry learned earlier.
    fun pick(liveValue: Capability, learnedValue: Capability?): Capability =
        if (liveValue != Capability.Unknown) liveValue else learnedValue ?: Capability.Unknown

    val unknown = stringResource(R.string.diagnostics_unknown)
    val notReported = stringResource(R.string.diagnostics_not_reported)
    val context = LocalContext.current
    val version = remember(context) { appVersion(context) }

    BackScaffold(title = stringResource(R.string.diagnostics_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            if (record == null) {
                item {
                    Text(
                        stringResource(R.string.diagnostics_no_tv),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                item { Header(stringResource(R.string.diagnostics_tv)) }
                item { InfoRow(stringResource(R.string.diagnostics_name), record.name) }
                item { InfoRow(stringResource(R.string.diagnostics_model), state.info.model ?: record.model ?: unknown) }
                item {
                    InfoRow(
                        stringResource(R.string.diagnostics_webos),
                        state.info.webOsVersion ?: record.webOsVersion ?: unknown,
                    )
                }
                item { InfoRow(stringResource(R.string.diagnostics_ip), record.host) }
                item { InfoRow(stringResource(R.string.diagnostics_connection), stringResource(state.connection.labelRes())) }

                item { Header(stringResource(R.string.diagnostics_features)) }
                item {
                    val picture = pick(caps.pictureWrites, learned?.pictureWrites)
                    val learnedOn = learned?.pictureWritesLearnedOn
                    CapabilityRow(
                        title = stringResource(R.string.diagnostics_picture),
                        summary = if (picture == Capability.Unavailable && learnedOn != null) {
                            stringResource(R.string.diagnostics_picture_learned_on, learnedOn)
                        } else {
                            stringResource(R.string.diagnostics_picture_summary)
                        },
                        capability = picture,
                    )
                }
                item {
                    CapabilityRow(
                        title = stringResource(R.string.diagnostics_pointer),
                        summary = stringResource(R.string.diagnostics_pointer_summary),
                        capability = pick(caps.pointer, learned?.pointer),
                    )
                }
                item {
                    CapabilityRow(
                        title = stringResource(R.string.diagnostics_volume),
                        summary = stringResource(R.string.diagnostics_volume_summary),
                        capability = pick(caps.volumeLevel, learned?.volumeLevel),
                    )
                }

                item { Header(stringResource(R.string.diagnostics_wake)) }
                item { InfoRow(stringResource(R.string.diagnostics_wired_mac), record.wiredMac?.uppercase() ?: notReported) }
                item { InfoRow(stringResource(R.string.diagnostics_wifi_mac), record.wifiMac?.uppercase() ?: notReported) }
            }
            item { Header(stringResource(R.string.diagnostics_app)) }
            item { InfoRow(stringResource(R.string.diagnostics_app_version), version ?: unknown) }
        }
    }
}

@Composable
private fun Header(text: String) {
    SectionHeader(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        modifier = Modifier.semantics(mergeDescendants = true) { },
    )
}

@Composable
private fun CapabilityRow(title: String, summary: String, capability: Capability) {
    val (icon, label, tint) = capabilityLook(capability)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
            }
        },
        modifier = Modifier.semantics(mergeDescendants = true) { },
    )
}

private data class CapabilityLook(val icon: ImageVector, val label: String, val tint: Color)

@Composable
private fun capabilityLook(capability: Capability): CapabilityLook = when (capability) {
    Capability.Available -> CapabilityLook(
        Icons.Rounded.CheckCircle,
        stringResource(R.string.diagnostics_works),
        MaterialTheme.colorScheme.primary,
    )
    Capability.Unavailable -> CapabilityLook(
        Icons.Rounded.Cancel,
        stringResource(R.string.diagnostics_doesnt_work),
        MaterialTheme.colorScheme.error,
    )
    Capability.Unknown -> CapabilityLook(
        Icons.AutoMirrored.Rounded.HelpOutline,
        stringResource(R.string.diagnostics_unknown),
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@StringRes
private fun ConnectionState.labelRes(): Int = when (this) {
    ConnectionState.Disconnected -> R.string.connection_disconnected
    ConnectionState.Connecting -> R.string.connection_connecting
    ConnectionState.AwaitingPrompt -> R.string.connection_awaiting_prompt
    ConnectionState.AwaitingPin -> R.string.connection_awaiting_pin
    ConnectionState.Connected -> R.string.connection_connected
    ConnectionState.NeedsPairing -> R.string.connection_needs_pairing
    ConnectionState.PairingDeclined -> R.string.connection_pairing_declined
    ConnectionState.CertificateMismatch -> R.string.connection_certificate_mismatch
    ConnectionState.Unsupported -> R.string.connection_unsupported
    ConnectionState.Off -> R.string.connection_off
    ConnectionState.WakeFailed -> R.string.connection_wake_failed
}

private fun appVersion(context: Context): String? = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName
}.getOrNull()
