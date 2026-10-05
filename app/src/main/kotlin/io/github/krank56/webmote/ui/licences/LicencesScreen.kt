package io.github.krank56.webmote.ui.licences

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.ui.common.BackScaffold
import io.github.krank56.webmote.ui.common.SectionHeader

/** Credits the projects Webmote's protocol client was built with reference to, and its libraries. */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    BackScaffold(title = stringResource(R.string.licences_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.licences_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Header(stringResource(R.string.licences_references)) }
            items(referenceProjects, key = { "ref:" + it.name }) { credit -> CreditCard(credit) }
            item { Header(stringResource(R.string.licences_libraries)) }
            items(libraries, key = { "lib:" + it.name }) { credit -> CreditCard(credit) }
            item { Header(stringResource(R.string.licences_full_texts)) }
            item { ExpandableText(title = Licence.Apache2.title, subtitle = null, body = APACHE_TEXT) }
            item { ExpandableText(title = Licence.Mit.title, subtitle = null, body = MIT_TEXT) }
        }
    }
}

@Composable
private fun Header(text: String) {
    SectionHeader(text, Modifier.padding(top = 12.dp))
}

@Composable
private fun CreditCard(credit: Credit) {
    val usage = credit.usage?.let { stringResource(it) }
    ExpandableText(
        title = credit.name,
        subtitle = listOfNotNull(usage, "${credit.licence.title} · ${credit.url}").joinToString("\n"),
        body = credit.noticeText(),
    )
}

/** A card that shows a licence notice when tapped. */
@Composable
private fun ExpandableText(title: String, subtitle: String?, body: String) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val stateText = stringResource(if (expanded) R.string.licences_expanded else R.string.licences_collapsed)
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .semantics { stateDescription = stateText },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                SelectionContainer {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}
