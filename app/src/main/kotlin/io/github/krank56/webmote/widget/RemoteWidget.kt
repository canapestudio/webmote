package io.github.krank56.webmote.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import io.github.krank56.webmote.R
import io.github.krank56.webmote.service.SystemSurfaces
import io.github.krank56.webmote.service.TvSnapshot

/** The 4×2 mini remote: the TV's name and power, Back, Home, mute, volume ± and a D-pad with OK. */
class RemoteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        SystemSurfaces.install(context)
        provideContent {
            GlanceTheme {
                MiniRemote(rememberTv())
            }
        }
    }
}

class RemoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RemoteWidget()
}

@Composable
private fun MiniRemote(tv: TvSnapshot) {
    val size = LocalSize.current
    // Three rows of buttons on each side; the D-pad and the left column are each three buttons wide.
    val button = minOf(
        buttonSize(size.height - 16.dp - GAP * 2, 3f),
        buttonSize(size.width - 16.dp - GAP * 5, 6f),
    )
    WidgetSurface {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TvHeader(tv, GlanceModifier.defaultWeight())
                    PowerButton(tv, button)
                }
                Spacer(GlanceModifier.defaultWeight())
                Row {
                    TvButton(R.drawable.ic_widget_back, R.string.system_action_back, WidgetCommand.Back.action, button)
                    Spacer(GlanceModifier.width(GAP))
                    TvButton(R.drawable.ic_widget_home, R.string.system_action_home, WidgetCommand.Home.action, button)
                    Spacer(GlanceModifier.width(GAP))
                    MuteButton(tv, button)
                }
                Spacer(GlanceModifier.height(GAP))
                Row {
                    TvButton(R.drawable.ic_widget_volume_down, R.string.system_action_volume_down, WidgetCommand.VolumeDown.action, button)
                    Spacer(GlanceModifier.width(GAP))
                    TvButton(R.drawable.ic_widget_volume_up, R.string.system_action_volume_up, WidgetCommand.VolumeUp.action, button)
                }
            }
            Spacer(GlanceModifier.width(GAP * 2))
            DPad(button)
        }
    }
}

@Composable
private fun DPad(button: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TvButton(R.drawable.ic_widget_up, R.string.system_action_up, WidgetCommand.Up.action, button)
        Spacer(GlanceModifier.height(GAP))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(R.drawable.ic_widget_left, R.string.system_action_left, WidgetCommand.Left.action, button)
            Spacer(GlanceModifier.width(GAP))
            OkButton(button)
            Spacer(GlanceModifier.width(GAP))
            TvButton(R.drawable.ic_widget_right, R.string.system_action_right, WidgetCommand.Right.action, button)
        }
        Spacer(GlanceModifier.height(GAP))
        TvButton(R.drawable.ic_widget_down, R.string.system_action_down, WidgetCommand.Down.action, button)
    }
}

private val GAP = 4.dp
