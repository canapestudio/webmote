package io.github.krank56.webmote.ui.common

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** How finely a label's size is searched. */
private val LabelSizeStep = 0.5.dp

/**
 * Auto-size that keeps words whole: the largest font size up to [maxSize] at which the text fits its box
 * with every line ending between words, but no smaller than [minSize]. Compose's step-based auto-size only
 * checks the box, and Android breaks a word that's wider than the line.
 *
 * [minSize] is in dp rather than sp because keys don't grow with the font size: on a small phone, a label
 * at 200% has no more room than at 100%.
 */
data class WholeWordsAutoSize(private val minSize: Dp, private val maxSize: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        fun fits(size: Float) = performLayout(constraints, text, size.toSp()).fitsWholeWords()

        var fitting = minSize.toPx()
        var tooLarge = maxSize.toPx()
        if (tooLarge <= fitting || fits(tooLarge)) return maxSize
        // Halve the gap between a size that fits (or the minimum) and one that doesn't.
        val step = LabelSizeStep.toPx()
        while (tooLarge - fitting > step) {
            val middle = (fitting + tooLarge) / 2
            if (fits(middle)) fitting = middle else tooLarge = middle
        }
        return fitting.toSp()
    }
}

/** Whether the text fits with no line cut off, ellipsized or ending mid-word. */
private fun TextLayoutResult.fitsWholeWords(): Boolean {
    if (didOverflowWidth || didOverflowHeight) return false
    val text = layoutInput.text
    return (0 until lineCount).none { line ->
        val end = getLineEnd(line)
        isLineEllipsized(line) || (end < text.length && !text[end - 1].isWhitespace())
    }
}
