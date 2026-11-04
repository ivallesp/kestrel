package app.kestrel

import android.app.Application
import app.kestrel.platform.NotificationChannels

open class KestrelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        // Must stay safe before the first unlock (direct boot): channels live in the system, not in app storage.
        NotificationChannels.create(this)
    }

    /** Tests replace the container to swap in fakes. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
