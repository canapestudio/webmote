package io.github.krank56.webmote.core.internal

/** How a player app seeks back and forward. */
internal enum class SeekMethod {
    /** The media controls' `rewind` and `fastForward`. */
    MediaControls,
}

/** A player app the session knows how to drive. */
internal class KnownPlayer(val appId: String, val seek: SeekMethod)

/**
 * The apps the session reports as players, with how each one seeks. Live TV, HDMI inputs and the
 * home screen are never players.
 *
 * UNCONFIRMED: the app IDs come from community sources (Home Assistant users, LG TV forums), not
 * from captures on webOS 26, and every app seeks with the media controls until the test TV shows
 * which apps ignore them. Check both against the test TV.
 */
internal object KnownPlayers {
    private val players: Map<String, KnownPlayer> = listOf(
        KnownPlayer("youtube.leanback.v4", SeekMethod.MediaControls), // YouTube
        KnownPlayer("netflix", SeekMethod.MediaControls),
        KnownPlayer("amazon", SeekMethod.MediaControls), // Prime Video
        KnownPlayer("com.disney.disneyplus-prod", SeekMethod.MediaControls), // Disney+
        KnownPlayer("cdp-30", SeekMethod.MediaControls), // Plex
    ).associateBy { it.appId }

    /** The known player with [appId], or null if it isn't one. */
    fun find(appId: String?): KnownPlayer? = appId?.let(players::get)
}
