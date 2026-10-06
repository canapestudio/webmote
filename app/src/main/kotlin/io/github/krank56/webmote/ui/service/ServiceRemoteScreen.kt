package io.github.krank56.webmote.ui.service

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.Notice
import io.github.krank56.webmote.ui.common.WholeWordsAutoSize
import io.github.krank56.webmote.ui.common.tvName
import io.github.krank56.webmote.ui.theme.LocalWarningColours

/** The service menus row: the three service menus, each asking first, and Power. */
private val serviceMenuKeys = listOf(
    RemoteButton.InStart to R.string.service_key_instart,
    RemoteButton.EzAdjust to R.string.service_key_ezadjust,
    RemoteButton.AdvancedSetting to R.string.service_key_advanced_setting,
    RemoteButton.Power to R.string.service_key_power,
)

/** The arrow cross, three to a row, with Back and Exit in its bottom corners; null is an empty cell. */
private val crossKeys = listOf(
    null, RemoteButton.Up, null,
    RemoteButton.Left, RemoteButton.Ok, RemoteButton.Right,
    RemoteButton.Back, RemoteButton.Down, RemoteButton.Exit,
)

/**
 * The number pad, four to a row so that it's no taller than the cross beside it: the digits as on a phone,
 * with * and 0 after them. Null is an empty cell.
 */
private val numberKeys = listOf(
    RemoteButton.Num1, RemoteButton.Num2, RemoteButton.Num3, null,
    RemoteButton.Num4, RemoteButton.Num5, RemoteButton.Num6, RemoteButton.Asterisk,
    RemoteButton.Num7, RemoteButton.Num8, RemoteButton.Num9, RemoteButton.Num0,
)

/** The space between the cross and the number pad. */
private val GroupGap = 12.dp

/** The top bar's title and the TV's name under it. */
private val TitleHeight = 28.dp
private val SubtitleHeight = 20.dp

/**
 * The service remote, behind Settings → Advanced: every key the TV knows, acting on the active TV. Always
 * visible are LG's service menus (each asking before it's sent) and Power, the arrows with OK, Back and
 * Exit, and a number pad; four tabs hold the rest. The first time it opens, a warning says what service
 * menus do; Cancel goes back. The key rows share the screen's height, so it doesn't scroll.
 */
@Composable
fun ServiceRemoteScreen(host: SessionHost, tvs: List<SavedTv>, state: TvState, onBack: () -> Unit) {
    val session = host.session
    val warningAccepted by host.settings.serviceWarningAccepted.collectAsStateWithLifecycle()
    val registryActiveId by host.registry.activeTvId.collectAsStateWithLifecycle()
    val name = tvName(tvs, state.tvId ?: registryActiveId)
    val pointerOk = state.capabilities.pointer != Capability.Unavailable
    var group by rememberSaveable { mutableStateOf(KeyGroup.entries.first()) }
    var confirming by rememberSaveable { mutableStateOf<RemoteButton?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val press: (RemoteButton) -> Unit = { button ->
        if (button.opensServiceMenu) confirming = button else session.press(button)
    }

    Scaffold(topBar = { ServiceTopBar(name, onBack) }) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            KeyRowsColumn(
                sections = listOfNotNull(
                    if (pointerOk) null else Section(rows = 0) { Notice(stringResource(R.string.service_pointer_unavailable)) },
                    Section(rows = 1) { ServiceMenus(pointerOk, press) },
                    Section(rows = 3) { ArrowsAndNumbers(pointerOk, press) },
                    Section(rows = 0) { GroupTabs(selected = group, onSelect = { group = it }) },
                    Section(rows = TabRows) { GroupKeys(group, pointerOk, press) },
                ),
                // No top padding: the top bar leaves room enough, and a small phone needs every dp.
                modifier = Modifier.widthIn(max = 480.dp).fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
    }

    if (!warningAccepted && !leaving) {
        WarningDialog(
            onAccept = host.settings::acceptServiceWarning,
            onCancel = {
                // The screen fades out after this: don't leave the dialog up for a second tap to go back twice.
                leaving = true
                onBack()
            },
        )
    }
    confirming?.let { button ->
        ConfirmDialog(
            key = stringResource(serviceMenuLabel(button)),
            tvName = name,
            onOpen = {
                confirming = null
                session.press(button)
            },
            onDismiss = { confirming = null },
        )
    }
}

@StringRes
private fun serviceMenuLabel(button: RemoteButton): Int = serviceMenuKeys.first { it.first == button }.second

/** The back arrow, then "Service remote" over the TV's name. At large font sizes both shrink to fit the bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceTopBar(tvName: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                FittedLine(stringResource(R.string.service_title), MaterialTheme.typography.titleLarge, Modifier.height(TitleHeight))
                FittedLine(
                    tvName,
                    MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    Modifier.height(SubtitleHeight),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
    )
}

/** One line of [text] that shrinks to fit the height it's given, and ends in "…" if it's still too long. */
@Composable
private fun FittedLine(text: String, style: TextStyle, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            style = style.copy(lineHeight = 1.2.em),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = WholeWordsAutoSize(minSize = 12.dp, maxSize = style.fontSize),
        )
    }
}

