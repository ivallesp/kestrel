package app.kestrel.engine

import app.kestrel.model.Money
import app.kestrel.model.TimelineKind

/** What Kestrel did and heard about its own bid during one window. */
internal class BidRecord {
    var sent = false
    var standing = BidStanding.UNKNOWN
    var submits = 0
    var sentAtUtcMillis: Long? = null
    var leadMillis: Long? = null
    var replyLatencyMillis: Long? = null
    var replyText: String? = null

    fun evidence(): BidEvidence = BidEvidence(sent, standing)
}

/**
 * The only code in Kestrel that presses eBay's submit button. Each [submit] presses it once; the window calls it at
 * most twice per snipe, the second time only to retry the identical amount after an unreadable reply.
 */
internal class BidSender(
    private val clock: TrueTime,
    private val journal: EngineJournal,
    private val snipeId: Long,
    private val record: BidRecord,
) {
    /**
     * Presses submit once. [endUtcMillis] is the end the bid aims at, for the lead. [beforeTap] stores that a bid is
     * going out: if the process dies right after the tap, a restarted window must only follow, never bid again.
     */
    suspend fun submit(
        page: BidPage,
        amount: Money,
        fireAtUtcMillis: Long,
        endUtcMillis: Long,
        beforeTap: suspend () -> Unit,
    ) {
        record.submits++
        // From here on the bid counts as sent, even if the call fails: the tap may still have reached eBay.
        record.sent = true
        beforeTap()
        val call = callWithin(WindowTimings.SUBMIT_TIMEOUT) { page.submit() }
        val dispatch = call.valueOrNull()
        val sentAt = dispatch?.let { clock.utcAtElapsedRealtime(it.dispatchedAtElapsedMillis) } ?: clock.nowUtcMillis()
        record.sentAtUtcMillis = sentAt
        record.leadMillis = endUtcMillis - sentAt
        val retryNote = if (record.submits > 1) " This was the one retry, with the same amount." else ""
        journal.event(
            snipeId,
            TimelineKind.BID_SENT,
            "Bid of ${amount.displayText()} sent ${secondsText(endUtcMillis - sentAt)} before the end.$retryNote",
            mapOf(
                "attempt" to record.submits,
                "amount" to amount,
                "fireAtUtcMillis" to fireAtUtcMillis,
                "sentAtUtcMillis" to sentAt,
                "lateByMillis" to sentAt - fireAtUtcMillis,
                "leadMillis" to endUtcMillis - sentAt,
                "inputMethod" to dispatch?.method?.name,
                "buttonLabel" to dispatch?.buttonLabel,
            ),
        )
        if (dispatch == null) {
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "${call.failureText("Pressing eBay's bid button")}. The tap may or may not have reached eBay.",
            )
        }
    }

    /** Waits up to 4 s for eBay's answer to the last tap and records it. */
    suspend fun awaitReply(page: BidPage): BidReply {
        val timeout = WindowTimings.REPLY_TIMEOUT
        val call = callWithin(timeout + WindowTimings.REPLY_CALL_SLACK) { page.awaitReply(timeout) }
        val reply = call.valueOrNull() ?: BidReply.Unconfirmed(null)
        val sentAt = record.sentAtUtcMillis ?: clock.nowUtcMillis()
        record.replyLatencyMillis =
            reply.latencyMillis ?: if (reply is BidReply.Unconfirmed) null else clock.nowUtcMillis() - sentAt
        val message =
            when (reply) {
                is BidReply.Leading -> {
                    record.standing = BidStanding.LEADING
                    record.replyText = reply.text
                    "eBay: \"${reply.text}\""
                }

                is BidReply.Outbid -> {
                    record.standing = BidStanding.OUTBID
                    record.replyText = reply.text
                    "eBay: \"${reply.text}\" Another bidder's max is higher than yours."
                }

                is BidReply.Rejected -> {
                    // A refused retry says nothing about the first tap, whose reply was lost: it may have counted.
                    val retryAfterLostReply = record.submits > 1 && record.standing == BidStanding.UNCONFIRMED
                    if (!retryAfterLostReply) record.standing = BidStanding.REJECTED
                    record.replyText = reply.text
                    "eBay refused the bid: \"${reply.text}\""
                }

                is BidReply.Unconfirmed -> {
                    record.standing = BidStanding.UNCONFIRMED
                    "eBay's reply could not be read within ${timeout / 1_000} s."
                }
            }
        journal.event(
            snipeId,
            TimelineKind.EBAY_REPLY,
            message,
            mapOf(
                "reply" to reply.javaClass.simpleName,
                "latencyMillis" to record.replyLatencyMillis,
                "attempt" to record.submits,
            ),
        )
        return reply
    }
}
