package io.github.krank56.webmote.ui.touchpad

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Points the TV's pointer with the phone: between [start] and [stop], feeds the gyroscope and the
 * gravity sensor to an [AimTracker] at the game rate. Without a gravity sensor, a low-pass-filtered
 * accelerometer stands in for it. The sensors are registered only while pointing.
 */
internal class PhonePointer(context: Context, private val tracker: AimTracker) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val gyroscope = sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val filtered = FloatArray(3)
    private var filterPrimed = false

    /** Whether the phone is pointing, for the touchpad to show. */
    var pointing by mutableStateOf(false)
        private set

    fun start() {
        if (pointing || sensors == null || gyroscope == null || gravity == null) return
        pointing = true
        filterPrimed = false
        tracker.start()
        sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME)
        sensors.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!pointing) return
        pointing = false
        sensors?.unregisterListener(this)
        tracker.stop()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> tracker.onGyroscope(values[0], values[1], values[2], event.timestamp)
            Sensor.TYPE_GRAVITY -> tracker.onGravity(values[0], values[1], values[2])
            Sensor.TYPE_ACCELEROMETER -> {
                // The accelerometer also feels the hand's jolts; filtering them out leaves gravity.
                for (i in 0..2) {
                    filtered[i] = if (filterPrimed) filtered[i] + LOW_PASS * (values[i] - filtered[i]) else values[i]
                }
                filterPrimed = true
                tracker.onGravity(filtered[0], filtered[1], filtered[2])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        /** How far each accelerometer reading moves the filtered gravity: about 0.2 s to settle at the game rate. */
        const val LOW_PASS = 0.1f
    }
}

/**
 * A [PhonePointer] that sends its moves, scaled by the user's pointing [speed], to [onMove]. It stops
 * pointing when it leaves the screen, or when the app goes to the background or the phone locks.
 */
@Composable
internal fun rememberPhonePointer(speed: Float, onMove: (Double, Double) -> Unit): PhonePointer {
    val context = LocalContext.current
    val currentOnMove by rememberUpdatedState(onMove)
    val tracker = remember { AimTracker { dx, dy -> currentOnMove(dx, dy) } }
    SideEffect { tracker.speed = speed.toDouble() }
    val pointer = remember(context, tracker) { PhonePointer(context, tracker) }
    LifecycleResumeEffect(pointer) {
        onPauseOrDispose { pointer.stop() }
    }
    return pointer
}

/** Whether the phone has the gyroscope that pointing with it needs. */
@Composable
internal fun rememberHasGyroscope(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }
}
