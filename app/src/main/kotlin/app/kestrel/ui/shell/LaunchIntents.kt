package app.kestrel.ui.shell

import android.content.Intent
import app.kestrel.platform.AppLinks

/** What an intent asks the app to show, and how the back stack starts. Pure, so every launch is testable. */
object LaunchIntents {
    /**
     * - A notification's snipe link (AppLinks, kestrel://snipe/<id>) opens that snipe's detail.
     * - A shared text/plain (the eBay app's or a browser's Share) opens Add with the text; Add finds the listing in it.
     * - Anything else (the launcher) asks for nothing.
     */
    fun destinationOf(intent: Intent?): Destination? {
        if (intent == null) return null
        AppLinks.snipeIdFrom(intent)?.let { return Destination.Lot(it) }
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/plain") == true) {
            val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
            return Destination.Add(shared?.takeIf { it.isNotEmpty() })
        }
        return null
    }

    /**
     * The back stack of a fresh start: Onboarding on the first launch, otherwise Home with the asked-for screen on
     * top, so back from a notification's snipe lands on the list.
     */
    fun startStack(
        onboardingDone: Boolean,
        asked: Destination?,
    ): List<Destination> =
        when {
            !onboardingDone -> listOf(Destination.Onboarding)
            asked == null || asked == Destination.Home -> listOf(Destination.Home)
            else -> listOf(Destination.Home, asked)
        }
}
