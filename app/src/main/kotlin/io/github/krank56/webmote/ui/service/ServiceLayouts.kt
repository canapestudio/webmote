package io.github.krank56.webmote.ui.service

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** A key is never shorter than this, so it stays easy to hit; on a small phone that's the height they all get. */
val MinKeyHeight = 44.dp

/** Nor taller than this, on a tall phone, like the Remote tab's larger keys. */
private val MaxKeyHeight = 64.dp

/** The space between keys. Tight, so that every key fits a small phone at [MinKeyHeight]. */
val KeyGap = 4.dp

/** The space between sections: the service menus, the arrows and numbers, the tabs. */
private val SectionGap = 8.dp

/**
 * A section of the service remote: [rows] rows of keys, or 0 for something of its own height, like a
 * notice. Its [content] is a single element, such as one [KeyGrid].
 */
class Section(val rows: Int, val content: @Composable () -> Unit)

/**
 * The service remote's [sections], top to bottom. Every row of keys gets the same height: the height the
 * other sections leave, shared out, between [MinKeyHeight] and [MaxKeyHeight]. A section of rows is given
 * exactly the height of its rows and the [KeyGap]s between them, and fills it. Only when even the
 * shortest rows don't fit (split screen, or a notice on a small phone) does the column scroll.
 */
@Composable
fun KeyRowsColumn(sections: List<Section>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val screenHeight = constraints.maxHeight
        Layout(
            contents = sections.map { it.content },
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val sectionGap = SectionGap.roundToPx()
            val keyGap = KeyGap.roundToPx()
            val natural = sections.indices.associateWith { index ->
                if (sections[index].rows == 0) measurables[index].single().measure(Constraints(maxWidth = width)) else null
            }
            val rows = sections.sumOf { it.rows }
            val taken = natural.values.sumOf { it?.height ?: 0 } +
                sectionGap * (sections.size - 1) +
                sections.sumOf { if (it.rows > 0) keyGap * (it.rows - 1) else 0 }
            val rowHeight = if (rows == 0) 0 else {
                ((screenHeight - taken) / rows).coerceIn(MinKeyHeight.roundToPx(), MaxKeyHeight.roundToPx())
            }
            val placeables = sections.mapIndexed { index, section ->
                natural[index] ?: measurables[index].single().measure(
                    Constraints.fixed(width, section.rows * rowHeight + (section.rows - 1) * keyGap),
                )
            }
            val height = placeables.sumOf { it.height } + sectionGap * (placeables.size - 1).coerceAtLeast(0)
            layout(width, height) {
                var y = 0
                placeables.forEach {
                    it.placeRelative(0, y)
                    y += it.height + sectionGap
                }
            }
        }
    }
}

/**
 * Keys in a grid of [columns] by [rows], filling the size it's given: each takes a cell, left to right
 * then top to bottom, [KeyGap] apart. A `Spacer` leaves a cell empty. The cells keep their place in every
 * layout direction, like the keys of a remote.
 *
 * [trailing], such as a legend, goes in the last row after the keys: at its end, in the room the keys
 * leave there.
 */
@Composable
fun KeyGrid(
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Layout(listOf(content, trailing), modifier) { (keyMeasurables, trailingMeasurables), constraints ->
        val gap = KeyGap.roundToPx()
        val width = constraints.maxWidth
        val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val cellHeight = ((constraints.maxHeight - gap * (rows - 1)) / rows).coerceAtLeast(0)
        val cells = keyMeasurables.map { it.measure(Constraints.fixed(cellWidth, cellHeight)) }
        val lastRowTop = (rows - 1) * (cellHeight + gap)
        // The first free cell in the last row; the last row is the keys' own if they reach it.
        val trailingColumn = (cells.size - (rows - 1) * columns).coerceIn(0, columns)
        val trailingWidth = (width - trailingColumn * (cellWidth + gap)).coerceAtLeast(0)
        val trailingPlaceable = trailingMeasurables.firstOrNull()?.measure(Constraints(maxWidth = trailingWidth, maxHeight = cellHeight))
        layout(width, constraints.maxHeight) {
            // place, not placeRelative: a remote's keys don't swap sides in right-to-left layouts.
            cells.forEachIndexed { index, cell ->
                cell.place((index % columns) * (cellWidth + gap), (index / columns) * (cellHeight + gap))
            }
            trailingPlaceable?.place(width - trailingPlaceable.width, lastRowTop + (cellHeight - trailingPlaceable.height) / 2)
        }
    }
}
