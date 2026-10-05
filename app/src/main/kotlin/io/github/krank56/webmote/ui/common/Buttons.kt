package io.github.krank56.webmote.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.core.RemoteButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 120L

/** The smallest a key's label shrinks to at large font sizes. */
private val MinLabelSize = 10.dp

/**
 * Wraps [onClick] with the haptic tick. With [repeatOnHold], holding the button down repeats the
 * action, like a physical remote's volume and arrow keys.
 */
@Composable
fun rememberPressAction(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    repeatOnHold: Boolean = false,
): () -> Unit {
    val haptics = LocalHaptics.current
    val currentOnClick by rememberUpdatedState(onClick)
    val pressed by interactionSource.collectIsPressedAsState()
    val holder = remember { RepeatState() }
    LaunchedEffect(pressed, repeatOnHold) {
        if (!pressed || !repeatOnHold) return@LaunchedEffect
        holder.repeated = false
        delay(REPEAT_DELAY_MS)
        holder.repeated = true
        while (isActive) {
            haptics.press()
            currentOnClick()
            delay(REPEAT_INTERVAL_MS)
        }
    }
    return remember(haptics) {
        {
            // The release after a repeating hold mustn't fire one press too many.
            if (!holder.repeated) {
                haptics.press()
                currentOnClick()
            }
            holder.repeated = false
        }
    }
}

private class RepeatState {
    var repeated = false
}

/** A round tonal icon button with the haptic tick, for the remote's keys. */
@Composable
fun RemoteIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp,
    shape: Shape = CircleShape,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val action = rememberPressAction(onClick, interactionSource, repeatOnHold)
    FilledTonalIconButton(
        onClick = action,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}

/** A tonal button with an icon over a short label, for the remote's labelled keys and shortcuts. */
@Composable
fun LabelledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = label,
    shape: Shape = MaterialTheme.shapes.large,
    width: Dp = 64.dp,
    height: Dp = 48.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val action = rememberPressAction(onClick, interactionSource)
    Labelled(label, enabled, width, modifier) {
        FilledTonalIconButton(
            onClick = action,
            modifier = Modifier.size(width = width, height = height),
            enabled = enabled,
            shape = shape,
            interactionSource = interactionSource,
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

/** A [LabelledIconButton] that's on or off, like Mute: it shows [checked], and gives the haptic tick. */
@Composable
fun LabelledIconToggleButton(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = label,
    shape: Shape = MaterialTheme.shapes.large,
    width: Dp = 64.dp,
    height: Dp = 48.dp,
) {
    val haptics = LocalHaptics.current
    Labelled(label, enabled, width, modifier) {
        FilledTonalIconToggleButton(
            checked = checked,
            onCheckedChange = {
                haptics.press()
                onCheckedChange(it)
            },
            modifier = Modifier.size(width = width, height = height),
            enabled = enabled,
            shape = shape,
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

/**
 * [button] over its [label], [width] wide. At large font sizes the label shrinks to fit rather than
 * spill into the next key, down to [MinLabelSize].
 */
@Composable
private fun Labelled(label: String, enabled: Boolean, width: Dp, modifier: Modifier, button: @Composable () -> Unit) {
    val style = MaterialTheme.typography.labelMedium
    // The floor is in dp, so at a small font size it can be above the theme's size: use the smaller.
    val minSize = with(LocalDensity.current) { MinLabelSize.toSp() }.let { if (it < style.fontSize) it else style.fontSize }
    Column(
        modifier = modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        button()
        // The button's description already names it; don't read the label twice.
        Box(Modifier.clearAndSetSemantics { }) {
            Text(
                label,
                style = style,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = style.fontSize),
            )
        }
    }
}

/** The colour of a colour key, as on the TV's remote; null for any other button. */
val RemoteButton.keyColour: Color?
    get() = when (this) {
        RemoteButton.Red -> Color(0xFFE53935)
        RemoteButton.Green -> Color(0xFF43A047)
        RemoteButton.Yellow -> Color(0xFFFDD835)
        RemoteButton.Blue -> Color(0xFF1E88E5)
        else -> null
    }

/** A round key with a text label, like the number pad's digits and the D-pad's OK. */
@Composable
fun RemoteTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 64.dp,
    contentDescription: String? = null,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    textStyle: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val action = rememberPressAction(onClick, interactionSource)
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    FilledTonalButton(
        onClick = action,
        modifier = modifier.size(size).then(described),
        enabled = enabled,
        shape = CircleShape,
        colors = colors,
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource,
    ) {
        Text(text, style = textStyle, maxLines = 1)
    }
}
