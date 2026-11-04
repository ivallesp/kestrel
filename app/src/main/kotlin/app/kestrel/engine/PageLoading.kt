package app.kestrel.engine

import app.kestrel.model.Listing
import app.kestrel.model.PageKind
import kotlinx.coroutines.delay

/** A page eBay showed instead of the item that only the user can deal with. */
internal data class UserHandOff(
    val reason: NeedsUserReason,
    val text: String,
)

/** The hand-off for [kind], or null when Kestrel can deal with the page itself (normal page, rate wall, browser check). */
internal fun userHandOffFor(
    kind: PageKind,
    title: String,
): UserHandOff? =
    when (kind) {
        PageKind.SIGN_IN -> {
            UserHandOff(NeedsUserReason.SIGN_IN, "eBay signed you out. Sign in to eBay in Kestrel again.")
        }

        PageKind.THIRD_PARTY_SIGN_IN -> {
            UserHandOff(
                NeedsUserReason.SIGN_IN,
                "eBay sent the page to a Google or Facebook sign-in, which cannot work inside Kestrel. " +
                    "Sign in with your eBay password instead.",
            )
        }

        PageKind.CAPTCHA -> {
            UserHandOff(
                NeedsUserReason.CAPTCHA,
                "eBay wants to check that you are a person. Open the page in Kestrel and answer it yourself.",
            )
        }

        PageKind.OTHER -> {
            UserHandOff(
                NeedsUserReason.UNKNOWN_PAGE,
                "eBay showed a page Kestrel does not recognise (\"$title\"). Open it in Kestrel to see what eBay wants.",
            )
        }

        PageKind.NOT_FOUND -> {
            UserHandOff(NeedsUserReason.UNKNOWN_PAGE, "eBay says the listing was not found.")
        }

        PageKind.NORMAL, PageKind.RATE_WALL, PageKind.CHALLENGE -> {
            null
        }
    }

internal const val RATE_WALL_TEXT = "eBay showed its \"Error Page\", which it uses to slow down busy visitors"
internal const val CHALLENGE_TEXT = "eBay's automatic browser check did not clear"

/**
 * eBay's "Pardon Our Interruption" check clears itself and then shows the item. Wait in place and read the page every
 * few seconds instead of reloading, which would restart the check.
 */
internal suspend fun BidLayerPreview.waitOutChallenge(
    clock: EngineClock,
    maximumWaitMillis: Long,
): Listing? {
    val deadline = clock.nowUtcMillis() + maximumWaitMillis
    while (true) {
        val remaining = deadline - clock.nowUtcMillis()
        if (remaining <= 0) return null
        delay(minOf(WindowTimings.CHALLENGE_POLL, remaining))
        val listing = callWithin(WindowTimings.READ_LISTING_TIMEOUT) { readListing() }.valueOrNull()
        if (listing != null) return listing
    }
}
