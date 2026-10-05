package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.internal.Connector
import io.github.krank56.webmote.core.internal.PictureControl
import io.github.krank56.webmote.core.internal.PointerControl
import io.github.krank56.webmote.core.internal.PowerControl
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
    private val pointer = PointerControl(core)
    private val picture = PictureControl(core)
    private val power = PowerControl(core, connector)

    init {
        core.features += listOf(power, volume, pointer, picture)
    }

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

    /** Submits the PIN the TV shows, while [ConnectionState.AwaitingPin]. */
    public fun submitPin(pin: String): Unit = onSession { connector.submitPin(pin) }

    /**
     * Discards [tvId]'s client key and certificate pin, makes it the active TV and pairs with it
     * again. By default it's the session's current TV.
     */
    public fun repair(tvId: String? = null): Unit = onSession { connector.repair(tvId) }

    /** Makes [tvId] the active TV: closes the current connection and connects to it. */
    public fun switchTo(tvId: String): Unit = onSession { connector.switchTo(tvId) }

    /**
     * Forgets [tvId] with its key, pin and MACs. If it was the active TV, another saved TV becomes
     * active and the session connects to it.
     */
    public fun forget(tvId: String): Unit = onSession { connector.forget(tvId) }

    /** Changes [tvId]'s display name. Blank names are ignored. */
    public fun rename(tvId: String, name: String): Unit = onSession {
        name.trim().takeIf { it.isNotEmpty() }?.let { registry.rename(tvId, it) }
    }

    /** While the TV is off, keep probing for it and connect as soon as it answers, until [stopOffPolling]. */
    public fun startOffPolling(): Unit = onSession { power.startOffPolling() }

    public fun stopOffPolling(): Unit = onSession { power.stopOffPolling() }

    // Buttons

    public fun press(button: RemoteButton): Unit = onSession { pointer.press(button) }

    // Volume

    public fun volumeUp(): Unit = onSession { volume.up() }

    public fun volumeDown(): Unit = onSession { volume.down() }

    /** A volume slider value while dragging. Throttled; follow it with [setVolume] on release. */
    public fun dragVolume(level: Int): Unit = onSession { volume.drag(level) }

    public fun setVolume(level: Int): Unit = onSession { volume.set(level) }

    public fun setMute(muted: Boolean): Unit = onSession { volume.setMute(muted) }

    // Picture

    /** A picture slider value while dragging. Throttled; follow it with [setPicture] on release. */
    public fun dragPicture(setting: PictureSetting, value: Int): Unit = onSession { picture.drag(setting, value) }

    public fun setPicture(setting: PictureSetting, value: Int): Unit = onSession { picture.set(setting, value) }

    public fun setEnergySaving(mode: EnergySaving): Unit = onSession { picture.setEnergySaving(mode) }

    // Power

    public fun powerOff(): Unit = onSession { power.powerOff() }

    /** Sends a Wake-on-LAN packet to each of the active TV's MACs and connects once it answers. */
    public fun wake(): Unit = onSession { power.wake() }

    /** Turns the picture off; the sound keeps playing. */
    public fun screenOff(): Unit = onSession { power.screenOff() }

    public fun screenOn(): Unit = onSession { power.screenOn() }

    /** Closes the connection and stops the session for good. */
    override fun close(): Unit = core.close()

    private fun onSession(block: () -> Unit) {
        core.scope.launch { block() }
    }
}
