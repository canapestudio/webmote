package io.github.krank56.webmote.ui.simple

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.ui.common.Notice

/** The digits as a phone keypad, three to a row, with 0 alone in the middle of the last. */
private val keypad = listOf(
    listOf(RemoteButton.Num1, RemoteButton.Num2, RemoteButton.Num3),
    listOf(RemoteButton.Num4, RemoteButton.Num5, RemoteButton.Num6),
    listOf(RemoteButton.Num7, RemoteButton.Num8, RemoteButton.Num9),
    listOf(null, RemoteButton.Num0),
)

private val RemoteButton.digit: String get() = name.removePrefix("Num")

/**
 * Simple mode's 123: a full-screen pad of large digits, sent like the 123 sheet's, and Close. It covers
 * the Simple screen rather than sliding up as a sheet, so there's nothing to drag away by accident. Like
 * the Simple screen it never scrolls: under the title, the keypad's rows and Close share the height.
 */
@Composable
fun SimpleNumberPad(session: TvSession, pointerOk: Boolean, onClose: () -> Unit) {
    SimpleColumn(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            stringResource(R.string.simple_numbers_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (!pointerOk) Notice(stringResource(R.string.numbers_pointer_unavailable))
        keypad.forEach { row ->
            SimpleRow(3, Modifier.weight(1f)) {
                row.forEach { button ->
                    if (button == null) {
                        Spacer(Modifier)
                    } else {
                        SimpleButton(
                            label = button.digit,
                            onClick = { session.press(button) },
                            enabled = pointerOk,
                            labelStyle = MaterialTheme.typography.displaySmall,
                        )
                    }
                }
            }
        }
        SimpleButton(
            Icons.Rounded.Close,
            stringResource(R.string.simple_close),
            onClick = onClose,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
