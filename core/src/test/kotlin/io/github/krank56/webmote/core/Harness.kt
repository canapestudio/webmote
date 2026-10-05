package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.io.File

/**
 * A [TvSession] wired to a [FakeTv] and a real [TvRegistry] in [directory].
 *
 * The session runs on a coroutine test scheduler, so its own work only runs when a test lets it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Harness(
    private val directory: File,
    val tv: FakeTv = FakeTv(),
) : AutoCloseable {
    val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))

    var registry = TvRegistry(directory)
        private set

    var session = TvSession(registry, scope)
        private set

    val state: TvState get() = session.state.value

    /** Simulates closing and reopening the app: a fresh registry and session over the same files. */
    fun reopen() {
        session.close()
        registry = TvRegistry(directory)
        session = TvSession(registry, scope)
    }

    override fun close() {
        session.close()
        scope.cancel()
        tv.close()
    }
}
