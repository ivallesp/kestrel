package app.kestrel

import android.app.Application
import app.kestrel.platform.NotificationChannels
import app.kestrel.ui.shell.KestrelImageLoader
import app.kestrel.ui.shell.UiPreferences
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

open class KestrelApp :
    Application(),
    SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    /** What only the UI remembers (onboarding done). Created on first use, once per process. */
    val uiPreferences: UiPreferences by lazy { UiPreferences.create(this) }

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        // Must stay safe before the first unlock (direct boot): channels live in the system, not in app storage.
        NotificationChannels.create(this)
    }

    /** Tests replace the container to swap in fakes. */
    protected open fun createContainer(): AppContainer = AppContainer(this)

    /** One image loader for the app: screens show listing photos from it and notifications reuse its disk cache. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = KestrelImageLoader.create(context)
}
