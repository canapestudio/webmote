package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.Received
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.io.File
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A [TvSession] wired to a [FakeTv] and a real [TvRegistry] in [directory].
 *
 * The session runs on a coroutine test scheduler, so its own work only runs when a test lets it.
 * Network I/O is real, so tests wait for its effects with [eventually], which runs the session's
 * pending work while polling in real time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Harness(
    private val directory: File,
    val tv: FakeTv = FakeTv(),
    private val config: SessionConfig = tv.sessionConfig(),
) : AutoCloseable {
    val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))

    var registry = TvRegistry(directory)
        private set

    var session = TvSession(registry, scope, config)
        private set

    val state: TvState get() = session.state.value

    /** Simulates closing and reopening the app: a fresh registry and session over the same files. */
    fun reopen() {
        session.close()
        registry = TvRegistry(directory)
        session = TvSession(registry, scope, config)
    }

    /** Runs the session's pending work until [condition] holds, failing after [timeout] of real time. */
    fun eventually(timeout: Duration = 5.seconds, message: () -> String = { "condition" }, condition: () -> Boolean) {
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            scheduler.runCurrent()
            if (condition()) return
            if (start.elapsedNow() > timeout) fail("Timed out waiting for ${message()}; state: $state")
            Thread.sleep(2)
        }
    }

    /** Waits until the session reports [connection]. */
    fun awaitConnection(connection: ConnectionState, timeout: Duration = 5.seconds) =
        eventually(timeout, { "connection $connection" }) { state.connection == connection }

    /** Waits until the fake TV has received [count] requests for [uri], and returns them. */
    fun awaitRequests(uri: String, count: Int = 1): List<Received> {
        eventually(message = { "$count request(s) for $uri, got ${tv.requests(uri).size}" }) { tv.requests(uri).size >= count }
        return tv.requests(uri)
    }

    /** Lets real I/O settle for [millis] while running the session's work, without moving virtual time. */
    fun settle(millis: Long = 150) {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow().inWholeMilliseconds < millis) {
            scheduler.runCurrent()
            Thread.sleep(2)
        }
    }

    /** Powers the fake TV on, pairs with it by prompt, and waits until connected. */
    fun pair(name: String? = null) {
        tv.powerOn()
        session.connect(tv.host, name)
        awaitConnection(ConnectionState.Connected)
    }

    override fun close() {
        session.close()
        scope.cancel()
        tv.close()
    }
}
