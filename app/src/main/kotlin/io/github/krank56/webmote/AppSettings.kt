package io.github.krank56.webmote

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's app preferences, persisted in shared preferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _haptics = MutableStateFlow(prefs.getBoolean(HAPTICS, true))
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    private val _volumeKeys = MutableStateFlow(prefs.getBoolean(VOLUME_KEYS, true))

    /** Whether the phone's volume keys control the TV while connected. */
    val volumeKeys: StateFlow<Boolean> = _volumeKeys.asStateFlow()

    private val _touchpadSensitivity = MutableStateFlow(prefs.getFloat(TOUCHPAD_SENSITIVITY, DEFAULT_SENSITIVITY))

    /** Multiplies touchpad drags into pointer movement. */
    val touchpadSensitivity: StateFlow<Float> = _touchpadSensitivity.asStateFlow()

    private val _wakeHintDismissed = MutableStateFlow(prefs.getBoolean(WAKE_HINT_DISMISSED, false))

    /** Whether the user has dismissed the one-time hint shown when Wake-on-LAN doesn't wake the TV. */
    val wakeHintDismissed: StateFlow<Boolean> = _wakeHintDismissed.asStateFlow()

    private val _simpleMode = MutableStateFlow(prefs.getBoolean(SIMPLE_MODE, false))

    /** Whether the app opens on Simple mode's one screen of large, labelled buttons instead of the tabs. */
    val simpleMode: StateFlow<Boolean> = _simpleMode.asStateFlow()

    private val _showArrows = MutableStateFlow(prefs.getBoolean(SHOW_ARROWS, true))

    /** Whether Simple mode's screen shows the arrows, OK and Back. */
    val showArrows: StateFlow<Boolean> = _showArrows.asStateFlow()

    fun setHaptics(enabled: Boolean) {
        prefs.edit { putBoolean(HAPTICS, enabled) }
        _haptics.value = enabled
    }

    fun setVolumeKeys(enabled: Boolean) {
        prefs.edit { putBoolean(VOLUME_KEYS, enabled) }
        _volumeKeys.value = enabled
    }

    fun setTouchpadSensitivity(sensitivity: Float) {
        val value = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        prefs.edit { putFloat(TOUCHPAD_SENSITIVITY, value) }
        _touchpadSensitivity.value = value
    }

    fun dismissWakeHint() {
        prefs.edit { putBoolean(WAKE_HINT_DISMISSED, true) }
        _wakeHintDismissed.value = true
    }

    fun setSimpleMode(enabled: Boolean) {
        prefs.edit { putBoolean(SIMPLE_MODE, enabled) }
        _simpleMode.value = enabled
    }

    fun setShowArrows(enabled: Boolean) {
        prefs.edit { putBoolean(SHOW_ARROWS, enabled) }
        _showArrows.value = enabled
    }

    companion object {
        const val DEFAULT_SENSITIVITY = 1.5f
        const val MIN_SENSITIVITY = 0.5f
        const val MAX_SENSITIVITY = 4f

        private const val HAPTICS = "haptics"
        private const val VOLUME_KEYS = "volume_keys"
        private const val TOUCHPAD_SENSITIVITY = "touchpad_sensitivity"
        private const val WAKE_HINT_DISMISSED = "wake_hint_dismissed"
        private const val SIMPLE_MODE = "simple_mode"
        private const val SHOW_ARROWS = "show_arrows"
    }
}
