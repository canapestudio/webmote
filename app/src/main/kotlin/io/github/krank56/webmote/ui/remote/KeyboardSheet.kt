package io.github.krank56.webmote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.ui.common.LocalHaptics
import kotlinx.coroutines.delay

/** Lets the sheet finish attaching its window before the field asks for the soft keyboard. */
private const val FOCUS_DELAY_MS = 200L

/**
 * The live keyboard: opens the phone's soft keyboard and types on the TV as the user types.
 * Backspace deletes on the TV (even past what was typed here), and Enter submits.
 */
@Composable
fun KeyboardSheet(session: TvSession, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    val mirror = remember(session) { TextMirror(insert = session::insertText, delete = session::deleteCharacters) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(FOCUS_DELAY_MS)
        focus.requestFocus()
        keyboard?.show()
    }

    val enter = {
        haptics.press()
        session.sendEnter()
        // The TV keeps its text; start afresh here so the next letters append to it.
        mirror.reset()
        field = TextFieldValue("")
    }
    val backspace = {
        haptics.press()
        val text = field.text
        if (text.isEmpty()) {
            session.deleteCharacters(1)
        } else {
            val shorter = text.substring(0, text.offsetByCodePoints(text.length, -1))
            field = TextFieldValue(shorter, TextRange(shorter.length))
            mirror.onTextChanged(shorter)
        }
    }

    RemoteSheet(title = stringResource(R.string.keyboard_title), onDismiss = onDismiss) {
        Text(
            stringResource(R.string.keyboard_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = field,
            onValueChange = { value ->
                field = value
                mirror.onTextChanged(value.text)
            },
            label = { Text(stringResource(R.string.keyboard_field_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { enter() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    when {
                        // Backspace on an empty field: the IME has nothing to delete here, but the TV may.
                        event.key == Key.Backspace && field.text.isEmpty() -> {
                            if (event.type == KeyEventType.KeyDown) backspace()
                            true
                        }
                        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                            if (event.type == KeyEventType.KeyDown) enter()
                            true
                        }
                        else -> false
                    }
                },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.End)) {
            OutlinedButton(onClick = backspace) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.keyboard_delete))
            }
            Button(onClick = enter) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.keyboard_enter))
            }
        }
    }
}
