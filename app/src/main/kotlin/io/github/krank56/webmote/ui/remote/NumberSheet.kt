package io.github.krank56.webmote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.ui.common.Notice
import io.github.krank56.webmote.ui.common.RemoteIconButton
import io.github.krank56.webmote.ui.common.RemoteTextButton
import io.github.krank56.webmote.ui.common.keyColour
import io.github.krank56.webmote.ui.common.rememberPressAction

private val digitRows = listOf(
    listOf(RemoteButton.Num1, RemoteButton.Num2, RemoteButton.Num3),
    listOf(RemoteButton.Num4, RemoteButton.Num5, RemoteButton.Num6),
    listOf(RemoteButton.Num7, RemoteButton.Num8, RemoteButton.Num9),
)

private val RemoteButton.digit: String get() = name.removePrefix("Num")

/** The 123 sheet: number pad, channel ± and the red/green/yellow/blue keys. */
@Composable
fun NumberSheet(session: TvSession, pointerOk: Boolean, onDismiss: () -> Unit) {
    RemoteSheet(title = stringResource(R.string.numbers_title), onDismiss = onDismiss) {
        if (!pointerOk) Notice(stringResource(R.string.numbers_pointer_unavailable))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                digitRows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { button ->
                            RemoteTextButton(button.digit, onClick = { session.press(button) }, enabled = pointerOk)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Spacer(Modifier.size(64.dp))
                    RemoteTextButton(RemoteButton.Num0.digit, onClick = { session.press(RemoteButton.Num0) }, enabled = pointerOk)
                    Spacer(Modifier.size(64.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteIconButton(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.numbers_channel_up),
                    onClick = session::channelUp,
                    repeatOnHold = true,
                    size = 64.dp,
                    iconSize = 32.dp,
                )
                Text(
                    stringResource(R.string.numbers_channel_short),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                RemoteIconButton(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.numbers_channel_down),
                    onClick = session::channelDown,
                    repeatOnHold = true,
                    size = 64.dp,
                    iconSize = 32.dp,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColourKey(RemoteButton.Red, stringResource(R.string.numbers_red), pointerOk, session)
            ColourKey(RemoteButton.Green, stringResource(R.string.numbers_green), pointerOk, session)
            ColourKey(RemoteButton.Yellow, stringResource(R.string.numbers_yellow), pointerOk, session)
            ColourKey(RemoteButton.Blue, stringResource(R.string.numbers_blue), pointerOk, session)
        }
    }
}

@Composable
private fun RowScope.ColourKey(
    button: RemoteButton,
    description: String,
    enabled: Boolean,
    session: TvSession,
) {
    val colour = button.keyColour ?: return
    val interactionSource = remember { MutableInteractionSource() }
    val action = rememberPressAction({ session.press(button) }, interactionSource)
    Button(
        onClick = action,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = colour,
            disabledContainerColor = colour.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .semantics { contentDescription = description },
    ) {
        Spacer(Modifier.width(0.dp))
    }
}
