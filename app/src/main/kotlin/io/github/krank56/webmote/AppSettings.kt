package io.github.krank56.webmote

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's app preferences, persisted in shared preferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _wakeHintDismissed = MutableStateFlow(prefs.getBoolean(WAKE_HINT_DISMISSED, false))

    /** Whether the user has dismissed the one-time hint shown when Wake-on-LAN doesn't wake the TV. */
    val wakeHintDismissed: StateFlow<Boolean> = _wakeHintDismissed.asStateFlow()

    fun dismissWakeHint() {
        prefs.edit { putBoolean(WAKE_HINT_DISMISSED, true) }
        _wakeHintDismissed.value = true
    }

    companion object {
        private const val WAKE_HINT_DISMISSED = "wake_hint_dismissed"
    }
}
