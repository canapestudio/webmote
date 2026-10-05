package io.github.krank56.webmote.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long a released slider keeps showing its value while waiting for the TV to report it. */
private const val SETTLE_TIMEOUT_MS = 2_500L

/**
 * A 0–100 slider for a value the TV reports, like the volume.
 *
 * While dragging it shows the finger's value and reports each whole step to [onDrag]; on release it
 * reports the final value to [onRelease]. It then keeps showing that value until the TV reports it
 * (or a short timeout), so the thumb doesn't jump back while the TV catches up.
 */
@Composable
fun TvSlider(
    value: Int?,
    onDrag: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    showLabel: Boolean = false,
    range: IntRange = 0..100,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var released by remember { mutableStateOf<Int?>(null) }
    var lastSent by remember { mutableIntStateOf(Int.MIN_VALUE) }

    LaunchedEffect(released, value) {
        val target = released ?: return@LaunchedEffect
        if (value != target) delay(SETTLE_TIMEOUT_MS)
        released = null
    }

    val usable = enabled && (value != null || dragValue != null)
    val shown = dragValue ?: (released ?: value ?: range.first).toFloat()
    val shownText = if (value == null && dragValue == null && released == null) "–" else shown.roundToInt().toString()

    Column(modifier.alpha(if (usable) 1f else 0.6f)) {
        if (showLabel) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(shownText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(24.dp))
            }
            Slider(
                value = shown.coerceIn(range.first.toFloat(), range.last.toFloat()),
                onValueChange = { v ->
                    dragValue = v
                    val step = v.roundToInt()
                    if (step != lastSent) {
                        lastSent = step
                        onDrag(step)
                    }
                },
                onValueChangeFinished = {
                    val final = dragValue?.roundToInt()
                    dragValue = null
                    lastSent = Int.MIN_VALUE
                    if (final != null) {
                        released = final
                        onRelease(final)
                    }
                },
                enabled = usable,
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
            )
            if (!showLabel) {
                // One line: at large font sizes "100" widens the column rather than wrapping.
                Text(
                    shownText,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 32.dp).clearAndSetSemantics { },
                )
            }
        }
    }
}
