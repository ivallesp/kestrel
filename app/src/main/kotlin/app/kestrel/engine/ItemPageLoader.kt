package app.kestrel.engine

import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.PageKind
import app.kestrel.model.TimelineKind

/** What one load of the item page gave. */
internal sealed interface PageLoad {
    data class Ready(
        val listing: Listing,
        val url: String,
    ) : PageLoad

    /** eBay showed a page only the user can deal with. */
    data class HandOff(
        val handOff: UserHandOff,
    ) : PageLoad

    /** Worth another try after [ItemPageLoader.pauseBeforeRetry]. */
    data class Retry(
        val kind: PageKind?,
        val reason: String,
    ) : PageLoad
}

private const val NO_LISTING_TEXT = "The item page had no listing details"

/** Loads the item page once and sorts what eBay showed into ready, hand to the user, or try again. */
internal class ItemPageLoader(
    private val clock: EngineClock,
    private val journal: EngineJournal,
    private val snipeId: Long,
) {
    /** [readyByUtcMillis] bounds the whole load, including waiting out eBay's browser check. */
    suspend fun loadOnce(
        page: BidLayerPreview,
        ref: ItemRef,
        readyByUtcMillis: Long,
    ): PageLoad {
        val budget = minOf(WindowTimings.OPEN_ITEM_TIMEOUT, readyByUtcMillis - clock.nowUtcMillis())
        val call = callWithin(budget) { page.openItem(ref) }
        val state = call.valueOrNull() ?: return PageLoad.Retry(null, call.failureText("Loading the item page"))
        val handOff = userHandOffFor(state.kind, state.title)
        return when {
            state.kind == PageKind.NORMAL -> {
                val listing =
                    state.listing ?: callWithin(WindowTimings.READ_LISTING_TIMEOUT) { page.readListing() }.valueOrNull()
                listing?.let { PageLoad.Ready(it, state.url) } ?: PageLoad.Retry(state.kind, NO_LISTING_TEXT)
            }

            state.kind == PageKind.CHALLENGE -> {
                journal.event(
                    snipeId,
                    TimelineKind.PROBLEM,
                    "eBay is checking the browser. Kestrel waits for the check to clear.",
                )
                val wait = minOf(WindowTimings.CHALLENGE_WAIT, readyByUtcMillis - clock.nowUtcMillis())
                val listing = page.waitOutChallenge(clock, wait)
                // The check's own URL says nothing about the item, so only the listing itself is compared later.
                if (listing != null) PageLoad.Ready(listing, ref.url) else PageLoad.Retry(state.kind, CHALLENGE_TEXT)
            }

            state.kind == PageKind.RATE_WALL -> {
                PageLoad.Retry(state.kind, RATE_WALL_TEXT)
            }

            // A listing that is briefly "not found" can be an eBay hiccup; one more look before giving up.
            state.kind == PageKind.NOT_FOUND -> {
                PageLoad.Retry(state.kind, "eBay said the listing was not found")
            }

            handOff != null -> {
                PageLoad.HandOff(handOff)
            }

            else -> {
                PageLoad.Retry(state.kind, "eBay showed an unexpected page")
            }
        }
    }

    /** eBay's rate wall needs 8 to 12 s to lift; other failures get a short pause. */
    fun pauseBeforeRetry(
        retry: PageLoad.Retry,
        attempt: Int,
    ): Long =
        if (retry.kind == PageKind.RATE_WALL) {
            stableBetween(
                WindowTimings.RATE_WALL_BACKOFF_MINIMUM,
                WindowTimings.RATE_WALL_BACKOFF_MAXIMUM,
                snipeId,
                attempt.toLong(),
            )
        } else {
            WindowTimings.LOAD_RETRY_PAUSE
        }
}
