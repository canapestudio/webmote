package io.github.krank56.webmote.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.krank56.webmote.MainActivity
import io.github.krank56.webmote.R
import io.github.krank56.webmote.service.TvSnapshot
import io.github.krank56.webmote.service.label
import io.github.krank56.webmote.service.snapshot
import io.github.krank56.webmote.service.snapshots
import io.github.krank56.webmote.sessionHost

/** The active TV, following the session while the widget's Glance session is alive. */
@Composable
internal fun rememberTv(): TvSnapshot {
    val host = LocalContext.current.sessionHost
    val snapshots = remember(host) { host.snapshots() }
    return snapshots.collectAsState(host.snapshot()).value
}

private fun TvSnapshot.status(context: Context): String = context.getString(
    when {
        tvName == null -> R.string.system_no_tv
        connected && muted -> R.string.system_state_muted
        connected -> R.string.system_state_on
        else -> connection.label
    },
)

/** The widget's rounded, themed background. */
@Composable
internal fun WidgetSurface(content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The TV's name and state; tapping it opens the remote. */
@Composable
internal fun TvHeader(tv: TvSnapshot, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    Column(modifier = modifier.clickable(actionStartActivity<MainActivity>())) {
        Text(
            text = tv.tvName ?: context.getString(R.string.app_name),
            style = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Text(
            text = tv.status(context),
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
        )
    }
}

/** A round icon button; [highlighted] marks an on state (TV on, muted). */
@Composable
internal fun TvButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    action: Action,
    size: Dp,
    highlighted: Boolean = false,
) {
    val colors = GlanceTheme.colors
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(if (highlighted) colors.primary else colors.secondaryContainer)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = LocalContext.current.getString(description),
            modifier = GlanceModifier.size(size * 0.5f),
            colorFilter = ColorFilter.tint(if (highlighted) colors.onPrimary else colors.onSecondaryContainer),
        )
    }
}

/** The D-pad's OK button. */
@Composable
internal fun OkButton(size: Dp) {
    val colors = GlanceTheme.colors
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(colors.primaryContainer)
            .clickable(WidgetCommand.Ok.action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = LocalContext.current.getString(R.string.system_action_ok),
            style = TextStyle(color = colors.onPrimaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** Turns the TV off or on; opens the app instead when only it can help (no TV yet, pairing). */
@Composable
internal fun PowerButton(tv: TvSnapshot, size: Dp) = TvButton(
    icon = R.drawable.ic_widget_power,
    description = if (tv.connected) R.string.system_action_turn_off else R.string.system_action_turn_on,
    action = if (tv.needsApp) actionStartActivity<MainActivity>() else WidgetCommand.Power.action,
    size = size,
    highlighted = tv.connected,
)

@Composable
internal fun MuteButton(tv: TvSnapshot, size: Dp) = TvButton(
    icon = R.drawable.ic_widget_mute,
    description = if (tv.muted) R.string.system_action_unmute else R.string.system_action_mute,
    action = WidgetCommand.Mute.action,
    size = size,
    highlighted = tv.connected && tv.muted,
)

/** The largest button that fits [available] space split into [count] buttons, capped at a comfortable size. */
internal fun buttonSize(available: Dp, count: Float): Dp = (available / count).coerceIn(16.dp, 48.dp)
