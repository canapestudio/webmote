package io.github.krank56.webmote.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.krank56.webmote.MainActivity
import io.github.krank56.webmote.R

/** The keep-alive service's ongoing notification: the TV's name and state, with −, mute, + and power. */
internal object KeepAliveNotification {
    const val ID = 1
    private const val CHANNEL = "keep_alive"

    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notif_channel_name))
            .setDescription(context.getString(R.string.notif_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun build(context: Context, tv: TvSnapshot): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif_remote)
            .setContentTitle(tv.tvName ?: context.getString(R.string.app_name))
            .setContentText(context.getString(tv.connection.label))
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            // While the TV is still connecting (or turns out to be off), let Android 12+ hold the
            // notification back for a few seconds, so a service that stops right away never shows it.
            .setForegroundServiceBehavior(
                if (tv.connected) NotificationCompat.FOREGROUND_SERVICE_DEFAULT else NotificationCompat.FOREGROUND_SERVICE_DEFERRED,
            )
            .addAction(
                R.drawable.ic_widget_volume_down,
                context.getString(R.string.notif_action_volume_down),
                NotificationActionReceiver.intent(context, NotificationActionReceiver.VOLUME_DOWN),
            )
            .addAction(
                R.drawable.ic_widget_mute,
                context.getString(if (tv.muted) R.string.notif_action_unmute else R.string.notif_action_mute),
                NotificationActionReceiver.intent(context, NotificationActionReceiver.TOGGLE_MUTE),
            )
            .addAction(
                R.drawable.ic_widget_volume_up,
                context.getString(R.string.notif_action_volume_up),
                NotificationActionReceiver.intent(context, NotificationActionReceiver.VOLUME_UP),
            )
        if (tv.connected) {
            builder.addAction(
                R.drawable.ic_widget_power,
                context.getString(R.string.notif_action_power_off),
                NotificationActionReceiver.intent(context, NotificationActionReceiver.POWER_OFF),
            )
        }
        return builder.build()
    }

    /** Replaces the service's notification. Does nothing without the notification permission: it isn't shown then. */
    fun update(context: Context, tv: TvSnapshot) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(ID, build(context, tv))
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
