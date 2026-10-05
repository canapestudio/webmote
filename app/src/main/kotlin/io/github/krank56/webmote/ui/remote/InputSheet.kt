package io.github.krank56.webmote.ui.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.LocalHaptics

/** The input picker: the TV's inputs (HDMI 1, 2…); tapping one switches to it. */
@Composable
fun InputSheet(state: TvState, session: TvSession, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    RemoteSheet(title = stringResource(R.string.inputs_title), onDismiss = onDismiss) { dismiss ->
        if (state.inputs.isEmpty()) {
            Text(
                stringResource(R.string.inputs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            state.inputs.forEach { input ->
                ListItem(
                    headlineContent = { Text(input.label) },
                    leadingContent = { Icon(Icons.Rounded.SettingsInputHdmi, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) {
                            haptics.press()
                            session.switchInput(input.id)
                            dismiss()
                        },
                )
            }
        }
    }
}
