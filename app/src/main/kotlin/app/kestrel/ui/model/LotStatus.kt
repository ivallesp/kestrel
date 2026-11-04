package app.kestrel.ui.model

import androidx.compose.runtime.Immutable
import app.kestrel.model.Money
import app.kestrel.model.StatusFamily
import app.kestrel.ui.theme.StatusTone
import java.time.Instant

/**
 * What Kestrel tells the user about one snipe: one case per row of the status table in DESIGN 6.1, plus [NeedsYou]
 * for pages Kestrel hands over that are neither a captcha nor a phone check.
 *
 * Each case has one family (its colour and glyph), one list label, one detail line and at most one action; the words
 * live in LotStatusWords.kt and screens never hard-code them. [LotStatusMapper] derives the case from the domain
 * snipe, its timeline, the clock and the connection. A status is only as strong as its evidence: "Bid sent" is not
 * "In the lead", and "In the lead" is not "Won" ([Won] needs eBay's result on the listing page).
 *
 * The name differs from DESIGN's `SnipeStatus` because the domain already has an enum of that name.
 */
@Immutable
sealed interface LotStatus {
    val kind: LotStatusKind

    val family: StatusFamily get() = kind.family

    /** Only "Bid not sent" and "Bid failed": the red tone of the Ended family. */
    val isFailure: Boolean get() = kind.isFailure

    val tone: StatusTone get() = StatusTone.of(family, isFailure)

    /** No max: Kestrel only watches the price. */
    data object Watching : LotStatus {
        override val kind get() = LotStatusKind.Watching
    }

    /** A bid of [max] is scheduled for [bidAt], [leadSeconds] before the end. */
    data class Armed(
        val max: Money,
        val bidAt: Instant,
        val leadSeconds: Int,
    ) : LotStatus {
        override val kind get() = LotStatusKind.Armed
    }

    /** eBay's next bid ([minimumBid]) is above [max]; usually because the [current] price passed it. */
    data class OverMax(
        val current: Money,
        val max: Money,
        val minimumBid: Money?,
    ) : LotStatus {
        override val kind get() = LotStatusKind.OverMax
    }

    /** eBay signed the user out; bids are paused until they sign in. [bidAt] is null while only watching. */
    data class SignInNeeded(
        val bidAt: Instant?,
    ) : LotStatus {
        override val kind get() = LotStatusKind.SignInNeeded
    }

    /** eBay showed a human check that only the user may solve. */
    data class Captcha(
        val bidAt: Instant?,
    ) : LotStatus {
        override val kind get() = LotStatusKind.Captcha
    }

    /** eBay asked to verify the user (a code by text, for example). */
    data class PhoneCheck(
        val bidAt: Instant?,
    ) : LotStatus {
        override val kind get() = LotStatusKind.PhoneCheck
    }

    /**
     * The practice run before the bid failed: on eBay's sign-in page, on the max, or otherwise. [reason] is the
     * engine's own sentence, which says where it stopped.
     */
    data class DryRunFailed(
        val ranAt: Instant?,
        val cause: DryRunCause,
        val bidAt: Instant?,
        val reason: String? = null,
    ) : LotStatus {
        override val kind get() = LotStatusKind.DryRunFailed
    }

    /** The phone is offline and the bid is due within the hour. */
    data class OfflineNearBid(
        val bidAt: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.OfflineNearBid
    }

    /** Three checks in a row failed inside the final hour. */
    data class Unreachable(
        val bidAt: Instant?,
    ) : LotStatus {
        override val kind get() = LotStatusKind.Unreachable
    }

    /**
     * Kestrel stopped and needs the user for something that is neither a captcha nor a phone check: a page it does
     * not recognise, a max in another currency, or a bid window that closed without bidding. [message] is the
     * engine's own explanation, when it gave one.
     */
    data class NeedsYou(
        val message: String?,
        val bidAt: Instant?,
    ) : LotStatus {
        override val kind get() = LotStatusKind.NeedsYou
    }

