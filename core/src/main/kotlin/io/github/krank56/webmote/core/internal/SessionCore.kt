package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.SessionConfig
import io.github.krank56.webmote.core.TvRegistry
import io.github.krank56.webmote.core.TvState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.coroutines.ContinuationInterceptor

/**
 * State shared by the session's parts. All session logic runs on [scope], whose dispatcher runs one
 * coroutine at a time, so the parts don't need locks between themselves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SessionCore(
    val registry: TvRegistry,
    parent: CoroutineScope,
    val config: SessionConfig,
) {
    val dispatcher: CoroutineDispatcher =
        ((parent.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher) ?: Dispatchers.Default).limitedParallelism(1)

    val scope: CoroutineScope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]) + dispatcher)

    val state = MutableStateFlow(TvState(tvId = registry.activeTvId.value))

    /** The live connection, while the session is connected. */
    var link: Link? = null

    /** The features to start on every new link, in order. */
    val features = mutableListOf<Feature>()

    /** The base HTTP client. Each TV gets a pinned client derived from it. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun update(change: (TvState) -> TvState) = state.update(change)

    /** Runs [block] against the live link on the session's dispatcher. Does nothing while not connected. */
    fun withLink(block: suspend (Link) -> Unit) {
        scope.launch {
            val link = link ?: return@launch
            try {
                block(link)
            } catch (e: SsapException) {
                // The TV refused the command; there's nothing useful to report for a fire-and-forget command.
            } catch (e: java.io.IOException) {
                // The link is closing; the connector notices and reports it.
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // The TV didn't answer in time.
            }
        }
    }

    fun close() {
        link?.close()
        link = null
        scope.cancel()
    }
}

/** One live, registered connection to one TV. Closing it stops everything launched in [scope]. */
internal class Link(
    val tvId: String,
    val host: String,
    val webOsVersion: String?,
    val socket: SsapSocket,
    /** An HTTP client that trusts only this TV's pinned certificate. */
    val http: OkHttpClient,
    parent: CoroutineScope,
) {
    val scope: CoroutineScope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))

    fun close() {
        scope.cancel()
        socket.close()
    }
}

/** A part of the session that needs to set itself up on every new connection. */
internal interface Feature {
    /**
     * Called on the session's dispatcher once a link reaches the connected state. Work launched in
     * [Link.scope] stops when the link closes.
     */
    fun onConnected(link: Link)
}
