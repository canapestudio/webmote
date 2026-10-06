package io.github.krank56.webmote.ui.simple

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The space between Simple mode's buttons, so a shaky press lands on the one meant. */
val SimpleGap = 12.dp

private val ContentMaxWidth = 560.dp
private val ContentPadding = 16.dp

/**
 * A Simple mode page: a [SimpleContent] that fills the screen and never scrolls. Its [SimpleRow]s, each
 * given `Modifier.weight(1f)`, share the height that's left.
 */
@Composable
fun SimpleColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SimpleContent(modifier.fillMaxSize(), content = content)
}

/** [content] in a centred column at Simple mode's width, so everything on a Simple mode screen lines up. */
@Composable
fun SimpleContent(
    modifier: Modifier = Modifier,
    verticalPadding: Dp = ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = ContentPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(SimpleGap),
            content = content,
        )
    }
}

/**
 * One row of buttons, [columns] cells of equal width laid out left to right in every layout direction (so
 * a D-pad's left is the TV's left). Each child takes a cell, as tall as the row: the height the row is
 * given (a weighted row), or else its tallest child's.
 */
@Composable
fun SimpleRow(columns: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = SimpleGap.roundToPx()
        val cellWidth = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val height = if (constraints.hasFixedHeight) {
            constraints.maxHeight
        } else {
            (measurables.maxOfOrNull { it.maxIntrinsicHeight(cellWidth) } ?: 0)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        }
        val cells = measurables.map { it.measure(Constraints.fixed(cellWidth, height)) }
        layout(constraints.maxWidth, height) {
            cells.forEachIndexed { index, cell -> cell.place(index * (cellWidth + gap), 0) }
        }
    }
}
