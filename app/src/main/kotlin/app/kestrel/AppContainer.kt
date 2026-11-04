package app.kestrel

import android.content.Context

/** Manual dependency container. Each layer adds its singletons here. */
class AppContainer(
    val context: Context,
)
