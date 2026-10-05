package io.github.krank56.webmote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.krank56.webmote.ui.WebmoteApp
import io.github.krank56.webmote.ui.theme.WebmoteTheme

/** The app's single activity. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebmoteTheme {
                WebmoteApp()
            }
        }
    }
}
