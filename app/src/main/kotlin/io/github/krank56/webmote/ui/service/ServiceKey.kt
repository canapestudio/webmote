package io.github.krank56.webmote.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.ui.common.WholeWordsAutoSize
import io.github.krank56.webmote.ui.common.rememberPressAction

/** The smallest a key's label shrinks to, at large font sizes or in a narrow key. */
private val MinLabelSize = 10.dp

/** Relative to the font size, so a label that shrinks takes less height too. */
private val LabelLineHeight = 1.15.em

private val DotSize = 6.dp

/**
 * A key of the service remote, filling the cell it's given, with [content] (its label or icon) in the
 * middle. A key that isn't confirmed on webOS 26 has a hollow dot in its corner, and a colour key a stripe
 * of [swatch]. It gives the haptic tick, then calls [onPress]; TalkBack reads [description].
 */
@Composable
fun ServiceKey(
    button: RemoteButton,
    description: String,
    onPress: (RemoteButton) -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    swatch: Color? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val action = rememberPressAction({ onPress(button) }, interactionSource)
    Button(
        onClick = action,
        modifier = Modifier.semantics { contentDescription = description },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource,
    ) {
        Box(Modifier.fillMaxSize()) {
            // The description already names the key; don't read its label too.
            Box(
                Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp).clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
            if (!button.confirmedOnWebOs26) {
                UnconfirmedDot(Modifier.align(Alignment.TopEnd).padding(5.dp))
            }
            if (swatch != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 14.dp, end = 14.dp, bottom = 5.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (enabled) swatch else swatch.copy(alpha = 0.38f)),
                )
            }
        }
    }
}

/**
 * A key's label, with a warning mark before it on a service menu. At large font sizes, or when the key
 * is narrow, it shrinks to fit rather than spill, never breaking a word.
 */
@Composable
fun KeyLabel(text: String, style: TextStyle = MaterialTheme.typography.labelLarge, warning: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (warning) Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = style,
            lineHeight = LabelLineHeight,
            textAlign = TextAlign.Center,
            maxLines = text.split(' ').size,
            overflow = TextOverflow.Ellipsis,
            autoSize = WholeWordsAutoSize(minSize = MinLabelSize, maxSize = style.fontSize),
        )
    }
}

/** The hollow dot that marks a key not yet confirmed on webOS 26. */
@Composable
fun UnconfirmedDot(modifier: Modifier = Modifier, color: Color = LocalContentColor.current.copy(alpha = 0.7f)) {
    Box(modifier.size(DotSize).border(1.5.dp, color, CircleShape))
}

/** The one-line legend for [UnconfirmedDot]. */
@Composable
fun UnconfirmedLegend(modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        val style = MaterialTheme.typography.bodySmall
        UnconfirmedDot(color = MaterialTheme.colorScheme.outline)
        Text(
            stringResource(R.string.service_unconfirmed),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = LabelLineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = WholeWordsAutoSize(minSize = MinLabelSize, maxSize = style.fontSize),
        )
    }
}
