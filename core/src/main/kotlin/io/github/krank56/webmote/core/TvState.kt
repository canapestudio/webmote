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
    val inputs: List<TvInput> = emptyList(),
    /** The TV's apps, pinned favourites first. */
    val apps: List<TvApp> = emptyList(),
    /** The ID of the app in front on the TV (`netflix`, `com.webos.app.livetv`…), or null when it isn't known. */
    val foregroundAppId: String? = null,
    /** The foreground app when it's a known player app, such as YouTube or Netflix; null for any other app. */
    val player: Player? = null,
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

public data class TvInput(val id: String, val label: String)

/** A known player app in front on the TV: what the player controls act on. */
public data class Player(
    val appId: String,
    /** Whether it's playing, when the TV says. */
    val playback: PlaybackState = PlaybackState.Unknown,
)

public enum class PlaybackState {
    Playing,
    Paused,

    /** The TV doesn't say whether the player is playing. */
    Unknown,
}

public data class TvApp(
    val id: String,
    val title: String,
    val iconUrl: String?,
    val pinned: Boolean,
)

/** What the TV has been found to support. [Capability.Unknown] until the session learns otherwise. */
public data class Capabilities(
    /** Whether picture writes through the alert workaround take effect. */
    val pictureWrites: Capability = Capability.Unknown,
    /** Whether the pointer socket (D-pad, buttons, touchpad) can be opened. */
    val pointer: Capability = Capability.Unknown,
    /** Whether the TV reports a volume level. */
    val volumeLevel: Capability = Capability.Unknown,
)

@Serializable
public enum class Capability { Unknown, Available, Unavailable }

/**
 * Buttons sent on the pointer socket as `type:button` messages, by the names the reference projects
 * list (protocol research §4.3).
 */
public enum class RemoteButton(
    internal val wireName: String,
    /**
     * Whether the button was seen working on webOS 26 (the research's capture on a C2). The others come
     * from the reference projects' lists only, and may do nothing on a current TV.
     */
    public val confirmedOnWebOs26: Boolean = false,
    /** Whether it opens one of LG's service menus, which change factory settings. */
    public val opensServiceMenu: Boolean = false,
) {
    Up("UP", confirmedOnWebOs26 = true),
    Down("DOWN", confirmedOnWebOs26 = true),
    Left("LEFT", confirmedOnWebOs26 = true),
    Right("RIGHT", confirmedOnWebOs26 = true),
    Ok("ENTER", confirmedOnWebOs26 = true),
    Back("BACK", confirmedOnWebOs26 = true),
    Home("HOME", confirmedOnWebOs26 = true),
    /** The quick settings menu. */
    Settings("QMENU"),
    Info("INFO", confirmedOnWebOs26 = true),
    Num0("0", confirmedOnWebOs26 = true),
    Num1("1", confirmedOnWebOs26 = true),
    Num2("2", confirmedOnWebOs26 = true),
    Num3("3", confirmedOnWebOs26 = true),
    Num4("4", confirmedOnWebOs26 = true),
    Num5("5", confirmedOnWebOs26 = true),
    Num6("6", confirmedOnWebOs26 = true),
    Num7("7", confirmedOnWebOs26 = true),
    Num8("8", confirmedOnWebOs26 = true),
    Num9("9", confirmedOnWebOs26 = true),
    Red("RED", confirmedOnWebOs26 = true),
    Green("GREEN", confirmedOnWebOs26 = true),
    Yellow("YELLOW", confirmedOnWebOs26 = true),
    Blue("BLUE", confirmedOnWebOs26 = true),
    Exit("EXIT", confirmedOnWebOs26 = true),
    Asterisk("ASTERISK"),
    Power("POWER"),
    InStart("IN_START", confirmedOnWebOs26 = true, opensServiceMenu = true),
    EzAdjust("EZ_ADJUST", opensServiceMenu = true),
    AdvancedSetting("ADVANCE_SETTING", opensServiceMenu = true),

    // TV and guide
    Guide("GUIDE", confirmedOnWebOs26 = true),
    Program("PROGRAM"),
    ChannelList("LIST"),
    LiveTv("DASH", confirmedOnWebOs26 = true),
    Tv("TV"),
    ChannelUp("CHANNELUP", confirmedOnWebOs26 = true),
    ChannelDown("CHANNELDOWN", confirmedOnWebOs26 = true),
    Flashback("FLASHBACK"),
    Favourites("FAVORITES"),
    Teletext("TELETEXT"),
    TextOption("TEXTOPTION"),
    Record("RECORD"),
    Recordings("RECLIST"),

    // Sound and picture
    Subtitles("CC"),
    AudioDescription("AD"),
    MultiAudio("SAP"),
    VolumeUp("VOLUMEUP", confirmedOnWebOs26 = true),
    VolumeDown("VOLUMEDOWN", confirmedOnWebOs26 = true),
    Mute("MUTE", confirmedOnWebOs26 = true),
    AspectRatio("ASPECT_RATIO"),
    PictureMode("EZPIC"),
    EnergySaving("EYE_Q"),
    LiveZoom("LIVE_ZOOM"),
    FocusZoom("MAGNIFIER_ZOOM"),
    ThreeD("3D_MODE"),

    // Menus and apps
    Menu("MENU", confirmedOnWebOs26 = true),
    MyApps("MYAPPS"),
    Recent("RECENT"),
    InputHub("INPUT_HUB"),
    Search("SEARCH"),
    ScreenRemote("SCREEN_REMOTE"),
    EManual("EMANUAL"),
    SleepTimer("TIMER"),
    AlwaysReady("UPDOWN"),
    Simplink("HCEC"),

    // Playback and streaming
    Play("PLAY", confirmedOnWebOs26 = true),
    Pause("PAUSE", confirmedOnWebOs26 = true),
    Stop("STOP"),
    Rewind("REWIND"),
    FastForward("FASTFORWARD"),
    Previous("GOTOPREV"),
    Next("GOTONEXT"),
    Netflix("NETFLIX", confirmedOnWebOs26 = true),
    Amazon("AMAZON", confirmedOnWebOs26 = true),
    Alexa("ALEXA"),
    Yandex("YANDEX"),
    Ivi("IVI"),
    Soccer("SOCCER"),
    Twin("TWIN"),
    Usp("USP"),
    Bendable("BENDABLE"),
}

public enum class MediaKey(internal val uri: String) {
    Play("ssap://media.controls/play"),
    Pause("ssap://media.controls/pause"),
    Stop("ssap://media.controls/stop"),
    Rewind("ssap://media.controls/rewind"),
    FastForward("ssap://media.controls/fastForward"),
}