    /** The bid window is open and the bid is less than a minute away. The max can change until [editsLockAt]. */
    data class FinalMinute(
        val max: Money,
        val bidAt: Instant,
        val leadSeconds: Int,
        val editsLockAt: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.FinalMinute
    }

    /** The bid left the phone at [sentAt]; eBay has not replied yet. */
    data class BidSent(
        val amount: Money?,
        val sentAt: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.BidSent
    }

    /** eBay replied that the bid leads. Not won until the auction ends. */
    data class InTheLead(
        val ebayText: String?,
        val endsAt: Instant,
        val replyMillis: Long?,
    ) : LotStatus {
        override val kind get() = LotStatusKind.InTheLead
    }

    /** eBay replied (or the live price shows) that another bidder's max is higher. */
    data class Outbid(
        val ebayText: String?,
        val endsAt: Instant,
        val replyMillis: Long?,
    ) : LotStatus {
        override val kind get() = LotStatusKind.Outbid
    }

    /** Five seconds after the bid there is still no reply from eBay. */
    data class NoReply(
        val sentAt: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.NoReply
    }

    /** A late bid reset the clock: the auction now ends at [newEnd]. */
    data class Extended(
        val newEnd: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.Extended
    }

    /** The auction ended at [endedAt] and Kestrel is reading eBay's result. */
    data class ReadingResult(
        val endedAt: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.ReadingResult
    }

    /** eBay's listing page confirms the win. */
    data class Won(
        val wonFor: Money?,
        val max: Money?,
        val endedAt: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.Won
    }

    /** Someone else won. [bidSent] says whether Kestrel's bid went out (it does not when the price passed the max). */
    data class Lost(
        val soldFor: Money?,
        val max: Money?,
        val endedAt: Instant,
        val bidSent: Boolean,
    ) : LotStatus {
        override val kind get() = LotStatusKind.Lost
    }

    /** The seller ended the listing before its end time. */
    data class EndedEarly(
        val endedAt: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.EndedEarly
    }

    /** The user stopped this snipe. */
    data class CancelledByYou(
        val cancelledAt: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.CancelledByYou
    }

    /**
     * The group rule stopped this snipe: [wonTitle] was won in [groupName], or (when [wonTitle] is null) another
     * member was still undecided when this one would have bid. [auctionRunning] allows "Undo cancel".
     */
    data class CancelledByGroup(
        val groupName: String?,
        val wonTitle: String?,
        val auctionRunning: Boolean,
    ) : LotStatus {
        override val kind get() = LotStatusKind.CancelledByGroup
    }

    /**
     * The bid never left the phone. [signedOutAt] is when eBay was found signed out, the commonest cause; otherwise
     * [reason] is the engine's explanation.
     */
    data class BidNotSent(
        val signedOutAt: Instant?,
        val reason: String?,
        val at: Instant,
    ) : LotStatus {
        override val kind get() = LotStatusKind.BidNotSent
    }

    /**
     * The bid left the phone but did not count: eBay refused it, saying [ebayText], or (without eBay's words) Kestrel
     * found why, in [reason], for example a press that never registered.
     */
    data class BidFailed(
        val ebayText: String?,
        val at: Instant,
        val reason: String? = null,
    ) : LotStatus {
        override val kind get() = LotStatusKind.BidFailed
    }
}

/** Why a dry run failed, as far as the copy needs to know: eBay's sign-in page, the max, or anything else. */
enum class DryRunCause { SIGN_IN_PAGE, MAX, OTHER }

