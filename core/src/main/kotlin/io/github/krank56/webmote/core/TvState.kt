package io.github.krank56.webmote.core

/** Everything the app can observe about the active TV, as reported by [TvSession.state]. */
public data class TvState(
    /** The registry ID (`deviceUUID`) of the TV this session targets, or null when no TV is active. */
    val tvId: String? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    /** The address of the TV the session is reaching, or last reached; null before the first attempt. */
    val host: String? = null,
    val info: TvInfo = TvInfo(),
)

public enum class ConnectionState {
    /** No connection is open or wanted (no active TV, or [TvSession.disconnect] was called). */
    Disconnected,

    /** Opening the connection. */
    Connecting,

    /** The TV shows "Allow this device?" and is waiting for the user to accept it with the remote. */
    AwaitingPrompt,

    Connected,

    /** The user declined the pairing prompt. */
    PairingDeclined,

    /** The TV presented a certificate other than the pinned one. */
    CertificateMismatch,

    /** The TV refused or didn't answer the connection: it's off or unreachable. */
    Off,
}

public data class TvInfo(
    val model: String? = null,
    val webOsVersion: String? = null,
)