/** InStart, EzAdjust and Advanced setting, in the warning colour and marked, then Power. */
@Composable
private fun ServiceMenus(enabled: Boolean, onPress: (RemoteButton) -> Unit) {
    val warning = LocalWarningColours.current
    KeyGrid(columns = 4, rows = 1) {
        serviceMenuKeys.forEach { (button, label) ->
            val text = stringResource(label)
            ServiceKey(
                button = button,
                description = if (button.opensServiceMenu) stringResource(R.string.service_menu_description, text) else text,
                onPress = onPress,
                enabled = enabled,
                containerColor = warning.container,
                contentColor = warning.onContainer,
            ) {
                KeyLabel(text, warning = button.opensServiceMenu)
            }
        }
    }
}

/** The arrow cross on the left, the number pad on the right, in every layout direction. */
@Composable
private fun ArrowsAndNumbers(enabled: Boolean, onPress: (RemoteButton) -> Unit) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Absolute.spacedBy(GroupGap)) {
        KeyGrid(columns = 3, rows = 3, modifier = Modifier.weight(3f)) {
            crossKeys.forEach { button ->
                if (button == null) Spacer(Modifier) else CrossKey(button, enabled, onPress)
            }
        }
        KeyGrid(columns = 4, rows = 3, modifier = Modifier.weight(4f)) {
            numberKeys.forEach { button ->
                if (button == null) Spacer(Modifier) else NumberKey(button, enabled, onPress)
            }
        }
    }
}

@Composable
private fun CrossKey(button: RemoteButton, enabled: Boolean, onPress: (RemoteButton) -> Unit) {
    val colours = MaterialTheme.colorScheme
    val ok = button == RemoteButton.Ok
    val arrow = when (button) {
        RemoteButton.Up -> 0f to R.string.remote_up
        RemoteButton.Right -> 90f to R.string.remote_right
        RemoteButton.Down -> 180f to R.string.remote_down
        RemoteButton.Left -> -90f to R.string.remote_left
        else -> null
    }
    val label = when (button) {
        RemoteButton.Ok -> R.string.remote_ok
        RemoteButton.Back -> R.string.remote_back
        else -> R.string.service_key_exit
    }
    val description = stringResource(arrow?.second ?: label)
    ServiceKey(
        button = button,
        description = description,
        onPress = onPress,
        enabled = enabled,
        containerColor = if (ok) colours.primaryContainer else colours.secondaryContainer,
        contentColor = if (ok) colours.onPrimaryContainer else colours.onSecondaryContainer,
    ) {
        if (arrow != null) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.rotate(arrow.first))
        } else {
            KeyLabel(description, style = if (ok) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NumberKey(button: RemoteButton, enabled: Boolean, onPress: (RemoteButton) -> Unit) {
    val colours = MaterialTheme.colorScheme
    val star = button == RemoteButton.Asterisk
    val text = if (star) stringResource(R.string.service_key_asterisk) else button.name.removePrefix("Num")
    ServiceKey(
        button = button,
        description = if (star) stringResource(R.string.service_key_asterisk_description) else text,
        onPress = onPress,
        enabled = enabled,
        containerColor = colours.secondaryContainer,
        contentColor = colours.onSecondaryContainer,
    ) {
        KeyLabel(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Says, the first time the service remote opens, what service menus do. Anything but "I understand" goes back. */
@Composable
private fun WarningDialog(onAccept: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
        iconContentColor = LocalWarningColours.current.accent,
        title = { Text(stringResource(R.string.service_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.service_warning_keys))
                Text(stringResource(R.string.service_warning_risk))
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.service_warning_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Asks before [key], a service menu, is sent to [tvName]. Webmote never suggests a service code. */
@Composable
private fun ConfirmDialog(key: String, tvName: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
        iconContentColor = LocalWarningColours.current.accent,
        title = { Text(stringResource(R.string.service_confirm_title, key)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.service_confirm_risk, key, tvName))
                Text(stringResource(R.string.service_confirm_code))
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.service_confirm_open, key)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
