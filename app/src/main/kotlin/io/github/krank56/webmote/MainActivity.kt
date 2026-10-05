package io.github.krank56.webmote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.krank56.webmote.service.RemoteVolume
import io.github.krank56.webmote.ui.WebmoteApp
import io.github.krank56.webmote.ui.theme.WebmoteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The app's single activity. */
class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Nothing to do either way: without it the keep-alive notification is just hidden.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RemoteVolume.attach(this)
        val host = sessionHost
        setContent {
            WebmoteTheme {
                WebmoteApp(host)
            }
        }
        // Ask for notifications once, after the first TV is saved: that's when the keep-alive
        // notification starts to matter.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.registry.tvs.first { it.isNotEmpty() }
                requestNotificationsOnce()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sessionHost.onUserInteraction()
        sessionHost.session.connect()
    }

    private fun requestNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(NOTIFICATIONS_ASKED, false)) return
        prefs.edit { putBoolean(NOTIFICATIONS_ASKED, true) }
        notificationPermission.launch(permission)
    }

    private companion object {
        const val UI_PREFS = "ui"
        const val NOTIFICATIONS_ASKED = "notifications_asked"
    }
}
