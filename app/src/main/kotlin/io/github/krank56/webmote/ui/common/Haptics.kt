package io.github.krank56.webmote.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The haptic ticks buttons give. */
class Haptics internal constructor(private val feedback: HapticFeedback?) {
    /** A short tick for a button press. */
    fun press() {
        feedback?.performHapticFeedback(HapticFeedbackType.VirtualKey)
    }

    /** A firmer buzz for a long press. */
    fun longPress() {
        feedback?.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics(feedback = null) }

/** Provides [LocalHaptics] to [content]. */
@Composable
fun ProvideHaptics(content: @Composable () -> Unit) {
    val feedback = LocalHapticFeedback.current
    val haptics = remember(feedback) { Haptics(feedback) }
    CompositionLocalProvider(LocalHaptics provides haptics, content = content)
}
