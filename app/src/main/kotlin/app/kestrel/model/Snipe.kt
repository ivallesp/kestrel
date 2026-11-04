package app.kestrel.model

/** The user's instruction for one listing, plus what Kestrel last knew about it. Times are UTC epoch milliseconds. */
data class Snipe(
    val id: Long,
    val ref: ItemRef,
    val title: String,
    val imageUrl: String?,
    val sellerName: String?,
    /** Null means watching only (no bid will be placed). The hard ceiling otherwise. */
    val maxBid: Money?,
    val leadSeconds: Int,
    val groupId: Long?,
    val status: SnipeStatus,
    val endTimeUtcMillis: Long,
    val lastPrice: Money?,
    val lastBidCount: Int?,
    val lastCheckedAtUtcMillis: Long?,
    val postage: Money?,
    val buyerProtectionFee: Money?,
    val buyItNow: Money?,
    val hasReserve: Boolean,
    val reserveMet: Boolean?,
    val extendedBidding: Boolean,
    val dryRunAtUtcMillis: Long?,
    val dryRunPassed: Boolean?,
    val result: SnipeResult?,
    val createdAtUtcMillis: Long,
    val updatedAtUtcMillis: Long,
) {
    val isWatchOnly: Boolean get() = maxBid == null
}

/** What happened when the snipe ran. */
data class SnipeResult(
    val outcome: SnipeOutcome,
    /** The final price of the listing, when known. */
    val finalPrice: Money?,
    /** When the submit was dispatched (UTC, true time). */
    val bidSentAtUtcMillis: Long?,
    /** How many milliseconds before the true end the bid was sent. */
    val bidLeadMillis: Long?,
    /** Time from submit to eBay's reply. */
    val replyLatencyMillis: Long?,
    /** eBay's own words, verbatim (layer notice or ended banner). */
    val ebayReplyText: String?,
    /** Plain-language reason for failures and skips. */
    val reason: String?,
)

enum class SnipeOutcome {
    WON,
    LOST,
    ENDED_EARLY,
    PRICE_PASSED_MAX,
    NOT_PLACED,
    FAILED,
    CANCELLED,
    SKIPPED_BY_GROUP,
    UNKNOWN,
}

enum class SnipeStatus(
    val family: StatusFamily,
) {
    WATCHING(StatusFamily.WATCHING),
    ARMED(StatusFamily.ARMED),
    PRICE_OVER_MAX(StatusFamily.ATTENTION),
    NEEDS_SIGN_IN(StatusFamily.ATTENTION),
    NEEDS_YOU(StatusFamily.ATTENTION),
    DRY_RUN_FAILED(StatusFamily.ATTENTION),
    BIDDING(StatusFamily.BIDDING),
    BID_SENT(StatusFamily.BIDDING),
    LEADING(StatusFamily.BIDDING),
    OUTBID(StatusFamily.BIDDING),
    EXTENDED(StatusFamily.BIDDING),
    WON(StatusFamily.WON),
    LOST(StatusFamily.ENDED),
    ENDED_EARLY(StatusFamily.ENDED),
    CANCELLED(StatusFamily.ENDED),
    SKIPPED_BY_GROUP(StatusFamily.ENDED),
    FAILED(StatusFamily.ENDED),
    ;

    val isFinal: Boolean get() = family == StatusFamily.WON || family == StatusFamily.ENDED

    /** True while Kestrel still intends to bid (or is bidding) on this listing. */
    val isActiveBid: Boolean get() =
        this in
            setOf(ARMED, PRICE_OVER_MAX, NEEDS_SIGN_IN, NEEDS_YOU, DRY_RUN_FAILED, BIDDING)
}

/** The six visible families. Colour is always paired with an icon and words. */
enum class StatusFamily { WATCHING, ARMED, ATTENTION, BIDDING, WON, ENDED }

/** "Win 1 of N": once [targetWins] members are won, the rest are cancelled. */
data class SnipeGroup(
    val id: Long,
    val name: String,
    val targetWins: Int = 1,
)

/** One line of a snipe's history. Shown in the detail screen and kept as a receipt. */
data class TimelineEvent(
    val id: Long,
    val snipeId: Long,
    val atUtcMillis: Long,
    val kind: TimelineKind,
    val message: String,
    /** Optional machine detail (JSON), for example which selector matched or timings. */
    val detail: String? = null,
)

enum class TimelineKind {
    ADDED,
    ARMED,
    EDITED,
    CHECKED,
    PRICE_OVER_MAX,
    DRY_RUN,
    WINDOW_OPENED,
    CLOCK_SYNCED,
    PAGE_READY,
    LAYER_READY,
    BID_SENT,
    EBAY_REPLY,
    EXTENDED,
    ENDED,
    RESULT,
    NEEDS_USER,
    PROBLEM,
    CANCELLED,
}
