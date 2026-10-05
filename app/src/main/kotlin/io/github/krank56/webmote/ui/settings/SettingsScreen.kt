package io.github.krank56.webmote.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krank56.webmote.AppSettings
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.Route
import io.github.krank56.webmote.ui.common.BackScaffold
import io.github.krank56.webmote.ui.common.SectionHeader

/**
 * Settings: manage the saved TVs (use, rename, re-pair, forget, add), the remote's preferences, and
 * the way to Diagnostics and the licences. [onDone] returns to the remote, e.g. to show a re-pair.
 */
@Composable
fun SettingsScreen(
    host: SessionHost,
    tvs: List<SavedTv>,
    state: TvState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    val session = host.session
    val settings = host.settings
    val haptics by settings.haptics.collectAsStateWithLifecycle()
    val volumeKeys by settings.volumeKeys.collectAsStateWithLifecycle()
    val sensitivity by settings.touchpadSensitivity.collectAsStateWithLifecycle()
    val registryActiveId by host.registry.activeTvId.collectAsStateWithLifecycle()
    val activeId = state.tvId ?: registryActiveId
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var forgettingId by rememberSaveable { mutableStateOf<String?>(null) }

    BackScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item { Header(stringResource(R.string.settings_tvs)) }
            items(tvs, key = { it.id }) { tv ->
                val active = tv.id == activeId
                TvRow(
                    tv = tv,
                    active = active,
                    onUse = {
                        host.onUserInteraction()
                        session.switchTo(tv.id)
                        onDone()
                    },
                    onRename = { renamingId = tv.id },
                    onRepair = {
                        host.onUserInteraction()
                        session.repair(tv.id)
                        onDone()
                    },
                    onForget = { forgettingId = tv.id },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_add_tv)) },
                    leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    modifier = Modifier.clickable(role = Role.Button) { onOpen(Route.AddTv) },
                )
            }

            item { Header(stringResource(R.string.settings_remote)) }
            item {
                SwitchRow(
                    icon = Icons.Rounded.Vibration,
                    title = stringResource(R.string.settings_haptics),
                    summary = stringResource(R.string.settings_haptics_summary),
                    checked = haptics,
                    onChange = settings::setHaptics,
                )
            }
            item {
                SwitchRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = stringResource(R.string.settings_volume_keys),
                    summary = stringResource(R.string.settings_volume_keys_summary),
                    checked = volumeKeys,
                    onChange = settings::setVolumeKeys,
                )
            }
            item { SensitivityRow(sensitivity = sensitivity, onChange = settings::setTouchpadSensitivity) }

            item { Header(stringResource(R.string.settings_about)) }
            item {
                LinkRow(
                    icon = Icons.Rounded.BugReport,
                    title = stringResource(R.string.diagnostics_title),
                    summary = stringResource(R.string.settings_diagnostics_summary),
                    onClick = { onOpen(Route.Diagnostics) },
                )
            }
            item {
                LinkRow(
                    icon = Icons.Rounded.Gavel,
                    title = stringResource(R.string.licences_title),
                    summary = stringResource(R.string.settings_licences_summary),
                    onClick = { onOpen(Route.Licences) },
                )
            }
        }
    }

    tvs.firstOrNull { it.id == renamingId }?.let { tv ->
        RenameDialog(
            currentName = tv.name,
            onDismiss = { renamingId = null },
            onRename = { name ->
                renamingId = null
                // The registry writes to disk; keep it off the main thread.
                session.rename(tv.id, name)
            },
        )
    }
    tvs.firstOrNull { it.id == forgettingId }?.let { tv ->
        ForgetDialog(
            name = tv.name,
            onDismiss = { forgettingId = null },
            onForget = {
                forgettingId = null
                session.forget(tv.id)
            },
        )
    }
}

@Composable
private fun Header(text: String) {
    SectionHeader(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun TvRow(
    tv: SavedTv,
    active: Boolean,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onRepair: () -> Unit,
    onForget: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val details = listOfNotNull(tv.model, tv.host).joinToString(" · ")
    val activeLabel = stringResource(R.string.settings_tv_active)
    ListItem(
        headlineContent = { Text(tv.name) },
        supportingContent = {
            Text(if (active) stringResource(R.string.settings_tv_active_details, details) else details)
        },
        leadingContent = {
            Icon(
                if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Tv,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Box {
                val moreDescription = stringResource(R.string.settings_tv_more, tv.name)
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = moreDescription }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!active) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_tv_use)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onUse()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_tv_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_repair)) },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRepair()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_tv_forget), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onForget()
                        },
                    )
                }
            }
        },
        modifier = Modifier.semantics { if (active) stateDescription = activeLabel },
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, summary: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SensitivityRow(sensitivity: Float, onChange: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(sensitivity) }
    LaunchedEffect(sensitivity) { value = sensitivity }
    val title = stringResource(R.string.settings_sensitivity)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_sensitivity_value, value))
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    onValueChangeFinished = { onChange(value) },
                    valueRange = AppSettings.MIN_SENSITIVITY..AppSettings.MAX_SENSITIVITY,
                    modifier = Modifier.semantics { contentDescription = title },
                )
            }
        },
        leadingContent = { Icon(Icons.Rounded.Mouse, contentDescription = null) },
    )
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_rename_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onRename(name.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name.trim()) }, enabled = valid) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ForgetDialog(name: String, onDismiss: () -> Unit, onForget: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_forget_title, name)) },
        text = { Text(stringResource(R.string.settings_forget_body)) },
        confirmButton = {
            TextButton(
                onClick = onForget,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.settings_tv_forget)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
