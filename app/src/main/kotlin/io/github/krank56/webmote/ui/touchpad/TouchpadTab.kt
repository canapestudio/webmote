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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

/**
 * The Magic Remote-style touchpad: drag moves the TV's pointer, tap clicks, two fingers scroll. With
 * Point with phone on (offered only on phones with a gyroscope), holding it and turning the phone
 * points.
 */
@Composable
fun TouchpadTab(session: TvSession, settings: AppSettings, state: TvState) {
    val sensitivity by settings.touchpadSensitivity.collectAsStateWithLifecycle()
    val pointWithPhone by settings.pointWithPhone.collectAsStateWithLifecycle()
    val pointingSpeed by settings.pointingSpeed.collectAsStateWithLifecycle()
    val hasGyroscope = rememberHasGyroscope()
    val pointerOk = state.capabilities.pointer != Capability.Unavailable
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!pointerOk) Notice(stringResource(R.string.touchpad_unavailable))
        if (hasGyroscope) {
            PointWithPhoneSwitch(checked = pointWithPhone, enabled = pointerOk, onChange = settings::setPointWithPhone)
        }
        Touchpad(
            enabled = pointerOk,
            sensitivity = sensitivity,
            pointWithPhone = hasGyroscope && pointWithPhone,
            pointingSpeed = pointingSpeed,
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

/** Turns pointing with the phone on and off. */
@Composable
private fun PointWithPhoneSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Rounded.ScreenRotation, contentDescription = null, modifier = Modifier.alpha(if (enabled) 1f else 0.5f))
        Text(
            stringResource(R.string.touchpad_point_with_phone),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The touchpad's surface. With [pointWithPhone], a press held still for [HOLD_TO_POINT_MS] points
 * with the phone, with a haptic tick, until the finger lifts: finger movement is then ignored, and a
 * second finger switches to scrolling for the rest of the gesture.
 */
@Composable
private fun Touchpad(
    enabled: Boolean,
    sensitivity: Float,
    pointWithPhone: Boolean,
    pointingSpeed: Float,
    onMove: (Double, Double) -> Unit,
    onTap: () -> Unit,
    onScroll: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val currentPointWithPhone by rememberUpdatedState(pointWithPhone)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val phone = rememberPhonePointer(pointingSpeed, onMove)
    val pointing = phone.pointing
    val description = stringResource(R.string.touchpad_description)
    val clickLabel = stringResource(R.string.touchpad_click)
    // Tells TalkBack users which mode the touchpad is in.
    val mode = when {
        pointing -> stringResource(R.string.touchpad_pointing)
        pointWithPhone -> stringResource(R.string.touchpad_point_with_phone)
        else -> null
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (pointing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    if (mode != null) stateDescription = mode
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
                        // With Point with phone on, a press held still past HOLD_TO_POINT_MS points instead of clicking.
                        val holdToPoint = currentPointWithPhone
                        val tapTimeout = if (holdToPoint) HOLD_TO_POINT_MS else viewConfiguration.longPressTimeoutMillis
                        var fingers = 1
                        var travelled = 0f
                        var moving = false
                        var pointed = false
                        var lastTime = down.uptimeMillis
                        try {
                            while (true) {
                                val event = if (holdToPoint && fingers == 1 && !moving && !pointed) {
                                    val left = down.uptimeMillis + HOLD_TO_POINT_MS - lastTime
                                    if (left > 0) withTimeoutOrNull(left) { awaitPointerEvent() } else null
                                } else {
                                    awaitPointerEvent()
                                }
                                if (event == null) {
                                    // Held still: point with the phone until the finger lifts.
                                    pointed = true
                                    haptics.press()
                                    phone.start()
                                    continue
                                }
                                val pressed = event.changes.filter { it.pressed }
                                event.changes.forEach { it.consume() }
                                lastTime = event.changes.maxOfOrNull { it.uptimeMillis } ?: lastTime
                                if (pressed.isEmpty()) break
                                fingers = max(fingers, pressed.size)
                                if (pressed.size >= 2) {
                                    // A second finger ends pointing; the rest of the gesture scrolls.
                                    if (pointed) phone.stop()
                                    // Two fingers: scroll by their average movement.
                                    val dx = pressed.sumOf { (it.position.x - it.previousPosition.x).toDouble() } / pressed.size
                                    val dy = pressed.sumOf { (it.position.y - it.previousPosition.y).toDouble() } / pressed.size
                                    // The session carries sub-pixel remainders over, so slow drags still add up.
                                    currentOnScroll(dx, dy)
                                } else if (fingers == 1 && !pointed) {
                                    // One finger (and never two in this gesture): move the pointer past the touch slop.
                                    // While pointing, the finger's movement is ignored.
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
                        } finally {
                            // Lifting the finger ends pointing, as does the touchpad going away mid-gesture.
                            if (pointed) phone.stop()
                        }
                        if (fingers == 1 && !moving && !pointed && lastTime - down.uptimeMillis < tapTimeout) {
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
                val color = if (pointing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(
                    if (pointing) Icons.Rounded.ScreenRotation else Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(
                        when {
                            pointing -> R.string.touchpad_pointing_hint
                            pointWithPhone -> R.string.touchpad_point_hint
                            else -> R.string.touchpad_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** How long a press must be held still, with Point with phone on, to start pointing. */
private const val HOLD_TO_POINT_MS = 200L
