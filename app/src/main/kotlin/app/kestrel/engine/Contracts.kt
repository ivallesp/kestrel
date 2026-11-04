package app.kestrel.engine

import app.kestrel.model.AutoRefreshState
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeResult
import app.kestrel.model.TimelineKind

/** True time, anchored to elapsedRealtime. Implemented by [TrueTime]. */
interface EngineClock {
    /** Best estimate of true UTC now (SNTP-corrected when synced). */
    fun nowUtcMillis(): Long

    /** Monotonic clock that keeps counting in deep sleep (SystemClock.elapsedRealtime). */
    fun elapsedRealtimeMillis(): Long
}

/** The item page as loaded in the WebView. */
data class PageState(
    val kind: PageKind,
    val url: String,
    val title: String,
    /** Parsed listing when [kind] is NORMAL and the page carries the item model. */
    val listing: Listing?,
)

/** The state of eBay's bid layer after it was opened (or failed to open). */
sealed interface LayerState {
    /** The amount field is ready. [minimum] is the live minimum eBay shows, when it could be read. */
    data class Ready(
        val minimum: Money?,
        val submitLabel: String?,
        val report: String,
    ) : LayerState

    data object NeedsSignIn : LayerState

    /** Something Kestrel does not handle (phone verification, age check, terms, unknown text). */
    data class NeedsUser(
        val text: String,
        val report: String,
    ) : LayerState

    data class Failed(
        val reason: String,
        val report: String,
    ) : LayerState
}

/** What the amount field showed after typing the maximum. */
data class TypedAmount(
    /** The value read back from the field, as text. */
    val fieldValue: String,
    /** Inline error shown by eBay, for example "Enter £66.00 or more", or Kestrel's own note of what is wrong. */
    val inlineError: String?,
    val minimum: Money?,
    /** The page checked that the field holds exactly the amount and eBay's bid button takes it: only then may it bid. */
    val verified: Boolean = false,
    /** Why the page did not verify the amount; null when [verified]. */
    val problem: TypingProblem? = null,
)

/** Why a typed maximum was not verified, sorted by what the window can do about it. */
enum class TypingProblem {
    /** A check, an interstitial or a JavaScript dialog stands in the way: only the user can deal with it. */
    NEEDS_USER,

    /** The maximum is below eBay's minimum: the price passed it. */
    BELOW_MINIMUM,

    /** The field holds the amount but eBay's bid button is still disabled; eBay may still be checking the amount. */
    BUTTON_DISABLED,

    /** Anything else: no field, another value in it, an eBay error, no recognisable bid button. */
    OTHER,
}

/** How the submit tap was delivered. */
enum class InputMethod { NATIVE_TOUCH, DOM_EVENTS }

data class SubmitDispatch(
    val dispatchedAtElapsedMillis: Long,
    val method: InputMethod,
    val buttonLabel: String?,
)

/**
 * Marks a failure of [BidPage.submit] in which the page provably pressed nothing, so no bid was sent. [needsUser] is
 * true when eBay showed something only the user can answer (a check, a dialog over the bid button).
 */
interface BidNotSent {
    val needsUser: Boolean
}

/** eBay's answer to the bid. */
sealed interface BidReply {
    val latencyMillis: Long?

    /** "Your current bid puts you in the lead." */
    data class Leading(
        val text: String,
        override val latencyMillis: Long?,
    ) : BidReply

    /** "Another bidder is winning. Increase your bid.": someone's maximum is higher. */
    data class Outbid(
        val text: String,
        override val latencyMillis: Long?,
    ) : BidReply

    /** eBay refused the bid and said why. */
    data class Rejected(
        val text: String,
        override val latencyMillis: Long?,
    ) : BidReply

    /** No reply could be read in time. [text] is what eBay showed that Kestrel could not place, if anything. */
    data class Unconfirmed(
        override val latencyMillis: Long?,
        val text: String? = null,
    ) : BidReply