/** One per [LotStatus] case: the family, the failure tone and the place on the ladder, without the payload. */
enum class LotStatusKind(
    val family: StatusFamily,
    val isFailure: Boolean = false,
) {
    Watching(StatusFamily.WATCHING),
    Armed(StatusFamily.ARMED),
    OverMax(StatusFamily.ATTENTION),
    SignInNeeded(StatusFamily.ATTENTION),
    Captcha(StatusFamily.ATTENTION),
    PhoneCheck(StatusFamily.ATTENTION),
    DryRunFailed(StatusFamily.ATTENTION),
    OfflineNearBid(StatusFamily.ATTENTION),
    Unreachable(StatusFamily.ATTENTION),
    NeedsYou(StatusFamily.ATTENTION),
    FinalMinute(StatusFamily.BIDDING),
    BidSent(StatusFamily.BIDDING),
    InTheLead(StatusFamily.BIDDING),
    Outbid(StatusFamily.BIDDING),
    NoReply(StatusFamily.BIDDING),
    Extended(StatusFamily.BIDDING),
    ReadingResult(StatusFamily.BIDDING),
    Won(StatusFamily.WON),
    Lost(StatusFamily.ENDED),
    EndedEarly(StatusFamily.ENDED),
    CancelledByYou(StatusFamily.ENDED),
    CancelledByGroup(StatusFamily.ENDED),
    BidNotSent(StatusFamily.ENDED, isFailure = true),
    BidFailed(StatusFamily.ENDED, isFailure = true),
    ;

    val isFinal: Boolean get() = family == StatusFamily.WON || family == StatusFamily.ENDED
}

/**
 * The honest ladder: which status may follow which (DESIGN 6.1 "Allowed transitions").
 *
 * The DESIGN edges:
 * - Watching to Armed and back;
 * - Armed to every Attention case and back;
 * - Armed to FinalMinute to BidSent to InTheLead, Outbid or NoReply;
 * - InTheLead, Outbid and NoReply to Extended (repeatable) and to ReadingResult; Extended to ReadingResult;
 *   ReadingResult to Won or Lost ("Extended" is a step the auction may skip);
 * - FinalMinute and BidSent to BidNotSent or BidFailed;
 * - Armed to EndedEarly or a cancel (by the user or by the group).
 *
 * Added because the engine and data layer produce them (each is a real path, not a shortcut):
 * - one Attention case to another, when a check finds a different problem;
 * - Watching to and from SignInNeeded, Captcha, PhoneCheck and NeedsYou: a watched listing can need sign-in or
 *   a hand-off;
 * - every Attention case to FinalMinute: the bid window also opens from those statuses;
 * - FinalMinute to Armed (an extension before the bid pushes it out of the final minute), to OverMax (the price
 *   passes the max) and to OfflineNearBid (the phone goes offline);
 * - BidSent to Extended (the clock reset before eBay replied), InTheLead and Extended to Outbid (the live price
 *   passed the max), and NoReply to BidSent, InTheLead or Outbid (the one retry with the same amount);
 * - every status before a result to ReadingResult, because the end time passed;
 * - ReadingResult to every ended case: the result page can also show an early end, a refused or missing bid, or the
 *   group's cancel; and back to Extended when eBay moves the end after the old one passed;
 * - Watching, the Attention cases and FinalMinute to the ended cases the engine writes directly: Lost (the price
 *   passed the max at the lock, or a watched auction ended), EndedEarly, the cancels and BidNotSent;
 * - CancelledByGroup back to Armed: "Undo cancel" while the auction runs.
 *
 * A status may always be followed by itself (a new max, a moving end, a ticking countdown).
 */
object LotStatusLadder {
    private val attention = LotStatusKind.entries.filter { it.family == StatusFamily.ATTENTION }.toSet()
    private val handOffs =
        setOf(LotStatusKind.SignInNeeded, LotStatusKind.Captcha, LotStatusKind.PhoneCheck, LotStatusKind.NeedsYou)
    private val beforeResult = LotStatusKind.entries.filter { !it.isFinal && it != LotStatusKind.ReadingResult }.toSet()
    private val ended = LotStatusKind.entries.filter { it.isFinal }.toSet()
    private val endedWithoutBid =
        setOf(
            LotStatusKind.Lost,
            LotStatusKind.EndedEarly,
            LotStatusKind.CancelledByYou,
            LotStatusKind.CancelledByGroup,
            LotStatusKind.BidNotSent,
        )

