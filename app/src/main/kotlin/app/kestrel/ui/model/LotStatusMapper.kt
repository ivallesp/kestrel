package app.kestrel.ui.model

import app.kestrel.ebay.BidIncrements
import app.kestrel.engine.BidTiming
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.model.TimelineKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/** Whether the phone can reach eBay now. */
sealed interface Connectivity {
    data object Online : Connectivity

    /** [sinceUtcMillis] is when the connection was lost, for "Offline since 18:02"; null when unknown. */
    data class Offline(
        val sinceUtcMillis: Long?,
    ) : Connectivity
}

/** The snipe's group, for the words of a group cancel: [wonTitle] is the member that was won, if any. */
data class GroupContext(
    val name: String,
    val wonTitle: String?,
)

/** When a snipe bids and when its max stops being editable, from the engine's own timing ([BidTiming]). */
object LotTimes {
    /**
     * The UI stops edits 3 s before the bid (DESIGN P7). The engine accepts them until
     * [BidTiming.EDIT_LOCK_BEFORE_FIRE_MILLIS] before the bid, so the earlier of the two applies and every edit the UI
     * allows still reaches the bid.
     */
    const val EDITS_LOCK_BEFORE_BID_MILLIS = 3_000L

    fun bidAtUtcMillis(snipe: Snipe): Long = BidTiming.fireAtUtcMillis(snipe.endTimeUtcMillis, snipe.leadSeconds)

    fun bidAt(snipe: Snipe): Instant = Instant.ofEpochMilli(bidAtUtcMillis(snipe))

    fun editsLockAt(snipe: Snipe): Instant {
        val engineLock = BidTiming.editsLockAtUtcMillis(snipe.endTimeUtcMillis, snipe.leadSeconds)
        return Instant.ofEpochMilli(minOf(engineLock, bidAtUtcMillis(snipe) - EDITS_LOCK_BEFORE_BID_MILLIS))
    }
}

/**
 * Derives the [LotStatus] the user sees from the domain: the stored [Snipe] (status and result), its timeline, the
 * time and the connection. Pure: the same inputs always give the same status, so every case renders in tests.
 *
 * Rules are checked from the top; the first that matches wins.
 *
 * | Domain status | Condition | LotStatus |
 * |---|---|---|
 * | any status before a result | the end time has passed | ReadingResult |
 * | WATCHING, or no max | | Watching |
 * | ARMED | offline and the bid is due within the hour | OfflineNearBid |
 * | ARMED | the last three checks inside the final hour failed | Unreachable |
 * | ARMED | otherwise | Armed |
 * | PRICE_OVER_MAX | | OverMax |
 * | NEEDS_SIGN_IN | | SignInNeeded |
 * | NEEDS_YOU | the current hand-off says CAPTCHA | Captcha |
 * | NEEDS_YOU | the current hand-off says VERIFICATION | PhoneCheck |
 * | NEEDS_YOU | the current hand-off says SIGN_IN | SignInNeeded |
 * | NEEDS_YOU | the current hand-off says OFFLINE and the bid is within the hour | OfflineNearBid |
 * | NEEDS_YOU | anything else, or the window closed without bidding | NeedsYou |
 * | DRY_RUN_FAILED | the dry run's reason is CAPTCHA | Captcha |
 * | DRY_RUN_FAILED | the dry run's reason is VERIFICATION | PhoneCheck |
 * | DRY_RUN_FAILED | otherwise | DryRunFailed (a sign-in page, or another failure) |
 * | BIDDING | eBay's next bid is above the max | OverMax |
 * | BIDDING | offline | OfflineNearBid |
 * | BIDDING | 60 s or less to the end | FinalMinute |
 * | BIDDING | otherwise (the window opens 120 s before the end) | Armed |
 * | BID_SENT | eBay refused the only bid | BidFailed |
 * | BID_SENT | under 5 s since the bid left | BidSent |
 * | BID_SENT | otherwise | NoReply |
 * | LEADING | | InTheLead (eBay's words from the reply) |
 * | OUTBID | | Outbid (eBay's words when the reply said so) |
 * | EXTENDED | | Extended (the stored end is the new end) |
 * | WON | | Won |
 * | LOST | | Lost |
 * | ENDED_EARLY | | EndedEarly |
 * | CANCELLED | | CancelledByYou |
 * | SKIPPED_BY_GROUP | | CancelledByGroup |
 * | FAILED | the bid left the phone (a sent time, or a BID_SENT event) | BidFailed |
 * | FAILED | otherwise | BidNotSent (signed out, when a sign-in hand-off came after the window opened) |
 *
 * Evidence comes from the timeline the engine writes: the BID_SENT detail (`sentAtUtcMillis`, `amount`), the
 * EBAY_REPLY message and detail (`reply`, `latencyMillis`), the NEEDS_USER and DRY_RUN detail (`reason`, one of
 * engine.NeedsUserReason), WINDOW_OPENED, CANCELLED and RESULT times, and PROBLEM events for failed checks.
 */
