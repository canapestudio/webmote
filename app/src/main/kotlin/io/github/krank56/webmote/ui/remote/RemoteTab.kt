package io.github.krank56.webmote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.LabelledIconButton
import io.github.krank56.webmote.ui.common.LabelledIconToggleButton
import io.github.krank56.webmote.ui.common.Notice
import io.github.krank56.webmote.ui.common.RemoteIconButton
import io.github.krank56.webmote.ui.common.TvSlider
import kotlin.math.max

/**
 * Everyday control: the D-pad with Mute, Settings, Back and Home around it, and volume. It fits the
 * screen without scrolling: the D-pad gets the height the rows below leave.
 */
@Composable
fun RemoteTab(session: TvSession, state: TvState) {
    val pointerOk = state.capabilities.pointer != Capability.Unavailable

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        FitColumn(
            spacing = 8.dp,
            fill = {
                // The notice takes its room from the D-pad, never from the rows below.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!pointerOk) Notice(stringResource(R.string.remote_pointer_unavailable))
                    DPadAndTvKeys(session, muted = state.volume.muted, enabled = pointerOk, modifier = Modifier.weight(1f))
                }
            },
            modifier = Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Sound(state = state, session = session)
        }
    }
}

/**
 * Fills the height: [fill] gets what the [rows] under it leave, so nothing scrolls. Only when that's
 * less than [fill]'s minimum height (a split screen, or a notice at a large font size on a small
 * phone) does the column grow past the screen and scroll, rather than overlap.
 */
@Composable
private fun FitColumn(
    spacing: Dp,
    fill: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    rows: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val screenHeight = constraints.maxHeight
        Layout(
            contents = listOf(fill, rows),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) { (fillMeasurables, rowMeasurables), constraints ->
            val gap = spacing.roundToPx()
            val width = constraints.maxWidth
            val rowPlaceables = rowMeasurables.map { it.measure(Constraints(maxWidth = width)) }
            val rowsHeight = rowPlaceables.sumOf { gap + it.height }
            val fillMeasurable = fillMeasurables.single()
            val fillHeight = max(screenHeight - rowsHeight, fillMeasurable.minIntrinsicHeight(width))
            val fillPlaceable = fillMeasurable.measure(Constraints.fixed(width, fillHeight))
            layout(width, fillHeight + rowsHeight) {
                fillPlaceable.placeRelative(0, 0)
                var y = fillHeight
                rowPlaceables.forEach {
                    y += gap
                    it.placeRelative(0, y)
                    y += it.height
                }
            }
        }
    }
}

/** The D-pad, with Mute top left, Settings top right, Back bottom left and Home bottom right. */
@Composable
private fun DPadAndTvKeys(session: TvSession, muted: Boolean, enabled: Boolean, modifier: Modifier) {
    val keyShape = CircleShape
    DPad(
        enabled = enabled,
        onPress = session::press,
        topLeft = {
            // Never disabled: Mute doesn't go through the button connection.
            LabelledIconToggleButton(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                stringResource(R.string.remote_mute),
                checked = muted,
                onCheckedChange = session::setMute,
                shape = keyShape,
            )
        },
        topRight = {
            LabelledIconButton(
                Icons.Rounded.Settings, stringResource(R.string.remote_settings), { session.press(RemoteButton.Settings) },
                enabled = enabled, shape = keyShape,
                contentDescription = stringResource(R.string.remote_settings_description),
            )
        },
        bottomLeft = {
            LabelledIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.remote_back), { session.press(RemoteButton.Back) },
                enabled = enabled, shape = keyShape,
            )
        },
        bottomRight = {
            LabelledIconButton(
                Icons.Rounded.Home, stringResource(R.string.remote_home), { session.press(RemoteButton.Home) },
                enabled = enabled, shape = keyShape,
            )
        },
        modifier = modifier,
    )
}

/** The volume slider, or − and + when there's none. */
@Composable
private fun Sound(state: TvState, session: TvSession) {
    val volume = state.volume
    // A soundbar (or a TV that reports no level) gets − and + in the slider's place.
    val showVolumeSlider = state.capabilities.volumeLevel != Capability.Unavailable && volume.level != null
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Tight vertically: each slider's touch target already leaves room around its track.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (showVolumeSlider) {
                TvSlider(
                    value = volume.level,
                    onDrag = session::dragVolume,
                    onRelease = session::setVolume,
                    label = stringResource(R.string.remote_volume),
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                )
            } else {
                VolumeButtons(session)
            }
        }
    }
}

/** Volume − and +, repeating while held, in a row no higher than the slider they replace. */
@Composable
private fun VolumeButtons(session: TvSession) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(24.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteIconButton(
                Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.remote_volume_down),
                onClick = session::volumeDown,
                repeatOnHold = true,
                size = 48.dp,
            )
            RemoteIconButton(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.remote_volume_up),
                onClick = session::volumeUp,
                repeatOnHold = true,
                size = 48.dp,
            )
        }
    }
}
