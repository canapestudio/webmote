package io.github.krank56.webmote.ui.remote

import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.MediaKey
import io.github.krank56.webmote.core.PictureSetting
import io.github.krank56.webmote.core.Player
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.core.TvApp
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.apps.AppIcon
import io.github.krank56.webmote.ui.common.LabelledIconButton
import io.github.krank56.webmote.ui.common.LabelledIconToggleButton
import io.github.krank56.webmote.ui.common.Notice
import io.github.krank56.webmote.ui.common.RemoteIconButton
import io.github.krank56.webmote.ui.common.TvSlider
import io.github.krank56.webmote.ui.common.rememberPressAction
import kotlin.math.max

/** The sheets the Remote tab opens. */
private enum class RemoteSheetKind { Numbers, Keyboard, Inputs, Picture }

/**
 * Everyday control: the D-pad with Mute, Settings, Back and Home around it, volume, brightness, the
 * media keys (the player row while a player app is in front), and shortcuts to the sheets. It fits
 * the screen without scrolling: the D-pad gets the height the rows below leave.
 */
@Composable
fun RemoteTab(session: TvSession, state: TvState) {
    var sheet by rememberSaveable { mutableStateOf<RemoteSheetKind?>(null) }
    val pointerOk = state.capabilities.pointer != Capability.Unavailable
    val pictureOk = state.capabilities.pictureWrites != Capability.Unavailable
    val player = state.player

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
            SoundAndPicture(state = state, session = session, pictureOk = pictureOk)
            if (player != null) PlayerRow(player, state.apps, session) else MediaRow(session)
            ShortcutsRow(onOpen = { sheet = it })
        }
    }

    when (sheet) {
        RemoteSheetKind.Numbers -> NumberSheet(session, pointerOk, onDismiss = { sheet = null })
        RemoteSheetKind.Keyboard -> KeyboardSheet(session, onDismiss = { sheet = null })
        RemoteSheetKind.Inputs -> InputSheet(state, session, onDismiss = { sheet = null })
        RemoteSheetKind.Picture -> PictureSheet(state, session, onDismiss = { sheet = null })
        null -> Unit
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

/** The volume slider (or − and + when there's none) and the brightness (backlight) slider. */
@Composable
private fun SoundAndPicture(state: TvState, session: TvSession, pictureOk: Boolean) {
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
            TvSlider(
                value = state.picture.backlight,
                onDrag = { session.dragPicture(PictureSetting.Backlight, it) },
                onRelease = { session.setPicture(PictureSetting.Backlight, it) },
                label = stringResource(R.string.picture_backlight),
                icon = Icons.Rounded.LightMode,
                enabled = pictureOk,
            )
            if (!pictureOk) {
                Text(
                    stringResource(R.string.picture_unavailable_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
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

/** Rewind, play/pause, stop, fast-forward. */
@Composable
private fun MediaRow(session: TvSession) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RemoteIconButton(
            Icons.Rounded.FastRewind,
            stringResource(R.string.remote_rewind),
            onClick = { session.media(MediaKey.Rewind) },
        )
        PlayPauseButton(onClick = session::playPause)
        RemoteIconButton(
            Icons.Rounded.Stop,
            stringResource(R.string.remote_stop),
            onClick = { session.media(MediaKey.Stop) },
        )
        RemoteIconButton(
            Icons.Rounded.FastForward,
            stringResource(R.string.remote_fast_forward),
            onClick = { session.media(MediaKey.FastForward) },
        )
    }
}

/**
 * The media row while a player app is in front: the app's icon, then seek back, play/pause, stop and
 * seek forward. Seeking goes the way the app's player seeks.
 */
@Composable
private fun PlayerRow(player: Player, apps: List<TvApp>, session: TvSession) {
    // Until the TV has listed its apps, the player is named by its ID.
    val app = apps.firstOrNull { it.id == player.appId } ?: TvApp(player.appId, title = player.appId, iconUrl = null, pinned = false)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics { contentDescription = app.title }) {
            AppIcon(app, session, size = 40.dp)
        }
        RemoteIconButton(
            Icons.Rounded.FastRewind,
            stringResource(R.string.player_seek_back),
            onClick = session::seekBack,
        )
        PlayPauseButton(onClick = session::playPause)
        RemoteIconButton(
            Icons.Rounded.Stop,
            stringResource(R.string.remote_stop),
            onClick = { session.media(MediaKey.Stop) },
        )
        RemoteIconButton(
            Icons.Rounded.FastForward,
            stringResource(R.string.player_seek_forward),
            onClick = session::seekForward,
        )
    }
}

/** A single play/pause key: the TV doesn't reliably report playback, so it shows both symbols. */
@Composable
private fun PlayPauseButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val action = rememberPressAction(onClick, interactionSource)
    val description = stringResource(R.string.remote_play_pause)
    FilledTonalIconButton(
        onClick = action,
        interactionSource = interactionSource,
        modifier = Modifier.size(56.dp).semantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** Opens the 123 sheet, the keyboard, the input picker and the Picture sheet. */
@Composable
private fun ShortcutsRow(onOpen: (RemoteSheetKind) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LabelledIconButton(
            Icons.Rounded.Dialpad,
            stringResource(R.string.remote_numbers),
            { onOpen(RemoteSheetKind.Numbers) },
            contentDescription = stringResource(R.string.remote_numbers_description),
        )
        LabelledIconButton(Icons.Rounded.Keyboard, stringResource(R.string.remote_keyboard), { onOpen(RemoteSheetKind.Keyboard) })
        LabelledIconButton(Icons.Rounded.SettingsInputHdmi, stringResource(R.string.remote_inputs), { onOpen(RemoteSheetKind.Inputs) })
        LabelledIconButton(Icons.Rounded.Tune, stringResource(R.string.remote_picture), { onOpen(RemoteSheetKind.Picture) })
    }
}
