package io.github.krank56.webmote.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.width
import io.github.krank56.webmote.R
import io.github.krank56.webmote.service.SystemSurfaces
import io.github.krank56.webmote.service.TvSnapshot

/** The 4×1 strip: power, volume down, mute, volume up, with the TV's name and state when there's room. */
class StripWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        SystemSurfaces.install(context)
        provideContent {
            GlanceTheme {
                Strip(rememberTv())
            }
        }
    }
}

class StripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StripWidget()
}

@Composable
private fun Strip(tv: TvSnapshot) {
    val size = LocalSize.current
    val showHeader = size.width >= HEADER_MIN_WIDTH
    val button = minOf(
        buttonSize(size.height - 16.dp, 1f),
        buttonSize(size.width - 16.dp, if (showHeader) 7f else 4.5f),
    )
    val buttons: List<@Composable () -> Unit> = listOf(
        { PowerButton(tv, button) },
        { TvButton(R.drawable.ic_widget_volume_down, R.string.system_action_volume_down, WidgetCommand.VolumeDown.action, button) },
        { MuteButton(tv, button) },
        { TvButton(R.drawable.ic_widget_volume_up, R.string.system_action_volume_up, WidgetCommand.VolumeUp.action, button) },
    )
    WidgetSurface {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (showHeader) {
                TvHeader(tv, GlanceModifier.defaultWeight())
                buttons.forEach { button ->
                    Spacer(GlanceModifier.width(6.dp))
                    button()
                }
            } else {
                // No room for the name: spread the buttons out evenly.
                buttons.forEach { button ->
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { button() }
                }
            }
        }
    }
}

private val HEADER_MIN_WIDTH = 280.dp
