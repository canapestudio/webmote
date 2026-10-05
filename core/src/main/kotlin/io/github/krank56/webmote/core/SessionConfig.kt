package io.github.krank56.webmote.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tunables and network targets for a [TvSession]. The defaults are the production values; tests aim
 * the ports at a fake TV on loopback.
 */
public data class SessionConfig(
    /** The encrypted SSAP port. */
    val port: Int = 3001,
    /** How long to wait for the TV to answer an SSAP request. */
    val requestTimeout: Duration = 10.seconds,
    /** Real-time socket timeout for TCP connections to the TV. */
    val connectTimeout: Duration = 3.seconds,
    /** Where blocking network I/O runs. The registry's small file writes stay on the session's dispatcher. */
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
)
