package io.github.krank56.webmote.ui.remote

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.ui.common.RemoteTextButton
import io.github.krank56.webmote.ui.common.rememberPressAction
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The D-pad's diameter where there's room: this share of the width, at most [MaxDiameter]. */
private const val WIDTH_SHARE = 0.78f
private val MaxDiameter = 280.dp

/** The space between the D-pad and the keys in its corners. */
private val CornerGap = 8.dp

/**
 * The round D-pad with OK in the middle, and a key in each corner of the square around it. Arrows
 * repeat while held, like on the TV's remote.
 *
 * The D-pad is 78 % of the width, at most 280 dp, and the square is just large enough for the corner
 * keys to clear it. On a short screen the D-pad shrinks until it fits, with the keys still clear of
 * it. Its minimum height is one key above the other. The corners are absolute, like the arrows:
 * [topLeft] is top left in every layout direction.
 */
@Composable
fun DPad(
    enabled: Boolean,
    onPress: (RemoteButton) -> Unit,
    topLeft: @Composable () -> Unit,
    topRight: @Composable () -> Unit,
    bottomLeft: @Composable () -> Unit,
    bottomRight: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf({ DPadCircle(enabled, onPress) }, topLeft, topRight, bottomLeft, bottomRight),
        modifier = modifier,
        measurePolicy = DPadLayout,
    )
}

/** Lays out [DPad]'s contents: the circle, then the top-left, top-right, bottom-left and bottom-right keys. */
private object DPadLayout : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val (circle, tl, tr, bl, br) = measurables
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val keys = listOf(tl, tr, bl, br).map { it.single().measure(loose) }
        val keyWidth = keys.maxOf { it.width }.toFloat()
        val keyHeight = keys.maxOf { it.height }.toFloat()
        val gap = CornerGap.toPx()

        val preferred = min(constraints.maxWidth * WIDTH_SHARE, MaxDiameter.toPx())
        val side = squareSide(preferred, keyWidth, keyHeight, gap)
        val width = min(constraints.maxWidth.toFloat(), side)
        val height = if (constraints.hasBoundedHeight) min(constraints.maxHeight.toFloat(), side) else side
        // A key's inner corner, or its inner edge once the keys reach past the middle, stays gap clear of the circle.
        val clearance = hypot(max(width / 2 - keyWidth, 0f), max(height / 2 - keyHeight, 0f)) - gap
        val diameter = minOf(preferred, height, 2 * clearance).coerceAtLeast(0f).roundToInt()
        val pad = circle.single().measure(Constraints.fixed(diameter, diameter))

        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.constrainHeight(height.roundToInt())
        return layout(layoutWidth, layoutHeight) {
            val left = (layoutWidth - width) / 2
            val top = (layoutHeight - height) / 2
            val right = left + width
            val bottom = top + height
            pad.place((layoutWidth - diameter) / 2, (layoutHeight - diameter) / 2)
            // place, not placeRelative: the corners don't swap sides in right-to-left layouts.
            keys[0].place(left.roundToInt(), top.roundToInt())
            keys[1].place((right - keys[1].width).roundToInt(), top.roundToInt())
            keys[2].place(left.roundToInt(), (bottom - keys[2].height).roundToInt())
            keys[3].place((right - keys[3].width).roundToInt(), (bottom - keys[3].height).roundToInt())
        }
    }

    // The least height that keeps the keys apart: one above the other on each side.
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int {
        val (_, tl, tr, bl, br) = measurables
        fun height(key: List<IntrinsicMeasurable>) = key.single().minIntrinsicHeight(width)
        return max(height(tl) + height(bl), height(tr) + height(br))
    }
}

/**
 * The side of the smallest square holding a circle of [diameter] with a [keyWidth] × [keyHeight] key
 * in each corner, its inner corner [gap] clear of the circle.
 */
private fun squareSide(diameter: Float, keyWidth: Float, keyHeight: Float, gap: Float): Float {
    val reach = diameter / 2 + gap
    // Half the side, h, solves (h − keyWidth)² + (h − keyHeight)² = reach².
    val skew = keyWidth - keyHeight
    val half = (keyWidth + keyHeight + sqrt(max(2 * reach * reach - skew * skew, 0f))) / 2
    return max(2 * half, diameter)
}

/** The round pad itself, filling the size it's given. */
@Composable
private fun DPadCircle(enabled: Boolean, onPress: (RemoteButton) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val diameter = min(maxWidth, maxHeight)
        val arrowSize = diameter * 0.3f
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(diameter),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Absolute alignment: the D-pad's left is the TV's left in every layout direction.
                DPadArrow(
                    RemoteButton.Up, 0f, stringResource(R.string.remote_up), enabled, onPress, arrowSize,
                    Modifier.align(Alignment.TopCenter),
                )
                DPadArrow(
                    RemoteButton.Down, 180f, stringResource(R.string.remote_down), enabled, onPress, arrowSize,
                    Modifier.align(Alignment.BottomCenter),
                )
                DPadArrow(
                    RemoteButton.Left, -90f, stringResource(R.string.remote_left), enabled, onPress, arrowSize,
                    Modifier.align(AbsoluteAlignment.CenterLeft),
                )
                DPadArrow(
                    RemoteButton.Right, 90f, stringResource(R.string.remote_right), enabled, onPress, arrowSize,
                    Modifier.align(AbsoluteAlignment.CenterRight),
                )
                RemoteTextButton(
                    text = stringResource(R.string.remote_ok),
                    onClick = { onPress(RemoteButton.Ok) },
                    enabled = enabled,
                    size = diameter * 0.38f,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun DPadArrow(
    button: RemoteButton,
    rotation: Float,
    description: String,
    enabled: Boolean,
    onPress: (RemoteButton) -> Unit,
    size: Dp,
    modifier: Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val action = rememberPressAction({ onPress(button) }, interactionSource, repeatOnHold = true)
    IconButton(
        onClick = action,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.padding(4.dp).size(size),
    ) {
        Icon(
            Icons.Rounded.KeyboardArrowUp,
            contentDescription = description,
            modifier = Modifier.size(44.dp).rotate(rotation),
        )
    }
}
