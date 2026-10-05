package io.github.krank56.webmote.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException

/** Redraws every placed widget from the session; a no-op when none is placed. */
internal object Widgets {
    suspend fun refresh(context: Context) {
        try {
            StripWidget().updateAll(context)
            RemoteWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Webmote", "Couldn't update the widgets", e)
        }
    }
}
