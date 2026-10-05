package io.github.krank56.webmote.core.internal

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
import kotlin.coroutines.ContinuationInterceptor

/**
 * State shared by the session's parts. All session logic runs on [scope], whose dispatcher runs one
 * coroutine at a time, so the parts don't need locks between themselves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SessionCore(
    val registry: TvRegistry,
    parent: CoroutineScope,
) {
    val dispatcher: CoroutineDispatcher =
        ((parent.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher) ?: Dispatchers.Default).limitedParallelism(1)

    val scope: CoroutineScope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]) + dispatcher)

    val state = MutableStateFlow(TvState(tvId = registry.activeTvId.value))

    fun close() {
        scope.cancel()
    }
}
