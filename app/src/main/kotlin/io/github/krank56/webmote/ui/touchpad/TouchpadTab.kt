package io.github.krank56.webmote.ui.touchpad

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krank56.webmote.AppSettings
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.LocalHaptics
import io.github.krank56.webmote.ui.common.Notice
import io.github.krank56.webmote.ui.common.RemoteIconButton
import kotlin.math.max

/** The Magic Remote-style touchpad: drag moves the TV's pointer, tap clicks, two fingers scroll. */
@Composable
fun TouchpadTab(session: TvSession, settings: AppSettings, state: TvState) {
    val sensitivity by settings.touchpadSensitivity.collectAsStateWithLifecycle()
    val pointerOk = state.capabilities.pointer != Capability.Unavailable
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!pointerOk) Notice(stringResource(R.string.touchpad_unavailable))
        Touchpad(
            enabled = pointerOk,
            sensitivity = sensitivity,
            onMove = session::movePointer,
            onTap = session::click,
            onScroll = session::scroll,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack,
                stringResource(R.string.remote_back),
                onClick = { session.press(RemoteButton.Back) },
                enabled = pointerOk,
            )
            RemoteIconButton(
                Icons.Rounded.Home,
                stringResource(R.string.remote_home),
                onClick = { session.press(RemoteButton.Home) },
                enabled = pointerOk,
            )
        }
    }
}

@Composable
private fun Touchpad(
    enabled: Boolean,
    sensitivity: Float,
    onMove: (Double, Double) -> Unit,
    onTap: () -> Unit,
    onScroll: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val description = stringResource(R.string.touchpad_description)
    val clickLabel = stringResource(R.string.touchpad_click)

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    if (enabled) {
                        onClick(label = clickLabel) {
                            currentOnTap()
                            true
                        }
                    } else {
                        disabled()
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        val tapTimeout = viewConfiguration.longPressTimeoutMillis
                        var fingers = 1
                        var travelled = 0f
                        var moving = false
                        var endTime = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            event.changes.forEach { it.consume() }
                            if (pressed.isEmpty()) {
                                endTime = event.changes.maxOfOrNull { it.uptimeMillis } ?: endTime
                                break
                            }
                            fingers = max(fingers, pressed.size)
                            if (pressed.size >= 2) {
                                // Two fingers: scroll by their average movement.
                                val dx = pressed.sumOf { (it.position.x - it.previousPosition.x).toDouble() } / pressed.size
                                val dy = pressed.sumOf { (it.position.y - it.previousPosition.y).toDouble() } / pressed.size
                                // The session carries sub-pixel remainders over, so slow drags still add up.
                                currentOnScroll(dx, dy)
                            } else if (fingers == 1) {
                                // One finger (and never two in this gesture): move the pointer past the touch slop.
                                val change = pressed.first()
                                val delta: Offset = change.position - change.previousPosition
                                if (!moving) {
                                    travelled += delta.getDistance()
                                    moving = travelled > slop
                                }
                                if (moving) {
                                    val s = currentSensitivity.toDouble()
                                    currentOnMove(delta.x * s, delta.y * s)
                                }
                            }
                        }
                        if (fingers == 1 && !moving && endTime - down.uptimeMillis < tapTimeout) {
                            haptics.press()
                            currentOnTap()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(R.string.touchpad_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
