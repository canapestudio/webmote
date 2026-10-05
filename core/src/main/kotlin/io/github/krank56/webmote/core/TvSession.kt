package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.internal.SessionCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's one interface to its TVs: it reports everything about the active TV through [state]. */
public class TvSession(
    private val registry: TvRegistry,
    scope: CoroutineScope,
) : AutoCloseable {
    private val core = SessionCore(registry, scope)

    public val state: StateFlow<TvState> = core.state.asStateFlow()

    /** Stops the session for good. */
    override fun close(): Unit = core.close()
}
