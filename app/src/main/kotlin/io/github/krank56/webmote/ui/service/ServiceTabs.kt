package io.github.krank56.webmote.ui.service

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.krank56.webmote.R
import io.github.krank56.webmote.core.RemoteButton
import io.github.krank56.webmote.ui.common.WholeWordsAutoSize
import io.github.krank56.webmote.ui.common.keyColour

/** How many keys a tab has to a row. */
private const val TAB_COLUMNS = 4

/** Material's tab height: the tabs don't grow with the keys on a tall phone. */
private val TabHeight = 48.dp

/** A key in one of the tabs: its button, its short label, and its full name for TalkBack. */
class TabKey(val button: RemoteButton, @StringRes val label: Int, @StringRes val description: Int = label)

/** The service remote's tabs: every key that isn't always visible, in four groups. */
enum class KeyGroup(@StringRes val label: Int, @StringRes val description: Int, val keys: List<TabKey>) {
    TvAndGuide(
        R.string.service_tab_tv,
        R.string.service_tab_tv_description,
        listOf(
            TabKey(RemoteButton.Guide, R.string.service_key_guide, R.string.service_desc_guide),
            TabKey(RemoteButton.Program, R.string.service_key_programme),
            TabKey(RemoteButton.ChannelList, R.string.service_key_list, R.string.service_desc_list),
            TabKey(RemoteButton.LiveTv, R.string.service_key_live_tv),
            TabKey(RemoteButton.Tv, R.string.service_key_tv),
            TabKey(RemoteButton.ChannelUp, R.string.service_key_channel_up, R.string.numbers_channel_up),
            TabKey(RemoteButton.ChannelDown, R.string.service_key_channel_down, R.string.numbers_channel_down),
            TabKey(RemoteButton.Flashback, R.string.service_key_flashback, R.string.service_desc_flashback),
            TabKey(RemoteButton.Favourites, R.string.service_key_favourites, R.string.service_desc_favourites),
            TabKey(RemoteButton.Teletext, R.string.service_key_teletext),
            TabKey(RemoteButton.TextOption, R.string.service_key_text_option, R.string.service_desc_text_option),
            TabKey(RemoteButton.Record, R.string.service_key_record),
            TabKey(RemoteButton.Recordings, R.string.service_key_recordings, R.string.service_desc_recordings),
            TabKey(RemoteButton.Red, R.string.numbers_red),
            TabKey(RemoteButton.Green, R.string.numbers_green),
            TabKey(RemoteButton.Yellow, R.string.numbers_yellow),
            TabKey(RemoteButton.Blue, R.string.numbers_blue),
        ),
    ),
    SoundAndPicture(
        R.string.service_tab_sound,
        R.string.service_tab_sound_description,
        listOf(
            TabKey(RemoteButton.Subtitles, R.string.service_key_subtitles),
            TabKey(RemoteButton.AudioDescription, R.string.service_key_audio_description, R.string.service_desc_audio_description),
            TabKey(RemoteButton.MultiAudio, R.string.service_key_multi_audio),
            TabKey(RemoteButton.VolumeUp, R.string.service_key_volume_up, R.string.remote_volume_up),
            TabKey(RemoteButton.VolumeDown, R.string.service_key_volume_down, R.string.remote_volume_down),
            TabKey(RemoteButton.Mute, R.string.remote_mute),
            TabKey(RemoteButton.AspectRatio, R.string.service_key_aspect_ratio),
            TabKey(RemoteButton.PictureMode, R.string.service_key_picture_mode),
            TabKey(RemoteButton.EnergySaving, R.string.picture_energy_saving),
            TabKey(RemoteButton.LiveZoom, R.string.service_key_live_zoom),
            TabKey(RemoteButton.FocusZoom, R.string.service_key_focus_zoom),
            TabKey(RemoteButton.ThreeD, R.string.service_key_3d, R.string.service_desc_3d),
        ),
    ),
    MenusAndApps(
        R.string.service_tab_menus,
        R.string.service_tab_menus_description,
        listOf(
            TabKey(RemoteButton.Home, R.string.remote_home),
            TabKey(RemoteButton.Menu, R.string.service_key_menu),
            TabKey(RemoteButton.Settings, R.string.service_key_quick_menu),
            TabKey(RemoteButton.MyApps, R.string.service_key_my_apps),
            TabKey(RemoteButton.Recent, R.string.service_key_recent, R.string.service_desc_recent),
            TabKey(RemoteButton.InputHub, R.string.service_key_input_hub),
            TabKey(RemoteButton.Search, R.string.service_key_search),
            TabKey(RemoteButton.ScreenRemote, R.string.service_key_screen_remote),
            TabKey(RemoteButton.Info, R.string.service_key_info),
            TabKey(RemoteButton.EManual, R.string.service_key_emanual, R.string.service_desc_emanual),
            TabKey(RemoteButton.SleepTimer, R.string.service_key_sleep_timer),
            TabKey(RemoteButton.AlwaysReady, R.string.service_key_always_ready),
            TabKey(RemoteButton.Simplink, R.string.service_key_simplink, R.string.service_desc_simplink),
        ),
    ),
    PlaybackAndStreaming(
        R.string.service_tab_playback,
        R.string.service_tab_playback_description,
        listOf(
            TabKey(RemoteButton.Play, R.string.service_key_play),
            TabKey(RemoteButton.Pause, R.string.service_key_pause),
            TabKey(RemoteButton.Stop, R.string.remote_stop),
            TabKey(RemoteButton.Rewind, R.string.remote_rewind),
            TabKey(RemoteButton.FastForward, R.string.remote_fast_forward),
            TabKey(RemoteButton.Previous, R.string.service_key_previous),
            TabKey(RemoteButton.Next, R.string.service_key_next),
            TabKey(RemoteButton.Netflix, R.string.service_key_netflix),
            TabKey(RemoteButton.Amazon, R.string.service_key_amazon),
            TabKey(RemoteButton.Alexa, R.string.service_key_alexa),
            TabKey(RemoteButton.Yandex, R.string.service_key_yandex),
            TabKey(RemoteButton.Ivi, R.string.service_key_ivi),
            TabKey(RemoteButton.Soccer, R.string.service_key_soccer),
            TabKey(RemoteButton.Twin, R.string.service_key_twin),
            TabKey(RemoteButton.Usp, R.string.service_key_usp),
            TabKey(RemoteButton.Bendable, R.string.service_key_bendable),
        ),
    ),
}

