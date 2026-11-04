package app.kestrel.engine

import app.kestrel.model.AutoRefreshState
import app.kestrel.model.TimelineKind

/**
 * Calls the page's AUTO_REFRESH at eBay's own pace: never sooner than the refreshInSeconds of the last reply and never
 * more often than every 2 s, the rate eBay's own page uses in the last minute.
 */
internal class AuctionPoller(
    private val clock: EngineClock,
    private val journal: EngineJournal,
    private val snipeId: Long,
) {
    private var nextPollAtUtcMillis = 0L

    var consecutiveFailures = 0
        private set

    val millisUntilDue: Long get() = nextPollAtUtcMillis - clock.nowUtcMillis()

    /** Ask again at the next opportunity, for example to confirm a surprising reply. */
    fun pollSoon() {
        nextPollAtUtcMillis = clock.nowUtcMillis()
    }

    /**
     * One poll when it is due; null when it is not due, failed, or could not finish
     * [WindowTimings.REFRESH_SAFETY_BEFORE_DEADLINE] before [deadlineUtcMillis].
     */
    suspend fun pollIfDue(
        page: BidPage,
        deadlineUtcMillis: Long?,
    ): AutoRefreshState? {
        val now = clock.nowUtcMillis()
        if (now < nextPollAtUtcMillis) return null
        var timeout = WindowTimings.AUTO_REFRESH_TIMEOUT
        if (deadlineUtcMillis != null) {
            // A slow reply must never delay the next step of the bid.
            timeout = minOf(timeout, deadlineUtcMillis - now - WindowTimings.REFRESH_SAFETY_BEFORE_DEADLINE)
            if (timeout < WindowTimings.MINIMUM_REFRESH_BUDGET) return null
        }
        return poll(page, timeout)
    }

    /** One poll now, whatever the pace, bounded by [timeoutMillis]. */
    suspend fun poll(
        page: BidPage,
        timeoutMillis: Long,
    ): AutoRefreshState? {
        val call = callWithin(timeoutMillis) { page.autoRefresh() }
        val state = call.valueOrNull()
        val suggested = (state?.refreshInSeconds ?: 0) * 1_000L
        nextPollAtUtcMillis = clock.nowUtcMillis() + maxOf(WindowTimings.MINIMUM_REFRESH_INTERVAL, suggested)
        if (state == null) {
            consecutiveFailures++
            if (consecutiveFailures == WindowTimings.REFRESH_FAILURES_BEFORE_NOTE) {
                journal.event(
                    snipeId,
                    TimelineKind.PROBLEM,
                    "eBay's live updates are not coming through (${call.failureText("AUTO_REFRESH")}).",
                )
            }
            return null
        }
        consecutiveFailures = 0
        return state
    }
}
