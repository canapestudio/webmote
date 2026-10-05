package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration

// Helpers shared by the power, wake, off-state, lifetime and screen-off tests.

/** Pairs with the fake TV and waits until both of its MACs are stored. */
fun Harness.pairAndLearnMacs() {
    pair()
    eventually(message = { "both MACs stored" }) { registry[tv.uuid]?.let { it.wiredMac != null && it.wifiMac != null } == true }
}

/** Pairs, learns the MACs, then turns the TV off with its own remote: the session reports it off. */
fun Harness.pairThenTurnTvOff() {
    pairAndLearnMacs()
    tv.powerOff()
    awaitConnection(ConnectionState.Off)
}

/** How often the session probes a waking or off TV, as configured for the fake TV. */
val Harness.pollInterval: Duration get() = tv.sessionConfig().pollInterval

/** How long a wake waits for the TV, as configured for the fake TV. */
val Harness.wakeTimeout: Duration get() = tv.sessionConfig().wakeTimeout

/** The magic packets the fake TV received for [mac]. */
fun FakeTv.magicPacketsFor(mac: String): List<ByteArray> =
    magicPackets.filter { FakeTv.isMagicPacketFor(it, FakeTv.macBytes(mac)) }

/** How many times the fake TV received a `register`: one per full connection. */
val FakeTv.registrations: Int get() = requests.count { it.type == "register" }

/** Records every connection state [session] reports, from now until [close]. */
class ConnectionRecorder(session: TvSession) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    val states: MutableList<ConnectionState> = CopyOnWriteArrayList()

    init {
        scope.launch { session.state.collect { states += it.connection } }
    }

    override fun close() = scope.cancel()
}
