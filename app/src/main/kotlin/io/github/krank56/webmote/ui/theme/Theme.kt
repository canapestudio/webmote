package io.github.krank56.webmote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Amber for what needs care, such as the service menus: Material 3 has no warning role. Fixed colours,
 * not the wallpaper's, so a warning always looks like one.
 */
@Immutable
data class WarningColours(
    /** Icons and text on the screen's background. */
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

private val LightWarning = WarningColours(accent = Color(0xFF8B5000), container = Color(0xFFFFDDB3), onContainer = Color(0xFF2A1800))
private val DarkWarning = WarningColours(accent = Color(0xFFFFB870), container = Color(0xFF6A3C00), onContainer = Color(0xFFFFDDB3))

val LocalWarningColours = staticCompositionLocalOf { LightWarning }

/** Material 3 with the system's dynamic colours (Android 12+), following the system light/dark theme. */
@Composable
fun WebmoteTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    CompositionLocalProvider(LocalWarningColours provides if (dark) DarkWarning else LightWarning) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
