package io.github.krank56.webmote.core

import kotlinx.serialization.Serializable

/** Everything the app can observe about the active TV, as reported by [TvSession.state]. */
public data class TvState(
    /** The registry ID (`deviceUUID`) of the TV this session targets, or null when no TV is active. */
    val tvId: String? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    /** The address of the TV the session is reaching, or last reached; null before the first attempt. */
    val host: String? = null,
    /** Whether a [TvSession.wake] is waiting for the TV to answer (the state is then [ConnectionState.Connecting]). */
    val waking: Boolean = false,
    val info: TvInfo = TvInfo(),
    val volume: Volume = Volume(),
    val picture: PictureValues = PictureValues(),
    val capabilities: Capabilities = Capabilities(),
)

public enum class ConnectionState {
    /** No connection is open or wanted (no active TV, or [TvSession.disconnect] was called). */
    Disconnected,

    /** Opening the connection, or waiting for the TV to come up after a wake. */
    Connecting,

    /** The TV shows "Allow this device?" and is waiting for the user to accept it with the remote. */
    AwaitingPrompt,

    /** The TV shows a PIN that has to be submitted with [TvSession.submitPin]. */
    AwaitingPin,

    Connected,

    /** The TV rejected the stored client key. [TvSession.repair] pairs again. */
    NeedsPairing,

    /** The user declined the pairing prompt, or the PIN was wrong. */
    PairingDeclined,

    /** The TV presented a certificate other than the pinned one. [TvSession.repair] re-pins. */
    CertificateMismatch,

    /** The TV only answers on the plain port 3000: it runs webOS 1–3, which Webmote doesn't support. */
    Unsupported,

    /** The TV refused or didn't answer the connection: it's off or unreachable. */
    Off,

    /** A wake was sent but the TV didn't respond within [SessionConfig.wakeTimeout]. */
    WakeFailed,
}

public data class TvInfo(
    val model: String? = null,
    val webOsVersion: String? = null,
)

public data class Volume(
    /** The TV's volume level, or null when it reports none (e.g. sound goes to a soundbar) or isn't known yet. */
    val level: Int? = null,
    val muted: Boolean = false,
)

/** Current picture settings. A null value hasn't been reported by the TV. */
public data class PictureValues(
    val backlight: Int? = null,
    val brightness: Int? = null,
    val contrast: Int? = null,
    val color: Int? = null,
    val energySaving: EnergySaving? = null,
) {
    public operator fun get(setting: PictureSetting): Int? = when (setting) {
        PictureSetting.Backlight -> backlight
        PictureSetting.Brightness -> brightness
        PictureSetting.Contrast -> contrast
        PictureSetting.Color -> color
    }
}

/** A numeric picture setting, 0–100. On OLED sets [Backlight] is the OLED light. */
public enum class PictureSetting(internal val key: String) {
    Backlight("backlight"),
    Brightness("brightness"),
    Contrast("contrast"),
    Color("color"),
}

public enum class EnergySaving(internal val key: String) {
    Auto("auto"),
    Off("off"),
    Min("min"),
    Med("med"),
    Max("max"),
}

/** What the TV has been found to support. [Capability.Unknown] until the session learns otherwise. */
public data class Capabilities(
    /** Whether picture writes through the alert workaround take effect. */
    val pictureWrites: Capability = Capability.Unknown,
    /** Whether the pointer socket (D-pad and buttons) can be opened. */
    val pointer: Capability = Capability.Unknown,
    /** Whether the TV reports a volume level. */
    val volumeLevel: Capability = Capability.Unknown,
)

@Serializable
public enum class Capability { Unknown, Available, Unavailable }

/** Buttons sent on the pointer socket as `type:button` messages. */
public enum class RemoteButton(internal val wireName: String) {
    Up("UP"),
    Down("DOWN"),
    Left("LEFT"),
    Right("RIGHT"),
    Ok("ENTER"),
    Back("BACK"),
    Home("HOME"),
    Settings("QMENU"),
    Info("INFO"),
}
