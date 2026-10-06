package io.github.krank56.webmote.ui.simple

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.ui.common.LocalHaptics
import kotlinx.coroutines.launch

/** How long the gear has to be held to open Settings. */
private const val HOLD_TO_OPEN_MS = 2_000

/**
 * Simple mode's top bar: the hold-to-open Settings gear, so a stray tap can't change anything, and, with
 * two or more TVs saved, one large button per TV.
 */
@Composable
fun SimpleTopBar(tvs: List<SavedTv>, activeTvId: String?, onSwitch: (String) -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            HoldToOpenGear(onOpen = onSettings)
        }
        if (tvs.size > 1) TvPicker(tvs, activeTvId, onSwitch)
    }
}

/** One button per saved TV, two to a row, the active one highlighted; a tap on another switches to it. */
@Composable
private fun TvPicker(tvs: List<SavedTv>, activeTvId: String?, onSwitch: (String) -> Unit) {
    val colours = MaterialTheme.colorScheme
    SimpleContent(verticalPadding = 0.dp) {
        tvs.chunked(2).forEach { row ->
            SimpleRow(2) {
                row.forEach { tv ->
                    val active = tv.id == activeTvId
                    SimpleButton(
                        Icons.Rounded.Tv,
                        tv.name,
                        onClick = { if (!active) onSwitch(tv.id) },
                        containerColor = if (active) colours.primary else colours.secondaryContainer,
                        contentColor = if (active) colours.onPrimary else colours.onSecondaryContainer,
                        modifier = Modifier.semantics { selected = active },
                    )
                }
            }
        }
    }
}

/**
 * A small gear that opens Settings only once it's been held for about two seconds, with a ring that
 * fills while it's held; a short tap does nothing. TalkBack and Switch Access open it with their
 * long-press action, which a stray touch can't trigger either.
 */
@Composable
fun HoldToOpenGear(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val currentOnOpen by rememberUpdatedState(onOpen)
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val description = stringResource(R.string.settings_title)
    val holdLabel = stringResource(R.string.simple_settings_hold)
    Box(
        modifier
            .size(48.dp)
            .pointerInput(haptics) {
                awaitEachGesture {
                    awaitFirstDown()
                    val hold = scope.launch {
                        progress.animateTo(1f, tween(HOLD_TO_OPEN_MS, easing = LinearEasing))
                        haptics.longPress()
                        currentOnOpen()
                    }
                    waitForUpOrCancellation()
                    hold.cancel()
                    scope.launch { progress.animateTo(0f) }
                }
            }
            .semantics {
                contentDescription = description
                onLongClick(label = holdLabel) {
                    currentOnOpen()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 3.dp,
            trackColor = Color.Transparent,
        )
        Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
