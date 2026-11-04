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

/** The page pressed nothing, so no bid went out; [needsUser] when eBay put something in front of the bid. */
internal data class NotSent(
    val reason: String,
    val needsUser: Boolean,
)

/**
 * The only code in Kestrel that presses eBay's submit button. [submit] presses it once, and the window calls it once:
 * the page allows one press, so an unreadable reply is followed on the page and the result read later, never retried.
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
     * Returns why nothing was sent when the page provably pressed nothing ([BidNotSent]); the bid then does not count
     * as sent and no receipt is written.
     */
    suspend fun submit(
        page: BidPage,
        amount: Money,
        fireAtUtcMillis: Long,
        endUtcMillis: Long,
        latestDispatchUtcMillis: Long,
        beforeTap: suspend () -> Unit,
    ): NotSent? {
        record.submits++
        beforeTap()
        // A store write stuck in I/O cannot be cut short by its time limit; a bid that would now arrive after the end
        // is not sent.
        if (clock.nowUtcMillis() > latestDispatchUtcMillis) {
            val reason = "the phone was too late to press it before the end"
            journal.event(snipeId, TimelineKind.PROBLEM, "Kestrel did not press eBay's bid button: $reason.")
            return NotSent(reason, needsUser = false)
        }
        // From here on the bid counts as sent, even if the call fails: the tap may still have reached eBay.
        record.sent = true
        val call = callWithin(WindowTimings.SUBMIT_TIMEOUT) { page.submit() }
        val refused = (call as? Call.Threw)?.error
        if (refused is BidNotSent) {
            record.sent = false
            val reason = refused.message ?: "the page did not say why"
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "Kestrel did not press eBay's bid button: $reason. No bid was sent.",
                mapOf("attempt" to record.submits, "needsUser" to refused.needsUser),
            )
            return NotSent(reason, refused.needsUser)
        }
        val dispatch = call.valueOrNull()
        val sentAt = dispatch?.let { clock.utcAtElapsedRealtime(it.dispatchedAtElapsedMillis) } ?: clock.nowUtcMillis()
        record.sentAtUtcMillis = sentAt
        record.leadMillis = endUtcMillis - sentAt
        journal.event(
            snipeId,
            TimelineKind.BID_SENT,
            "Bid of ${amount.displayText()} sent ${secondsText(endUtcMillis - sentAt)} before the end.",
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
        return null
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
                    record.standing = BidStanding.REJECTED
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
