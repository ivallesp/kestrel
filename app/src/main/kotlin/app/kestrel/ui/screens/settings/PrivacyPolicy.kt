package app.kestrel.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * The privacy policy, PRIVACY.md in the public repository. Google Play asks every app to link to one from inside the
 * app; Settings › About opens it in the browser.
 */
object PrivacyPolicy {
    const val URL = "https://github.com/ivallesp/kestrel/blob/main/PRIVACY.md"

    /** The address as people read it, in the row's detail. */
    const val SHOWN = "github.com/ivallesp/kestrel"

    /** Said when the phone has nothing that can open the page. */
    const val NOT_OPENED = "No app on this phone can open $SHOWN."

    /** Opens the policy in the browser; false when nothing can open it, so the caller says why. */
    fun open(context: Context): Boolean =
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
}
