package app.kestrel.engine

/** When a snipe fires and when its edits lock. Shared by the engine, the scheduler and the UI. */
object BidTiming {
    const val MINIMUM_LEAD_SECONDS = 1
    const val MAXIMUM_LEAD_SECONDS = 90

    /** Changes to the max (or a cancel) after this point no longer reach the bid. */
    const val EDIT_LOCK_BEFORE_FIRE_MILLIS = 2_000L

    fun leadMillis(leadSeconds: Int): Long = leadSeconds.coerceIn(MINIMUM_LEAD_SECONDS, MAXIMUM_LEAD_SECONDS) * 1_000L

    fun fireAtUtcMillis(
        endUtcMillis: Long,
        leadSeconds: Int,
    ): Long = endUtcMillis - leadMillis(leadSeconds)

    fun editsLockAtUtcMillis(
        endUtcMillis: Long,
        leadSeconds: Int,
    ): Long = fireAtUtcMillis(endUtcMillis, leadSeconds) - EDIT_LOCK_BEFORE_FIRE_MILLIS
}

/** The window's schedule and the time limits of every call into the page. All values in milliseconds. */
internal object WindowTimings {
    // Schedule relative to the true end (or to the fire time for long leads).
    const val PAGE_LOAD_BEFORE_END = 90_000L
    const val PAGE_LOAD_BEFORE_FIRE = 30_000L
    const val LAYER_OPEN_BEFORE_END = 45_000L
    const val LAYER_OPEN_BEFORE_FIRE = 15_000L

    /** The page must be ready this long before the latest dispatch to still open the layer, type and fire. */
    const val PAGE_READY_BEFORE_DISPATCH = 5_000L

    /** The bid layer must be open this long before the latest dispatch, to leave time to type the max. */
    const val LAYER_READY_BEFORE_DISPATCH = 1_500L

    /** Less time than this before the end is not enough to prepare a bid. */
    const val MINIMUM_TIME_TO_BID = 6_000L

    /** A bid dispatched later than this before the end cannot reach eBay in time. */
    const val TOO_LATE_BEFORE_END = 1_000L

    /** How late after the fire time a dispatch still counts as on time, so a 1 s lead is not refused as too late. */
    const val DISPATCH_TOLERANCE = 250L

    /** An end further away than this (an end time that moved) releases the phone; the window is scheduled again. */
    const val MAXIMUM_HOLD_BEFORE_END = 6 * 60_000L

    // Waiting.
    const val HOLD_TICK = 1_000L
    const val COARSE_WAKE_BEFORE_FIRE = 250L
    const val FINE_WAIT_STEP = 5L

    // Firing and the reply.
    const val REPLY_TIMEOUT = 4_000L
    const val REPLY_CALL_SLACK = 1_000L
    const val RETRY_MINIMUM_REMAINING = 2_000L
    const val RETRY_ACTIVE_CHECK_TIMEOUT = 1_000L

    // Following the auction with AUTO_REFRESH.
    const val MINIMUM_REFRESH_INTERVAL = 2_000L
    const val REFRESH_SAFETY_BEFORE_DEADLINE = 1_500L
    const val REFRESH_FAILURES_BEFORE_NOTE = 3
    const val MAXIMUM_REFRESH_FAILURES = 5
    const val POST_END_GRACE = 30_000L
    const val MAXIMUM_FOLLOW_AFTER_END = 20 * 60_000L

    // Reading the result.
    const val RESOLVE_WAIT_MINIMUM = 15_000L
    const val RESOLVE_WAIT_MAXIMUM = 30_000L
    const val RESOLVE_RETRY = 15_000L
    const val RESOLVE_ATTEMPTS = 3

    // Loading the page.
    const val PAGE_LOAD_RETRIES = 2
    const val RATE_WALL_BACKOFF_MINIMUM = 8_000L
    const val RATE_WALL_BACKOFF_MAXIMUM = 12_000L
    const val CHALLENGE_WAIT = 20_000L
    const val CHALLENGE_POLL = 2_000L
    const val LOAD_RETRY_PAUSE = 2_000L

    // Time limits of single calls.
    const val CLOCK_SYNC_TIMEOUT = 12_000L
    const val CREATE_PAGE_TIMEOUT = 15_000L
    const val OPEN_ITEM_TIMEOUT = 25_000L
    const val READ_LISTING_TIMEOUT = 5_000L
    const val OPEN_LAYER_TIMEOUT = 15_000L
    const val TYPE_TIMEOUT = 8_000L
    const val TYPE_MINIMUM_BUDGET = 300L
    const val ARM_OBSERVER_TIMEOUT = 3_000L
    const val SUBMIT_TIMEOUT = 3_000L
    const val AUTO_REFRESH_TIMEOUT = 5_000L
    const val MINIMUM_REFRESH_BUDGET = 500L
    const val CLOSE_LAYER_TIMEOUT = 3_000L
    const val REPORT_TIMEOUT = 3_000L
    const val STORE_TIMEOUT = 5_000L
}
