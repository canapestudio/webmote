package io.github.krank56.webmote.ui.simple

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.offset
import io.github.krank56.webmote.ui.common.WholeWordsAutoSize
import io.github.krank56.webmote.ui.common.rememberPressAction
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/** The largest a Simple mode button's icon gets; a short button shrinks it. */
val SimpleIconSize = 36.dp

/** The most of a short button's height its icon takes, leaving the rest to the label. */
private const val ICON_SHARE = 0.4f
private val IconGap = 4.dp

/**
 * The smallest a label shrinks to. It's in dp rather than sp because the buttons don't grow with the font
 * size: on a small phone, a label at 200% has no more room than at 100%.
 */
private val MinLabelSize = 12.dp

/** Relative to the font size, so a label that shrinks takes less height too. */
private val LabelLineHeight = 1.2.em

/** How long a tapped button stays inverted, so even a quick tap visibly flashes. */
private const val FLASH_MS = 150L

/** A [SimpleButton] with a vector icon. */
@Composable
fun SimpleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    contentDescription: String = label,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    labelStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    SimpleButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        repeatOnHold = repeatOnHold,
        contentDescription = contentDescription,
        containerColor = containerColor,
        contentColor = contentColor,
        labelStyle = labelStyle,
    ) {
        Icon(icon, contentDescription = null)
    }
}

/**
 * Simple mode's button: [icon] over a word (or just the word, for a digit), in a high-contrast pair of
 * theme colours that swap while it's pressed and just after a tap. It takes the height it's given; when
 * that's short, the icon shrinks and the label shrinks from [labelStyle] to fit, down to a readable
 * minimum and never breaking a word. It gives the haptic tick and TalkBack reads [contentDescription], the
 * label unless it needs saying differently.
 *
 * [icon] fills the square it's measured in, at most [SimpleIconSize].
 */
@Composable
fun SimpleButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    contentDescription: String = label,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    labelStyle: TextStyle = MaterialTheme.typography.titleLarge,
    icon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var taps by remember { mutableIntStateOf(0) }
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        flashing = true
        delay(FLASH_MS)
        flashing = false
    }
    val action = rememberPressAction(
        onClick = {
            taps++
            onClick()
        },
        interactionSource = interactionSource,
        repeatOnHold = repeatOnHold,
    )
    val inverted = pressed || flashing
    Button(
        onClick = action,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (inverted) contentColor else containerColor,
            contentColor = if (inverted) containerColor else contentColor,
        ),
        contentPadding = PaddingValues(6.dp),
        interactionSource = interactionSource,
    ) {
        IconOverLabel(icon) {
            // The button's description already names it; don't read the label twice.
            Box(Modifier.clearAndSetSemantics { }) {
                Text(
                    label,
                    style = labelStyle,
                    lineHeight = LabelLineHeight,
                    textAlign = TextAlign.Center,
                    // Lines break between words only. A word too long even at the smallest size ends in "…".
                    maxLines = label.split(' ').size,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = WholeWordsAutoSize(minSize = MinLabelSize, maxSize = labelStyle.fontSize),
                )
            }
        }
    }
}

/**
 * [icon] (if any) over [label], centred. The icon is a square of [SimpleIconSize], or [ICON_SHARE] of the
 * height when that's smaller; the label gets the height that's left.
 */
@Composable
private fun IconOverLabel(icon: (@Composable () -> Unit)?, label: @Composable () -> Unit) {
    Layout(contents = listOf(icon ?: {}, label)) { (iconMeasurables, labelMeasurables), constraints ->
        val iconSize = when {
            iconMeasurables.isEmpty() -> 0
            // Unbounded when asked for its natural height, as in the TV picker.
            !constraints.hasBoundedHeight -> SimpleIconSize.roundToPx()
            else -> min(SimpleIconSize.roundToPx(), (constraints.maxHeight * ICON_SHARE).toInt())
        }
        val gap = if (iconMeasurables.isEmpty()) 0 else IconGap.roundToPx()
        val iconPlaceable = iconMeasurables.firstOrNull()?.measure(Constraints.fixed(iconSize, iconSize))
        val labelPlaceable = labelMeasurables.first().measure(constraints.offset(vertical = -(iconSize + gap)))
        val width = max(iconSize, labelPlaceable.width)
        layout(width, iconSize + gap + labelPlaceable.height) {
            iconPlaceable?.place((width - iconSize) / 2, 0)
            labelPlaceable.place((width - labelPlaceable.width) / 2, iconSize + gap)
        }
    }
}
