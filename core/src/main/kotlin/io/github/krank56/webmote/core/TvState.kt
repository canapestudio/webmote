package io.github.krank56.webmote.core

/** Everything the app can observe about the active TV, as reported by [TvSession.state]. */
public data class TvState(
    /** The registry ID of the TV this session targets, or null when no TV is active. */
    val tvId: String? = null,
)
