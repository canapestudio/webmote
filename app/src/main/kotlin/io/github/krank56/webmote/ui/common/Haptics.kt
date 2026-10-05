package io.github.krank56.webmote.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The haptic ticks buttons give, honouring the user's haptics setting. */
class Haptics internal constructor(private val feedback: HapticFeedback?, private val enabled: Boolean) {
    /** A short tick for a button press. */
    fun press() {
        if (enabled) feedback?.performHapticFeedback(HapticFeedbackType.VirtualKey)
    }

    /** A firmer buzz for a long press. */
    fun longPress() {
        if (enabled) feedback?.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics(feedback = null, enabled = false) }

/** Provides [LocalHaptics] to [content]; [enabled] is the user's haptics setting. */
@Composable
fun ProvideHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    val feedback = LocalHapticFeedback.current
    val haptics = remember(feedback, enabled) { Haptics(feedback, enabled) }
    CompositionLocalProvider(LocalHaptics provides haptics, content = content)
}
