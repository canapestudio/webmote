package io.github.krank56.webmote.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.SavedTv

/** The display name of the TV with [id], or a generic "TV" when it isn't known. */
@Composable
fun tvName(tvs: List<SavedTv>, id: String?): String =
    tvs.firstOrNull { it.id == id }?.name ?: stringResource(R.string.tv_generic_name)

/** What the top bar's power button does, or null to hide it. */
data class PowerAction(val turnsOn: Boolean, val onClick: () -> Unit)

/**
 * The top bar shared by the remote and the TV's state screens: the active TV's name, a switcher when
 * more than one TV is saved, power and Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvTopBar(
    tvs: List<SavedTv>,
    activeTvId: String?,
    onSwitch: (String) -> Unit,
    onSettings: () -> Unit,
    power: PowerAction? = null,
) {
    val haptics = LocalHaptics.current
    TopAppBar(
        title = { TvSwitcher(tvs, activeTvId, onSwitch) },
        actions = {
            if (power != null) {
                val description = stringResource(if (power.turnsOn) R.string.power_on else R.string.power_off)
                IconButton(
                    onClick = {
                        haptics.press()
                        power.onClick()
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = description)
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        },
    )
}

@Composable
private fun TvSwitcher(tvs: List<SavedTv>, activeTvId: String?, onSwitch: (String) -> Unit) {
    val name = tvName(tvs, activeTvId)
    if (tvs.size <= 1) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val switchDescription = stringResource(R.string.switch_tv_description, name)
    Box {
        TextButton(
            onClick = { expanded = true },
            // Line the name up with a plain title; the button's own padding would push it right.
            modifier = Modifier.offset(x = (-12).dp).semantics { contentDescription = switchDescription },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tvs.forEach { tv ->
                val active = tv.id == activeTvId
                DropdownMenuItem(
                    text = { Text(tv.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        if (!active) onSwitch(tv.id)
                    },
                    leadingIcon = {
                        if (active) {
                            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.switch_tv_active))
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}