object LotStatusMapper {
    /** The last minute before the end, when the hero and S3 switch to the bidding layout. */
    const val FINAL_MINUTE_MILLIS = 60_000L

    /** After this long without eBay's reply, "Bid sent" becomes "No reply". */
    const val NO_REPLY_AFTER_MILLIS = 5_000L

    /** Offline matters once the bid is this close. */
    const val OFFLINE_RISK_MILLIS = 60 * 60_000L

    /** Failed checks in a row, inside the final hour, before a lot says "Can't reach the listing". */
    const val FAILED_CHECKS_FOR_UNREACHABLE = 3

    private const val FINAL_HOUR_MILLIS = 60 * 60_000L

    /** A failed check's PROBLEM event is written right after the attempt's time is stored. */
    private const val CHECK_EVENT_TOLERANCE_MILLIS = 5_000L

    /** Events that prove eBay's page loaded, so earlier failed checks no longer count. */
    private val loadedPageKinds =
        setOf(
            TimelineKind.CHECKED,
            TimelineKind.DRY_RUN,
            TimelineKind.PRICE_OVER_MAX,
            TimelineKind.PAGE_READY,
            TimelineKind.LAYER_READY,
            TimelineKind.NEEDS_USER,
        )

    fun statusOf(
        snipe: Snipe,
        events: List<TimelineEvent>,
        now: Instant,
        connectivity: Connectivity,
        group: GroupContext? = null,
    ): LotStatus {
        val evidence = Evidence(snipe, events.sortedBy { it.atUtcMillis }, now.toEpochMilli(), connectivity)
        val end = Instant.ofEpochMilli(snipe.endTimeUtcMillis)
        if (!snipe.status.isFinal && evidence.nowMillis >= snipe.endTimeUtcMillis) return LotStatus.ReadingResult(end)
        return when (snipe.status) {
            SnipeStatus.WATCHING -> LotStatus.Watching
            SnipeStatus.ARMED -> armed(evidence)
            SnipeStatus.PRICE_OVER_MAX -> overMax(evidence) ?: LotStatus.Watching
            SnipeStatus.NEEDS_SIGN_IN -> LotStatus.SignInNeeded(evidence.bidAt)
            SnipeStatus.NEEDS_YOU -> handOff(evidence)
            SnipeStatus.DRY_RUN_FAILED -> dryRunFailed(evidence)
            SnipeStatus.BIDDING -> bidding(evidence)
            SnipeStatus.BID_SENT -> bidSent(evidence)
            SnipeStatus.LEADING -> LotStatus.InTheLead(evidence.replyText("Leading"), end, evidence.replyMillis())
            SnipeStatus.OUTBID -> LotStatus.Outbid(evidence.replyText("Outbid"), end, evidence.replyMillis())
            SnipeStatus.EXTENDED -> LotStatus.Extended(end)
            SnipeStatus.WON -> LotStatus.Won(snipe.result?.finalPrice ?: snipe.lastPrice, snipe.maxBid, end)
            SnipeStatus.LOST -> lost(evidence, end)
            SnipeStatus.ENDED_EARLY -> LotStatus.EndedEarly(end)
            SnipeStatus.CANCELLED -> LotStatus.CancelledByYou(evidence.timeOf(TimelineKind.CANCELLED))
            SnipeStatus.SKIPPED_BY_GROUP -> cancelledByGroup(evidence, group)
            SnipeStatus.FAILED -> failed(evidence)
        }
    }

