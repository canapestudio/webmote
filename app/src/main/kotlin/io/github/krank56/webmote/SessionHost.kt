package io.github.krank56.webmote

import android.content.Context
import io.github.krank56.webmote.core.TvRegistry
import io.github.krank56.webmote.core.TvSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Holds the app's single [TvSession] for the active TV, so there's never a second connection. */
class SessionHost(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val registry = TvRegistry(File(context.filesDir, "tvs"))
    val session = TvSession(registry, scope)
}
