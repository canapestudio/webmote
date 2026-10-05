package io.github.krank56.webmote.ui.remote

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.EnergySaving
import io.github.krank56.webmote.core.PictureSetting
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.LocalHaptics
import io.github.krank56.webmote.ui.common.Notice
import io.github.krank56.webmote.ui.common.TvSlider
import kotlinx.coroutines.delay

/** How long a chosen energy-saving mode stays selected while waiting for the TV to report it. */
private const val SETTLE_TIMEOUT_MS = 2_500L

@StringRes
fun PictureSetting.labelRes(): Int = when (this) {
    PictureSetting.Backlight -> R.string.picture_backlight
    PictureSetting.Brightness -> R.string.picture_brightness
    PictureSetting.Contrast -> R.string.picture_contrast
    PictureSetting.Color -> R.string.picture_color
}

@StringRes
private fun EnergySaving.labelRes(): Int = when (this) {
    EnergySaving.Auto -> R.string.energy_auto
    EnergySaving.Off -> R.string.energy_off
    EnergySaving.Min -> R.string.energy_min
    EnergySaving.Med -> R.string.energy_med
    EnergySaving.Max -> R.string.energy_max
}

/** The Picture sheet: backlight, brightness, contrast and colour sliders, and energy saving. */
@Composable
fun PictureSheet(state: TvState, session: TvSession, onDismiss: () -> Unit) {
    val enabled = state.capabilities.pictureWrites != Capability.Unavailable
    RemoteSheet(title = stringResource(R.string.picture_title), onDismiss = onDismiss) {
        if (!enabled) Notice(stringResource(R.string.picture_unavailable))
        PictureSetting.entries.forEach { setting ->
            TvSlider(
                value = state.picture[setting],
                onDrag = { session.dragPicture(setting, it) },
                onRelease = { session.setPicture(setting, it) },
                label = stringResource(setting.labelRes()),
                showLabel = true,
                enabled = enabled,
            )
        }
        EnergySavingChooser(
            current = state.picture.energySaving,
            enabled = enabled,
            onSelect = session::setEnergySaving,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnergySavingChooser(current: EnergySaving?, enabled: Boolean, onSelect: (EnergySaving) -> Unit) {
    val haptics = LocalHaptics.current
    // Keep the user's choice selected until the TV reports it, as the sliders do.
    var pending by remember { mutableStateOf<EnergySaving?>(null) }
    LaunchedEffect(pending, current) {
        val target = pending ?: return@LaunchedEffect
        if (current != target) delay(SETTLE_TIMEOUT_MS)
        pending = null
    }
    val selected = pending ?: current
    val modes = EnergySaving.entries
    Column(Modifier.alpha(if (enabled) 1f else 0.6f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.picture_energy_saving), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = {
                        haptics.press()
                        pending = mode
                        onSelect(mode)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    enabled = enabled,
                    icon = {},
                    label = { Text(stringResource(mode.labelRes()), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) },
                )
            }
        }
    }
}