    /**
     * When the most recent check of the listing failed, for the row flag "Couldn't check at 18:05 · retrying".
     * Null when the last check worked or no check failed.
     */
    fun failedCheckAt(
        snipe: Snipe,
        events: List<TimelineEvent>,
    ): Instant? {
        val sorted = events.sortedBy { it.atUtcMillis }
        val lastProblem = sorted.lastOrNull { it.kind == TimelineKind.PROBLEM } ?: return null
        val lastLoad = sorted.lastOrNull { it.kind in loadedPageKinds }
        if (lastLoad != null && lastLoad.atUtcMillis > lastProblem.atUtcMillis) return null
        val lastAttempt = snipe.lastCheckedAtUtcMillis ?: return null
        return if (lastProblem.atUtcMillis >= lastAttempt - CHECK_EVENT_TOLERANCE_MILLIS) {
            Instant.ofEpochMilli(lastProblem.atUtcMillis)
        } else {
            null
        }
    }

    private fun armed(evidence: Evidence): LotStatus {
        val snipe = evidence.snipe
        val max = snipe.maxBid ?: return LotStatus.Watching
        val bidAt = LotTimes.bidAt(snipe)
        if (evidence.offline && evidence.bidWithinTheHour) return LotStatus.OfflineNearBid(bidAt)
        if (consecutiveFailedChecks(evidence) >= FAILED_CHECKS_FOR_UNREACHABLE) return LotStatus.Unreachable(bidAt)
        return LotStatus.Armed(max, bidAt, snipe.leadSeconds)
    }

    private fun overMax(evidence: Evidence): LotStatus.OverMax? {
        val snipe = evidence.snipe
        val max = snipe.maxBid ?: return null
        val minimum = minimumNextBid(snipe)
        return LotStatus.OverMax(current = snipe.lastPrice ?: minimum ?: max, max = max, minimumBid = minimum)
    }

    private fun handOff(evidence: Evidence): LotStatus {
        val bidAt = evidence.bidAt
        val latest = evidence.currentHandOff() ?: return LotStatus.NeedsYou(evidence.latestProblem()?.message, bidAt)
        return when (latest.detailText("reason")) {
            "CAPTCHA" -> {
                LotStatus.Captcha(bidAt)
            }

            "VERIFICATION" -> {
                LotStatus.PhoneCheck(bidAt)
            }

            "SIGN_IN" -> {
                LotStatus.SignInNeeded(bidAt)
            }

            "OFFLINE" -> {
                if (bidAt != null && evidence.bidWithinTheHour) {
                    LotStatus.OfflineNearBid(bidAt)
                } else {
                    LotStatus.NeedsYou(latest.message, bidAt)
                }
            }

            else -> {
                LotStatus.NeedsYou(latest.message, bidAt)
            }
        }
    }

    private fun dryRunFailed(evidence: Evidence): LotStatus {
        val dryRun = evidence.history.lastOrNull { it.kind == TimelineKind.DRY_RUN }
        val ranAt = (evidence.snipe.dryRunAtUtcMillis ?: dryRun?.atUtcMillis)?.let(Instant::ofEpochMilli)
        // A check the user must do is named for what it is; the dry run is retried once it is done.
        return when (dryRun?.detailText("reason")) {
            "CAPTCHA" -> LotStatus.Captcha(evidence.bidAt)
            "VERIFICATION" -> LotStatus.PhoneCheck(evidence.bidAt)
            "SIGN_IN" -> LotStatus.DryRunFailed(ranAt, DryRunCause.SIGN_IN_PAGE, evidence.bidAt)
            else -> LotStatus.DryRunFailed(ranAt, DryRunCause.OTHER, evidence.bidAt)
        }
    }

    private fun bidding(evidence: Evidence): LotStatus {
        val snipe = evidence.snipe
        val max = snipe.maxBid ?: return LotStatus.Watching
        // The window keeps the status while it runs; the price can still pass the max before the lock.
        val minimum = minimumNextBid(snipe)
        if (minimum != null && minimum.isAbove(max)) {
            return LotStatus.OverMax(current = snipe.lastPrice ?: minimum, max = max, minimumBid = minimum)
        }
        val bidAt = LotTimes.bidAt(snipe)
        if (evidence.offline) return LotStatus.OfflineNearBid(bidAt)
        return if (snipe.endTimeUtcMillis - evidence.nowMillis <= FINAL_MINUTE_MILLIS) {
            LotStatus.FinalMinute(max, bidAt, snipe.leadSeconds, LotTimes.editsLockAt(snipe))
        } else {
            LotStatus.Armed(max, bidAt, snipe.leadSeconds)
        }
    }

