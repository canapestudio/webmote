package io.github.krank56.webmote.service

import androidx.annotation.StringRes
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.TvState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What the notification shows about the active TV. It changes far less often than [TvState] (not on
 * every volume step), so the notification only redraws when something it shows changes.
 */
internal data class TvSnapshot(
    /** The TV's display name, or null when no TV is saved yet. */
    val tvName: String?,
    val connection: ConnectionState,
    val muted: Boolean,
) {
    val connected: Boolean get() = connection == ConnectionState.Connected
}

internal fun SessionHost.snapshot(state: TvState = session.state.value): TvSnapshot {
    val name = state.tvId?.let { registry[it]?.name } ?: registry.activeTv?.name
    return TvSnapshot(
        tvName = name,
        connection = state.connection,
        muted = state.volume.muted,
    )
}

internal fun SessionHost.snapshots(): Flow<TvSnapshot> =
    combine(session.state, registry.tvs) { state, _ -> snapshot(state) }.distinctUntilChanged()

/** The TV is connected, or a connection or pairing is under way. */
internal val ConnectionState.isLive: Boolean
    get() = when (this) {
        ConnectionState.Connecting,
        ConnectionState.AwaitingPrompt,
        ConnectionState.AwaitingPin,
        ConnectionState.Connected,
        -> true
        else -> false
    }

/** States the user can only resolve in the app (pairing prompts and pairing problems). */
internal val ConnectionState.needsApp: Boolean
    get() = when (this) {
        ConnectionState.AwaitingPrompt,
        ConnectionState.AwaitingPin,
        ConnectionState.NeedsPairing,
        ConnectionState.PairingDeclined,
        ConnectionState.CertificateMismatch,
        ConnectionState.Unsupported,
        -> true
        else -> false
    }

@get:StringRes
internal val ConnectionState.label: Int
    get() = when (this) {
        ConnectionState.Connected -> R.string.system_state_connected
        ConnectionState.Connecting -> R.string.system_state_connecting
        ConnectionState.AwaitingPrompt -> R.string.system_state_awaiting_prompt
        ConnectionState.AwaitingPin -> R.string.system_state_awaiting_pin
        ConnectionState.NeedsPairing -> R.string.system_state_needs_pairing
        ConnectionState.PairingDeclined -> R.string.system_state_pairing_declined
        ConnectionState.CertificateMismatch -> R.string.system_state_certificate_mismatch
        ConnectionState.Unsupported -> R.string.system_state_unsupported
        ConnectionState.Off -> R.string.system_state_off
        ConnectionState.WakeFailed -> R.string.system_state_wake_failed
        ConnectionState.Disconnected -> R.string.system_state_disconnected
    }

internal fun SessionHost.toggleMute() = perform { it.setMute(!it.state.value.volume.muted) }
