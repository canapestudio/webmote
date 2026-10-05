package io.github.krank56.webmote.core.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Rate-limits slider writes: while dragging, at most one value per [interval] reaches [send], always
 * the latest; the value on release is sent straight away, unless the drag already sent it.
 *
 * Must be used from the session's dispatcher.
 */
internal class Throttle<T : Any>(
    private val scope: CoroutineScope,
    private val interval: Duration,
    private val send: (T) -> Unit,
) {
    private var cooldown: Job? = null
    private var pending: T? = null
    private var lastSent: T? = null
    private var dragging = false

    fun drag(value: T) {
        dragging = true
        if (cooldown?.isActive == true) {
            pending = value
        } else {
            emit(value)
        }
    }

    fun release(value: T) {
        // Only a value this gesture already sent is a repeat; the TV may have moved since an earlier one.
        val repeat = dragging && value == lastSent
        dragging = false
        cooldown?.cancel()
        pending = null
        if (!repeat) emit(value)
    }

    private fun emit(value: T) {
        send(value)
        lastSent = value
        cooldown = scope.launch {
            delay(interval)
            val next = pending ?: return@launch
            pending = null
            emit(next)
        }
    }
}