    private fun bidSent(evidence: Evidence): LotStatus {
        val sent = evidence.history.lastOrNull { it.kind == TimelineKind.BID_SENT }
        val sentAtMillis = sent?.detailLong("sentAtUtcMillis") ?: sent?.atUtcMillis ?: evidence.snipe.updatedAtUtcMillis
        val reply = evidence.history.lastOrNull { it.kind == TimelineKind.EBAY_REPLY && it.atUtcMillis >= sentAtMillis }
        val onlyAttempt = (sent?.detailLong("attempt") ?: 1L) == 1L
        if (reply?.detailText("reply") == "Rejected" && onlyAttempt) {
            return LotStatus.BidFailed(quotedText(reply.message), Instant.ofEpochMilli(reply.atUtcMillis))
        }
        // eBay asked for something after the press: the bid may not count until the user answers it on eBay's page.
        if (reply?.detailText("reply") == "NeedsUser") {
            val ask =
                evidence.history.lastOrNull {
                    it.kind == TimelineKind.NEEDS_USER && it.atUtcMillis >= sentAtMillis
                }
            return LotStatus.NeedsYou(ask?.message, bidAt = null)
        }
        val sentAt = Instant.ofEpochMilli(sentAtMillis)
        return if (evidence.nowMillis - sentAtMillis < NO_REPLY_AFTER_MILLIS) {
            LotStatus.BidSent(sent?.detailMoney("amount") ?: evidence.snipe.maxBid, sentAt)
        } else {
            LotStatus.NoReply(sentAt)
        }
    }

    private fun lost(
        evidence: Evidence,
        end: Instant,
    ): LotStatus {
        val snipe = evidence.snipe
        return LotStatus.Lost(snipe.result?.finalPrice ?: snipe.lastPrice, snipe.maxBid, end, evidence.bidWasSent)
    }

    private fun cancelledByGroup(
        evidence: Evidence,
        group: GroupContext?,
    ): LotStatus =
        LotStatus.CancelledByGroup(
            groupName = group?.name,
            wonTitle = group?.wonTitle,
            auctionRunning = evidence.nowMillis < evidence.snipe.endTimeUtcMillis,
        )

    private fun failed(evidence: Evidence): LotStatus {
        val snipe = evidence.snipe
        val at = evidence.timeOf(TimelineKind.RESULT)
        if (evidence.bidWasSent) {
            val refusal =
                evidence.history.lastOrNull {
                    it.kind == TimelineKind.EBAY_REPLY &&
                        it.detailText("reply") == "Rejected"
                }
            // eBay is quoted only when it refused the bid; otherwise Kestrel's own finding says what happened.
            return if (refusal != null) {
                LotStatus.BidFailed(snipe.result?.ebayReplyText ?: quotedText(refusal.message), at)
            } else {
                LotStatus.BidFailed(null, at, reason = snipe.result?.reason)
            }
        }
        val windowOpened =
            evidence.history.lastOrNull { it.kind == TimelineKind.WINDOW_OPENED }?.atUtcMillis ?: Long.MIN_VALUE
        val signedOut =
            evidence.history.lastOrNull {
                it.kind == TimelineKind.NEEDS_USER && it.detailText("reason") == "SIGN_IN" &&
                    it.atUtcMillis >= windowOpened
            }
        return LotStatus.BidNotSent(
            signedOutAt = signedOut?.let { Instant.ofEpochMilli(it.atUtcMillis) },
            reason = snipe.result?.reason,
            at = at,
        )
    }

    /** Failed checks since eBay's page last loaded, inside the final hour, and only while the last check failed. */
    private fun consecutiveFailedChecks(evidence: Evidence): Int {
        if (failedCheckAt(evidence.snipe, evidence.history) == null) return 0
        val finalHourStarts = evidence.snipe.endTimeUtcMillis - FINAL_HOUR_MILLIS
        val lastLoad = evidence.history.lastOrNull { it.kind in loadedPageKinds }?.atUtcMillis ?: Long.MIN_VALUE
        return evidence.history.count {
            it.kind == TimelineKind.PROBLEM && it.atUtcMillis >= finalHourStarts && it.atUtcMillis > lastLoad
        }
    }

