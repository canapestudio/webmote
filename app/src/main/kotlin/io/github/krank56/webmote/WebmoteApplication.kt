package io.github.krank56.webmote

import android.app.Application
import android.content.Context

class WebmoteApplication : Application() {
    val host: SessionHost by lazy { SessionHost(this) }
}

/** The app's single [SessionHost]. */
val Context.sessionHost: SessionHost get() = (applicationContext as WebmoteApplication).host