    /**
     * eBay answered the press with something only the user can deal with (phone verification, sign-in, a captcha)
     * before its confirmbid went out. The bid may still be pending, so it counts as sent; the user is told at once.
     */
    data class NeedsUser(
        val text: String,
        val reason: NeedsUserReason,
        override val latencyMillis: Long?,
    ) : BidReply
}

/**
 * The part of the bid page a dry run may use. It cannot submit: [DryRun] only ever receives this type.
 */
interface BidLayerPreview {
    /** False once the page can no longer be used (its renderer died, or it was closed); every call then fails. */
    val isUsable: Boolean get() = true

    suspend fun openItem(ref: ItemRef): PageState

    suspend fun readListing(): Listing?

    suspend fun openBidLayer(): LayerState

    suspend fun typeMax(amount: Money): TypedAmount

    suspend fun closeLayer()
}

/** The full bid page used by a live snipe window. Implemented in `web/` over a headless WebView. */
interface BidPage : BidLayerPreview {
    /** Fetch the page's AUTO_REFRESH JSON from inside the page (same origin, with credentials). */
    suspend fun autoRefresh(): AutoRefreshState?

    /**
     * Reads the layer again, without typing, and verifies the amount [typeMax] typed last, as [typeMax] does. For a
     * bid button that eBay enables late, and for a last look before the press.
     */
    suspend fun checkTyped(): TypedAmount

    /** Start recording the bid request's reply; call before [submit]. */
    suspend fun armReplyObserver()

    /**
     * Press eBay's submit button once. Throws an exception that is a [BidNotSent] when the page provably pressed
     * nothing; any other failure means the tap may have reached eBay.
     */
    suspend fun submit(): SubmitDispatch

    suspend fun awaitReply(timeoutMillis: Long): BidReply

    /** A diagnostic dump (URL, title, visible texts, matched selectors) for the timeline. */
    suspend fun report(): String

    fun close()
}

/** Creates bid pages. One page (WebView) per snipe window. */
fun interface BidPageFactory {
    suspend fun create(): BidPage
}

/** Persistence used by the engine. Implemented by the data layer. */
interface SnipeStore {
    suspend fun snipe(id: Long): Snipe?

    suspend fun update(snipe: Snipe)

    suspend fun addEvent(
        snipeId: Long,
        kind: TimelineKind,
        message: String,
        detail: String? = null,
    )

    suspend fun recordPrice(
        snipeId: Long,
        atUtcMillis: Long,
        price: Money,
        bidCount: Int,
    )

    suspend fun group(id: Long): SnipeGroup?

    suspend fun groupMembers(groupId: Long): List<Snipe>
}

/** User-facing alerts. Implemented by the platform layer (notifications). */
interface SnipeNotifier {
    /** The final window opened: show the Live Update countdown to [bidAtUtcMillis]. */
    fun windowOpened(
        snipe: Snipe,
        bidAtUtcMillis: Long,
    )

    /** Update the Live Update (phase text such as "Checking", "Ready", "Bid sent", "Extended"). */
    fun windowProgress(
        snipe: Snipe,
        phase: WindowPhase,
        detail: String? = null,
    )

    fun windowClosed(snipeId: Long)

    fun priceOverMax(
        snipe: Snipe,
        current: Money,
        minimumNext: Money?,
    )

    /** Kestrel needs the user (sign-in, captcha, verification, unknown page). */
    fun needsUser(
        snipe: Snipe,
        reason: NeedsUserReason,
        detail: String?,
    )

    fun result(
        snipe: Snipe,
        result: SnipeResult,
    )
}

enum class WindowPhase { CHECKING, READY, BIDDING, BID_SENT, LEADING, OUTBID, EXTENDED, RESOLVING }

enum class NeedsUserReason { SIGN_IN, CAPTCHA, VERIFICATION, UNKNOWN_PAGE, DRY_RUN_FAILED, OFFLINE, BID_FAILED }