    /** eBay's lowest acceptable bid now, as the engine computes it; null when the price is unknown. */
    private fun minimumNextBid(snipe: Snipe): Money? {
        val price = snipe.lastPrice ?: return null
        return try {
            BidIncrements.minimumNextBid(
                snipe.ref.site,
                price,
                snipe.lastBidCount ?: 0,
                snipe.buyerProtectionFee != null,
            )
        } catch (error: RuntimeException) {
            null
        }
    }

    /** The text between the first and the last double quote: eBay's words inside an engine message. */
    internal fun quotedText(message: String): String? {
        val first = message.indexOf('"')
        val last = message.lastIndexOf('"')
        return if (first in 0 until last) message.substring(first + 1, last).takeIf { it.isNotBlank() } else null
    }

    /** What the mapper reads, computed once per call. */
    private class Evidence(
        val snipe: Snipe,
        val history: List<TimelineEvent>,
        val nowMillis: Long,
        connectivity: Connectivity,
    ) {
        val offline: Boolean = connectivity is Connectivity.Offline

        /** The bid time when Kestrel will bid (there is a max), else null. */
        val bidAt: Instant? = snipe.maxBid?.let { LotTimes.bidAt(snipe) }

        val bidWithinTheHour: Boolean get() = LotTimes.bidAtUtcMillis(snipe) - nowMillis <= OFFLINE_RISK_MILLIS

        val bidWasSent: Boolean
            get() = snipe.result?.bidSentAtUtcMillis != null || history.any { it.kind == TimelineKind.BID_SENT }

        /** The latest NEEDS_USER event, unless a bid window opened after it (then the window's outcome is newer). */
        fun currentHandOff(): TimelineEvent? {
            val handOff = history.lastOrNull { it.kind == TimelineKind.NEEDS_USER } ?: return null
            val window = history.lastOrNull { it.kind == TimelineKind.WINDOW_OPENED }
            return handOff.takeIf { window == null || it.atUtcMillis >= window.atUtcMillis }
        }

        fun latestProblem(): TimelineEvent? = history.lastOrNull { it.kind == TimelineKind.PROBLEM }

        /** eBay's words from the latest reply of [reply] type ("Leading", "Outbid"), when it carried any. */
        fun replyText(reply: String): String? =
            history
                .lastOrNull { it.kind == TimelineKind.EBAY_REPLY }
                ?.takeIf { it.detailText("reply") == reply }
                ?.let { quotedText(it.message) }

        fun replyMillis(): Long? =
            history.lastOrNull { it.kind == TimelineKind.EBAY_REPLY }?.detailLong("latencyMillis")

        /** When the latest event of [kind] happened, or when the snipe last changed. */
        fun timeOf(kind: TimelineKind): Instant =
            Instant.ofEpochMilli(history.lastOrNull { it.kind == kind }?.atUtcMillis ?: snipe.updatedAtUtcMillis)
    }
}

private val DetailJson = Json { ignoreUnknownKeys = true }

private fun TimelineEvent.detailObject(): JsonObject? {
    val text = detail ?: return null
    return try {
        DetailJson.parseToJsonElement(text).jsonObject
    } catch (error: SerializationException) {
        null
    } catch (error: IllegalArgumentException) {
        null
    }
}

private fun TimelineEvent.detailPrimitive(key: String): JsonPrimitive? = detailObject()?.get(key) as? JsonPrimitive

private fun TimelineEvent.detailText(key: String): String? = detailPrimitive(key)?.contentOrNull

private fun TimelineEvent.detailLong(key: String): Long? = detailPrimitive(key)?.longOrNull

/** Amounts in the engine's detail JSON read "GBP 120.00". */
private fun TimelineEvent.detailMoney(key: String): Money? {
    val parts = detailText(key)?.split(' ') ?: return null
    if (parts.size != 2) return null
    return try {
        Money.of(parts[1], parts[0])
    } catch (error: NumberFormatException) {
        null
    }
}
