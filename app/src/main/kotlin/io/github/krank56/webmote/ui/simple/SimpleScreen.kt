package io.github.krank56.webmote.ui.simple

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvApp
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.Route
import io.github.krank56.webmote.ui.apps.AppIcon
import io.github.krank56.webmote.ui.common.Notice
import io.github.krank56.webmote.ui.common.TvScaffold

/** How many favourites Simple mode shows: one row of tiles. */
private const val MAX_FAVOURITES = 4

/** The D-pad section's keys. The arrows are one icon, turned. */
private enum class DPadKey(
    val button: RemoteButton,
    @StringRes val label: Int,
    val icon: ImageVector,
    val rotation: Float = 0f,
) {
    Up(RemoteButton.Up, R.string.remote_up, Icons.Rounded.ArrowUpward),
    Left(RemoteButton.Left, R.string.remote_left, Icons.Rounded.ArrowUpward, rotation = -90f),
    Ok(RemoteButton.Ok, R.string.remote_ok, Icons.Rounded.Check),
    Right(RemoteButton.Right, R.string.remote_right, Icons.Rounded.ArrowUpward, rotation = 90f),
    Down(RemoteButton.Down, R.string.remote_down, Icons.Rounded.ArrowUpward, rotation = 180f),
    Back(RemoteButton.Back, R.string.remote_back, Icons.AutoMirrored.Rounded.Undo),
}

/** The cross's labels, three to a row: a size smaller, so "Gauche" and "Droite" come out as large as "OK". */
private val crossLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium

/**
 * Simple mode's one screen, in place of the tabs: large, labelled buttons, two to a row (three in the
 * arrow cross), whose rows share the screen's height so it never scrolls. No sliders, sheets or menus;
 * Settings sits behind the top bar's hold-to-open gear.
 */
@Composable
fun SimpleScreen(host: SessionHost, tvs: List<SavedTv>, state: TvState, onOpen: (Route) -> Unit) {
    val session = host.session
    val showArrows by host.settings.showArrows.collectAsStateWithLifecycle()
    val pointerOk = state.capabilities.pointer != Capability.Unavailable
    // The TV's first pinned favourites, in the Apps tab's order; the core already lists each app once.
    val favourites = remember(state.apps) { state.apps.filter { it.pinned }.take(MAX_FAVOURITES) }
    var numbersOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = numbersOpen) { numbersOpen = false }
    if (numbersOpen) {
        SimpleNumberPad(session, pointerOk, onClose = { numbersOpen = false })
        return
    }

    TvScaffold(host, tvs, state, onOpen) {
        SimpleColumn {
            PowerSoundAndChannels(session, state)
            if (showArrows) {
                Arrows(enabled = pointerOk, onPress = session::press, onNumbers = { numbersOpen = true })
            } else {
                NumbersButton(onClick = { numbersOpen = true }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
            if (favourites.isNotEmpty()) {
                SimpleRow(favourites.size, Modifier.weight(1f)) {
                    favourites.forEach { app -> FavouriteTile(app, session) }
                }
            }
        }
    }
}

/** Power and Mute, then Volume + and Channel +, then Volume − and Channel −. */
@Composable
private fun ColumnScope.PowerSoundAndChannels(session: TvSession, state: TvState) {
    val muted = state.volume.muted
    val colours = MaterialTheme.colorScheme
    SimpleRow(2, Modifier.weight(1f)) {
        SimpleButton(
            Icons.Rounded.PowerSettingsNew,
            stringResource(R.string.simple_power),
            onClick = session::powerOff,
            contentDescription = stringResource(R.string.power_off),
            containerColor = colours.error,
            contentColor = colours.onError,
        )
        SimpleButton(
            Icons.AutoMirrored.Rounded.VolumeOff,
            stringResource(R.string.simple_mute),
            onClick = { session.setMute(!muted) },
            containerColor = if (muted) colours.primary else colours.secondaryContainer,
            contentColor = if (muted) colours.onPrimary else colours.onSecondaryContainer,
            modifier = Modifier.semantics { toggleableState = ToggleableState(muted) },
        )
    }
    SimpleRow(2, Modifier.weight(1f)) {
        SimpleButton(
            Icons.AutoMirrored.Rounded.VolumeUp,
            stringResource(R.string.simple_volume_up),
            onClick = session::volumeUp,
            repeatOnHold = true,
            contentDescription = stringResource(R.string.remote_volume_up),
        )
        SimpleButton(
            Icons.Rounded.KeyboardArrowUp,
            stringResource(R.string.simple_channel_up),
            onClick = session::channelUp,
            contentDescription = stringResource(R.string.numbers_channel_up),
        )
    }
    SimpleRow(2, Modifier.weight(1f)) {
        SimpleButton(
            Icons.AutoMirrored.Rounded.VolumeDown,
            stringResource(R.string.simple_volume_down),
            onClick = session::volumeDown,
            repeatOnHold = true,
            contentDescription = stringResource(R.string.remote_volume_down),
        )
        SimpleButton(
            Icons.Rounded.KeyboardArrowDown,
            stringResource(R.string.simple_channel_down),
            onClick = session::channelDown,
            contentDescription = stringResource(R.string.numbers_channel_down),
        )
    }
}

/**
 * The D-pad section, a cross three to a row: Up; Left, OK and Right; then 123, Down and Back. 123 takes
 * the corner that would be empty, so it needs no row of its own.
 */
@Composable
private fun ColumnScope.Arrows(enabled: Boolean, onPress: (RemoteButton) -> Unit, onNumbers: () -> Unit) {
    if (!enabled) Notice(stringResource(R.string.remote_pointer_unavailable))
    SimpleRow(3, Modifier.weight(1f)) {
        Spacer(Modifier)
        DPadButton(DPadKey.Up, enabled, onPress)
    }
    SimpleRow(3, Modifier.weight(1f)) {
        DPadButton(DPadKey.Left, enabled, onPress)
        DPadButton(DPadKey.Ok, enabled, onPress)
        DPadButton(DPadKey.Right, enabled, onPress)
    }
    SimpleRow(3, Modifier.weight(1f)) {
        NumbersButton(onClick = onNumbers, labelStyle = crossLabelStyle)
        DPadButton(DPadKey.Down, enabled, onPress)
        DPadButton(DPadKey.Back, enabled, onPress)
    }
}

@Composable
private fun DPadButton(key: DPadKey, enabled: Boolean, onPress: (RemoteButton) -> Unit) {
    val colours = MaterialTheme.colorScheme
    val ok = key == DPadKey.Ok
    SimpleButton(
        label = stringResource(key.label),
        onClick = { onPress(key.button) },
        enabled = enabled,
        containerColor = if (ok) colours.primaryContainer else colours.secondaryContainer,
        contentColor = if (ok) colours.onPrimaryContainer else colours.onSecondaryContainer,
        labelStyle = crossLabelStyle,
    ) {
        Icon(key.icon, contentDescription = null, modifier = Modifier.rotate(key.rotation))
    }
}

/** 123, which opens the number pad. */
@Composable
private fun NumbersButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    SimpleButton(
        Icons.Rounded.Dialpad,
        stringResource(R.string.remote_numbers),
        onClick = onClick,
        modifier = modifier,
        contentDescription = stringResource(R.string.simple_numbers_description),
        labelStyle = labelStyle,
    )
}

/** One of the TV's pinned favourites: its icon and name. A tap opens it. */
@Composable
private fun FavouriteTile(app: TvApp, session: TvSession) {
    SimpleButton(label = app.title, onClick = { session.launchApp(app.id) }) {
        AppIcon(app, session, size = SimpleIconSize)
    }
}