    private val edges: Map<LotStatusKind, Set<LotStatusKind>> =
        LotStatusKind.entries.associateWith { from -> successorsOf(from) }

    private fun successorsOf(from: LotStatusKind): Set<LotStatusKind> =
        buildSet {
            add(from)
            if (from in beforeResult) add(LotStatusKind.ReadingResult)
            when (from) {
                LotStatusKind.Watching -> {
                    add(LotStatusKind.Armed)
                    addAll(handOffs)
                    addAll(endedWithoutBid - LotStatusKind.CancelledByGroup - LotStatusKind.BidNotSent)
                }

                LotStatusKind.Armed -> {
                    add(LotStatusKind.Watching)
                    addAll(attention)
                    add(LotStatusKind.FinalMinute)
                    add(LotStatusKind.EndedEarly)
                    add(LotStatusKind.CancelledByYou)
                    add(LotStatusKind.CancelledByGroup)
                    add(LotStatusKind.BidNotSent)
                }

                in attention -> {
                    add(LotStatusKind.Armed)
                    addAll(attention)
                    if (from in handOffs) add(LotStatusKind.Watching)
                    add(LotStatusKind.FinalMinute)
                    addAll(endedWithoutBid)
                }

                LotStatusKind.FinalMinute -> {
                    add(LotStatusKind.BidSent)
                    add(LotStatusKind.Armed)
                    add(LotStatusKind.OverMax)
                    add(LotStatusKind.OfflineNearBid)
                    add(LotStatusKind.BidFailed)
                    addAll(endedWithoutBid)
                }

                LotStatusKind.BidSent -> {
                    add(LotStatusKind.InTheLead)
                    add(LotStatusKind.Outbid)
                    add(LotStatusKind.NoReply)
                    add(LotStatusKind.Extended)
                    add(LotStatusKind.BidNotSent)
                    add(LotStatusKind.BidFailed)
                }

                LotStatusKind.InTheLead -> {
                    add(LotStatusKind.Extended)
                    add(LotStatusKind.Outbid)
                }

                LotStatusKind.Outbid -> {
                    add(LotStatusKind.Extended)
                }

                LotStatusKind.NoReply -> {
                    add(LotStatusKind.Extended)
                    add(LotStatusKind.BidSent)
                    add(LotStatusKind.InTheLead)
                    add(LotStatusKind.Outbid)
                }

                LotStatusKind.Extended -> {
                    add(LotStatusKind.Outbid)
                }

                LotStatusKind.ReadingResult -> {
                    addAll(ended)
                    add(LotStatusKind.Extended)
                }

                LotStatusKind.CancelledByGroup -> {
                    add(LotStatusKind.Armed)
                }

                else -> {
                    Unit
                }
            }
        }

    /** True when [to] may follow [from]. */
    fun allows(
        from: LotStatusKind,
        to: LotStatusKind,
    ): Boolean = to in edges.getValue(from)

    /** The statuses that may follow [from], itself included. */
    fun successors(from: LotStatusKind): Set<LotStatusKind> = edges.getValue(from)
}

/** True when [next] is a step this status may take. A screen animates a step and cuts to anything else. */
fun LotStatus.canAdvanceTo(next: LotStatus): Boolean = LotStatusLadder.allows(kind, next.kind)

/**
 * Moves to [next], enforcing the ladder. Throws when [next] is not a step from this status; use [canAdvanceTo] to
 * tell a step from a jump (for example a screen opened after the result).
 */
fun LotStatus.advance(next: LotStatus): LotStatus {
    require(canAdvanceTo(next)) { "$kind cannot be followed by ${next.kind}" }
    return next
}