/**
 * The rows every tab's keys take: as many as the largest group needs, plus a cell left for the legend, so
 * that switching tabs moves nothing.
 */
val TabRows = (KeyGroup.entries.maxOf { it.keys.size } + 1).let { cells -> (cells + TAB_COLUMNS - 1) / TAB_COLUMNS }

/** The four tabs, the [selected] one underlined. Their labels shrink to fit at large font sizes. */
@Composable
fun GroupTabs(selected: KeyGroup, onSelect: (KeyGroup) -> Unit) {
    val colours = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(TabHeight)) {
        HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = colours.outlineVariant)
        Row(Modifier.fillMaxSize().selectableGroup()) {
            KeyGroup.entries.forEach { group ->
                val isSelected = group == selected
                val description = stringResource(group.description)
                val label = stringResource(group.label)
                val style = MaterialTheme.typography.titleSmall
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(group) })
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    // The description names the tab in full; don't read the short label too.
                    Box(Modifier.padding(horizontal = 2.dp).clearAndSetSemantics { }) {
                        Text(
                            label,
                            style = style,
                            color = if (isSelected) colours.primary else colours.onSurfaceVariant,
                            lineHeight = 1.15.em,
                            textAlign = TextAlign.Center,
                            maxLines = label.split(' ').size,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = WholeWordsAutoSize(minSize = 10.dp, maxSize = style.fontSize),
                        )
                    }
                    if (isSelected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(colours.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * [group]'s keys, [TAB_COLUMNS] to a row in [TabRows] rows, filling the size they're given; the colour keys
 * show their colour. The legend for the unconfirmed dot sits after the keys, at the end of the last row.
 */
@Composable
fun GroupKeys(group: KeyGroup, enabled: Boolean, onPress: (RemoteButton) -> Unit) {
    val colours = MaterialTheme.colorScheme
    KeyGrid(columns = TAB_COLUMNS, rows = TabRows, trailing = { UnconfirmedLegend() }) {
        group.keys.forEach { key ->
            val label = stringResource(key.label)
            ServiceKey(
                button = key.button,
                description = stringResource(key.description),
                onPress = onPress,
                enabled = enabled,
                containerColor = colours.surfaceContainerHigh,
                contentColor = colours.onSurface,
                swatch = key.button.keyColour,
            ) {
                KeyLabel(label)
            }
        }
    }
}
