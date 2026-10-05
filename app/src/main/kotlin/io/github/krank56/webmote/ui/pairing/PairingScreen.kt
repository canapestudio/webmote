package io.github.krank56.webmote.ui.pairing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvCandidate
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.LocalHaptics
import io.github.krank56.webmote.ui.common.SectionHeader
import kotlinx.coroutines.CancellationException

/**
 * Finds TVs on the network (or takes an IP address) and pairs with one. It's the first screen when no
 * TV is saved ([onBack] null), and opens from Settings to add another TV, closing with [onPaired].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    host: SessionHost,
    state: TvState,
    tvs: List<SavedTv>,
    onBack: (() -> Unit)?,
    onPaired: () -> Unit,
) {
    val session = host.session
    val haptics = LocalHaptics.current

    // Discovery: each run collects the TVs that answer; Refresh starts another run.
    var searchRun by rememberSaveable { mutableIntStateOf(0) }
    var candidates by remember { mutableStateOf(emptyList<TvCandidate>()) }
    var searching by remember { mutableStateOf(true) }
    LaunchedEffect(searchRun) {
        searching = true
        candidates = emptyList()
        try {
            host.discover().collect { found ->
                if (candidates.none { it.host == found.host }) candidates = candidates + found
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No Wi-Fi, or the network blocks multicast: the manual IP entry is still there.
        }
        searching = false
    }

    // The current pairing attempt. The session's state is about it once it names the attempt's address.
    var attemptHost by rememberSaveable { mutableStateOf<String?>(null) }
    var attemptName by rememberSaveable { mutableStateOf<String?>(null) }

    val connect: (String, String?) -> Unit = { address, name ->
        haptics.press()
        attemptHost = address
        attemptName = name
        session.connect(address, name)
    }

    val attemptState = state.takeIf { attemptHost != null && it.host == attemptHost }
    LaunchedEffect(attemptState?.connection) {
        if (attemptState?.connection == ConnectionState.Connected) {
            attemptHost = null
            onPaired()
        }
    }

    val shownStatus: ConnectionState? = when {
        attemptHost != null -> attemptState?.connection ?: ConnectionState.Connecting
        // Pairing in progress without an attempt from this screen, e.g. after the activity was recreated.
        state.connection == ConnectionState.AwaitingPrompt || state.connection == ConnectionState.AwaitingPin -> state.connection
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (onBack == null) R.string.pairing_title_first else R.string.pairing_title_add)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchRun++ }, enabled = !searching) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.pairing_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.pairing_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                if (shownStatus != null) {
                    item {
                        val target = attemptName ?: attemptHost ?: tvs.firstOrNull { it.id == state.tvId }?.name.orEmpty()
                        PairingStatusCard(
                            connection = shownStatus,
                            target = target,
                            onSubmitPin = { pin -> session.submitPin(pin) },
                            onRetry = { attemptHost?.let { connect(it, attemptName) } },
                            onRepair = {
                                haptics.press()
                                session.repair()
                            },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                item {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp)) {
                        SectionHeader(stringResource(R.string.pairing_found_header))
                        if (searching) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    }
                }
                items(candidates, key = { it.host }) { candidate ->
                    val saved = tvs.firstOrNull { it.host == candidate.host }
                    CandidateRow(
                        candidate = candidate,
                        saved = saved != null,
                        onClick = { connect(candidate.host, candidate.name) },
                    )
                }
                if (!searching && candidates.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.pairing_none_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                item {
                    ManualEntry(onConnect = { address -> connect(address, null) })
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: TvCandidate, saved: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(candidate.name) },
            supportingContent = {
                Text(
                    if (saved) stringResource(R.string.pairing_candidate_saved, candidate.host) else candidate.host,
                )
            },
            leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.pairing_connect),
                onClick = onClick,
            ),
        )
    }
}

@Composable
private fun ManualEntry(onConnect: (String) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    val submit = {
        val value = address.trim()
        if (value.isNotEmpty()) onConnect(value)
    }
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.pairing_manual_header))
        Text(
            stringResource(R.string.pairing_manual_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.filterNot(Char::isWhitespace) },
                label = { Text(stringResource(R.string.pairing_manual_label)) },
                placeholder = { Text(stringResource(R.string.pairing_manual_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = submit, enabled = address.isNotBlank()) { Text(stringResource(R.string.pairing_connect)) }
        }
    }
}
