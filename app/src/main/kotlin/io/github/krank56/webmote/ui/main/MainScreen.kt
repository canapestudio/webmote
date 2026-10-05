package io.github.krank56.webmote.ui.main

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvState
import io.github.krank56.webmote.ui.common.TvScaffold
import io.github.krank56.webmote.ui.remote.RemoteTab

/** The main screen's bottom-navigation tabs. */
enum class MainTab(@StringRes val label: Int, val icon: ImageVector) {
    Remote(R.string.tab_remote, Icons.Rounded.SettingsRemote),
}

/**
 * The connected TV's remote: the Remote tab under the shared top bar. The selected [tab] is kept by
 * the caller.
 */
@Composable
fun MainScreen(
    host: SessionHost,
    tvs: List<SavedTv>,
    state: TvState,
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
) {
    val session = host.session
    BackHandler(enabled = tab != MainTab.Remote) { onTabChange(MainTab.Remote) }

    TvScaffold(
        host = host,
        tvs = tvs,
        state = state,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { onTabChange(item) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) {
        when (tab) {
            MainTab.Remote -> RemoteTab(session, state)
        }
    }
}
