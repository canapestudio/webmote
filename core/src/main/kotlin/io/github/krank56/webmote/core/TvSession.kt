package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.internal.Connector
import io.github.krank56.webmote.core.internal.SessionCore
import io.github.krank56.webmote.core.internal.VolumeControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The app's one interface to its TVs: it owns a single connection to the active TV, and reports
 * everything about it through [state].
 *
 * Commands are fire-and-forget and safe to call from any thread. Commands that need a connection
 * are ignored while the session isn't connected.
 */
public class TvSession(
    private val registry: TvRegistry,
    scope: CoroutineScope,
    config: SessionConfig = SessionConfig(),
) : AutoCloseable {
    private val core = SessionCore(registry, scope, config)
    private val connector = Connector(core)
    private val volume = VolumeControl(core)

    public val state: StateFlow<TvState> = core.state.asStateFlow()

    // Connection and pairing

    /** Connects to the active TV. Does nothing when there's none, or it's already connected or connecting. */
    public fun connect(): Unit = onSession { connector.connect() }

    /**
     * Connects to the TV at [host], pairing with it if it isn't saved yet. Once connected it's saved
     * and becomes the active TV; [name] is its display name if it's new.
     */
    public fun connect(host: String, name: String? = null): Unit = onSession { connector.connect(host, name) }

    public fun disconnect(): Unit = onSession { connector.disconnect() }

    // Volume

    public fun volumeUp(): Unit = onSession { volume.up() }

    public fun volumeDown(): Unit = onSession { volume.down() }

    /** Closes the connection and stops the session for good. */
    override fun close(): Unit = core.close()

    private fun onSession(block: () -> Unit) {
        core.scope.launch { block() }
    }
}
