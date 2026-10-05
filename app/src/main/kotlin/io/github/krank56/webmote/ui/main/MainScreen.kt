package io.github.krank56.webmote.ui.main

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.TouchApp
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
import io.github.krank56.webmote.ui.Route
import io.github.krank56.webmote.ui.apps.AppsTab
import io.github.krank56.webmote.ui.common.PowerAction
import io.github.krank56.webmote.ui.common.TvScaffold
import io.github.krank56.webmote.ui.remote.RemoteTab
import io.github.krank56.webmote.ui.touchpad.TouchpadTab

/** The main screen's bottom-navigation tabs. */
enum class MainTab(@StringRes val label: Int, val icon: ImageVector) {
    Remote(R.string.tab_remote, Icons.Rounded.SettingsRemote),
    Touchpad(R.string.tab_touchpad, Icons.Rounded.TouchApp),
    Apps(R.string.tab_apps, Icons.Rounded.Apps),
}

/**
 * The connected TV's remote: Remote, Touchpad and Apps tabs under the shared top bar. The selected
 * [tab] is kept by the caller so it survives a visit to Settings.
 */
@Composable
fun MainScreen(
    host: SessionHost,
    tvs: List<SavedTv>,
    state: TvState,
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val session = host.session
    BackHandler(enabled = tab != MainTab.Remote) { onTabChange(MainTab.Remote) }

    TvScaffold(
        host = host,
        tvs = tvs,
        state = state,
        onOpen = onOpen,
        power = PowerAction(turnsOn = false, onClick = session::powerOff),
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
            MainTab.Touchpad -> TouchpadTab(session, host.settings, state)
            MainTab.Apps -> AppsTab(session, state)
        }
    }
}
