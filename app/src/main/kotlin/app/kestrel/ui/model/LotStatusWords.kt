package app.kestrel.ui.model

import androidx.compose.runtime.Immutable
import app.kestrel.model.Money
import app.kestrel.model.StatusFamily
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.clockWithSeconds
import app.kestrel.ui.format.clockWithTenths
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.spoken
import app.kestrel.ui.format.timeLeft
import app.kestrel.ui.format.withCurlyQuotes
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

// The words of every status (DESIGN 6.1). Screens read them from here and never write state words themselves.

/**
 * A list label: [words] in the family accent (never truncated) and an optional [detail] after " · " in ink soft
 * (the only part that may ellipsise or wrap). States that are a whole sentence, such as
 * "£250.00 over your £1,900.00 max", have no detail.
 */
@Immutable
data class StateWords(
    val words: String,
    val detail: String? = null,
) {
    /** "Armed · max £45.00". */
    val text: String get() = if (detail == null) words else "$words · $detail"
}

/** The one action a status offers. Screens decide where it goes and what it opens. */
enum class LotAction(
    val label: String,
) {
    SET_MAX("Set a max"),
    EDIT_MAX("Edit max"),
    RAISE_MAX("Raise max"),
    SIGN_IN("Sign in"),
    OPEN_EBAY_PAGE("Open eBay page"),
    FIX("Fix"),
    OPEN_ON_EBAY("Open on eBay"),
    VIEW_RECEIPT("View receipt"),
    UNDO_CANCEL("Undo cancel"),
}

/** The list label, as rows and the hero body show it: "Armed · max £45.00", "Final minute · bids in 30 s". */
fun LotStatus.label(
    now: Instant,
    zone: ZoneId = AuctionZone,
): StateWords = labelOf(this, now, zone, spoken = false)

/** The list label for TalkBack: durations in words ("bids in 30 seconds"). */
fun LotStatus.spokenLabel(
    now: Instant,
    zone: ZoneId = AuctionZone,
): String {
    val label = labelOf(this, now, zone, spoken = true)
    val prefix = if (kind.family == StatusFamily.ATTENTION) "Needs attention: " else ""
    return prefix + if (label.detail == null) label.words else "${label.words}, ${label.detail}"
}

/** The words of the state chip (hero plate, S3 header, S5 header): "Armed", "Final minute", "Over your max". */
fun LotStatus.chipText(zone: ZoneId = AuctionZone): String =
    when (this) {
        LotStatus.Watching -> "Watching"
        is LotStatus.Armed -> "Armed"
        is LotStatus.OverMax -> "Over your max"
        is LotStatus.SignInNeeded -> "Sign-in needed"
        is LotStatus.Captcha -> "eBay wants a quick check"
        is LotStatus.PhoneCheck -> "eBay wants to verify you"
        is LotStatus.DryRunFailed -> "Dry run failed"
        is LotStatus.OfflineNearBid -> "Phone offline"
        is LotStatus.Unreachable -> "Can’t reach the listing"
        is LotStatus.NeedsYou -> "Needs you"
        is LotStatus.FinalMinute -> "Final minute"
        is LotStatus.BidSent -> "Bid sent"
        is LotStatus.InTheLead -> "In the lead"
        is LotStatus.Outbid -> "Outbid"
        is LotStatus.NoReply -> "No reply yet"
        is LotStatus.Extended -> "Extended · now ends ${newEnd.clockWithSeconds(zone)}"
        is LotStatus.ReadingResult -> "Reading the result"
        is LotStatus.Won -> "Won"
        is LotStatus.Lost -> "Lost"
        is LotStatus.EndedEarly -> "Ended early"
        is LotStatus.CancelledByYou -> "Cancelled"
        is LotStatus.CancelledByGroup -> if (wonTitle == null) "Skipped" else "Cancelled"
        is LotStatus.BidNotSent -> "Bid not sent"
        is LotStatus.BidFailed -> "Bid failed"
    }

/** The detail or hero line: one sentence that says what is happening and what happens next. */
fun LotStatus.detail(
    now: Instant,
    zone: ZoneId = AuctionZone,
): String =
    when (this) {
        LotStatus.Watching -> {
            "Watching. Set a max to snipe it."
        }

        is LotStatus.Armed -> {
            "Armed · bids ${atMoment(now, bidAt, zone)}, $leadSeconds\u202Fs before the end"
        }

        is LotStatus.OverMax -> {
            if (current.isAbove(max)) {
                "The price is ${current.formatted()}, ${(current - max).formatted()} over your max. " +
                    "Raise it or stop sniping."
            } else {
                val needed = minimumBid?.formatted()?.let { "at least $it" } ?: "more"
                "eBay needs $needed, more than your ${max.formatted()} max. Raise it or stop sniping."
            }
        }

        is LotStatus.SignInNeeded -> {
            if (bidAt == null) {
                "eBay signed you out. Sign in so Kestrel can keep checking this listing."
            } else {
                "eBay signed you out. Sign in before ${moment(now, bidAt, zone)} so Kestrel can bid."
            }
        }

        is LotStatus.Captcha -> {
            val deadline = bidAt?.let { " before ${moment(now, it, zone)}" } ?: ""
            "Solve it on eBay’s page$deadline. Kestrel never solves these for you."
        }

        is LotStatus.PhoneCheck -> {
            "eBay asked for a code by text. Enter it on eBay’s page; Kestrel will retry the dry run."
        }

        is LotStatus.DryRunFailed -> {
            val start = ranAt?.let { "At ${clockFor(now, it, zone)} " } ?: ""
            val what =
                when (cause) {
                    DryRunCause.SIGN_IN_PAGE -> "eBay showed a sign-in page instead of the bid panel."
                    DryRunCause.OTHER -> "the dry run didn’t reach the bid panel."
                }
            val sentence = (start + what).replaceFirstChar { it.uppercase() }
            val fix = bidAt?.let { " Fix it before ${moment(now, it, zone)}." } ?: " Fix it before the bid."
            sentence + fix
        }

        is LotStatus.OfflineNearBid -> {
            "Kestrel can’t bid at ${moment(now, bidAt, zone)} unless the phone reconnects."
        }

        is LotStatus.Unreachable -> {
            "Kestrel couldn’t check this listing three times. It keeps trying."
        }

        is LotStatus.NeedsYou -> {
            message?.withCurlyQuotes() ?: "Kestrel stopped and needs you to look at this listing on eBay."
        }

        is LotStatus.FinalMinute -> {
            "Kestrel bids ${max.formatted()} in ${timeLeft(now, bidAt)}, at ${bidAt.clockWithSeconds(zone)}, " +
                "$leadSeconds\u202Fs before the end."
        }

        is LotStatus.BidSent -> {
            "Bid sent at ${sentAt.clockWithTenths(zone)} · waiting for eBay’s reply"
        }

        is LotStatus.InTheLead -> {
            if (ebayText == null) {
                "eBay accepted your bid. Not won until it ends."
            } else {
                "eBay accepted your bid: “$ebayText” Not won until it ends."
            }
        }

        is LotStatus.Outbid -> {
            val quote = ebayText?.let { "eBay replied: “$it” " } ?: ""
            "${quote}Someone’s max is higher than yours."
        }

        is LotStatus.NoReply -> {
            "No reply from eBay yet. Kestrel will read the result when the auction ends."
        }

        is LotStatus.Extended -> {
            "A late bid reset the clock to 60\u202Fs. Kestrel is still following."
        }

        is LotStatus.ReadingResult -> {
            "Reading eBay’s result…"
        }

        is LotStatus.Won -> {
            wonDetail(wonFor, max)
        }

        is LotStatus.Lost -> {
            lostDetail(soldFor, max)
        }

        is LotStatus.EndedEarly -> {
            "The seller ended the listing early. Nothing was bid."
        }

        is LotStatus.CancelledByYou -> {
            "You stopped this snipe. Nothing was bid."
        }

        is LotStatus.CancelledByGroup -> {
            when {
                wonTitle != null -> "Kestrel cancelled this because you won $wonTitle."
                groupName != null -> "Kestrel didn’t bid because another snipe in $groupName was still undecided."
                else -> "Kestrel didn’t bid because another snipe in the group was still undecided."
            }
        }

        is LotStatus.BidNotSent -> {
            when {
                signedOutAt != null -> "eBay signed you out at ${signedOutAt.clockWithSeconds(zone)}. Nothing was bid."
                reason != null -> reason.withCurlyQuotes()
                else -> "Kestrel didn’t send the bid. Nothing was bid."
            }
        }

        is LotStatus.BidFailed -> {
            when {
                ebayText == null -> "eBay didn’t accept the bid. Nothing was bid."

                // eBay's own sentence ends with its punctuation, so no full stop follows the closing quote.
                endsASentence(ebayText) -> "eBay didn’t accept the bid: “$ebayText” Nothing was bid."

                else -> "eBay didn’t accept the bid: “$ebayText”. Nothing was bid."
            }
        }
    }

/** The status's action at [now], or null. "Edit max" ends when the edits lock, "Undo cancel" when the auction ends. */
fun LotStatus.action(now: Instant): LotAction? =
    when (this) {
        LotStatus.Watching -> LotAction.SET_MAX

        is LotStatus.Armed -> LotAction.EDIT_MAX

        is LotStatus.OverMax -> LotAction.RAISE_MAX

        is LotStatus.SignInNeeded -> LotAction.SIGN_IN

        is LotStatus.Captcha -> LotAction.OPEN_EBAY_PAGE

        is LotStatus.PhoneCheck -> LotAction.OPEN_EBAY_PAGE

        is LotStatus.DryRunFailed -> LotAction.FIX

        is LotStatus.OfflineNearBid -> null

        is LotStatus.Unreachable -> LotAction.OPEN_ON_EBAY

        is LotStatus.NeedsYou -> LotAction.OPEN_EBAY_PAGE

        is LotStatus.FinalMinute -> if (now < editsLockAt) LotAction.EDIT_MAX else null

        is LotStatus.BidSent -> null

        is LotStatus.InTheLead -> null

        is LotStatus.Outbid -> null

        is LotStatus.NoReply -> null

        is LotStatus.Extended -> null

        is LotStatus.ReadingResult -> null

        is LotStatus.Won -> LotAction.VIEW_RECEIPT

        is LotStatus.Lost -> LotAction.VIEW_RECEIPT

        is LotStatus.EndedEarly -> null

        // Undo for a cancel by the user lives in the snackbar shown right after it.
        is LotStatus.CancelledByYou -> null

        is LotStatus.CancelledByGroup -> if (wonTitle != null && auctionRunning) LotAction.UNDO_CANCEL else null

        is LotStatus.BidNotSent -> if (signedOutAt != null) LotAction.SIGN_IN else LotAction.VIEW_RECEIPT

        is LotStatus.BidFailed -> LotAction.VIEW_RECEIPT
    }

private fun labelOf(
    status: LotStatus,
    now: Instant,
    zone: ZoneId,
    spoken: Boolean,
): StateWords =
    when (status) {
        LotStatus.Watching -> {
            StateWords("Watching", "no max yet")
        }

        is LotStatus.Armed -> {
            StateWords("Armed", "max ${status.max.formatted()}")
        }

        is LotStatus.OverMax -> {
            StateWords(overMaxWords(status))
        }

        is LotStatus.SignInNeeded -> {
            StateWords("Sign-in needed", "paused")
        }

        is LotStatus.Captcha -> {
            StateWords("eBay wants a quick check")
        }

        is LotStatus.PhoneCheck -> {
            StateWords("eBay wants to verify you")
        }

        is LotStatus.DryRunFailed -> {
            StateWords("Dry run failed")
        }

        is LotStatus.OfflineNearBid -> {
            StateWords("Phone offline", "bid at risk")
        }

        is LotStatus.Unreachable -> {
            StateWords("Can’t reach the listing")
        }

        is LotStatus.NeedsYou -> {
            StateWords("Needs you", "open the listing")
        }

        is LotStatus.FinalMinute -> {
            val left = Duration.between(now, status.bidAt)
            StateWords("Final minute", "bids in ${if (spoken) left.spoken() else timeLeft(now, status.bidAt)}")
        }

        is LotStatus.BidSent -> {
            StateWords("Bid sent", "waiting for eBay")
        }

        is LotStatus.InTheLead -> {
            StateWords("In the lead", "ends ${clockFor(now, status.endsAt, zone)}")
        }

        is LotStatus.Outbid -> {
            StateWords("Outbid", "ends ${clockFor(now, status.endsAt, zone)}")
        }

        is LotStatus.NoReply -> {
            StateWords("Bid sent", "no reply yet")
        }

        is LotStatus.Extended -> {
            StateWords("Extended", "now ends ${status.newEnd.clockWithSeconds(zone)}")
        }

        is LotStatus.ReadingResult -> {
            StateWords("Ended", "reading the result")
        }

        is LotStatus.Won -> {
            StateWords("Won", status.endedAt.dayDate(zone))
        }

        is LotStatus.Lost -> {
            StateWords("Lost", status.endedAt.dayDate(zone))
        }

        is LotStatus.EndedEarly -> {
            StateWords("Ended early by the seller")
        }

        is LotStatus.CancelledByYou -> {
            StateWords("Cancelled", status.cancelledAt.dayDate(zone))
        }

        is LotStatus.CancelledByGroup -> {
            val group = status.groupName ?: "the group"
            if (status.wonTitle == null) {
                StateWords("Skipped", "another in $group was undecided")
            } else {
                StateWords("Cancelled", "you won another in $group")
            }
        }

        is LotStatus.BidNotSent -> {
            StateWords("Bid not sent")
        }

        is LotStatus.BidFailed -> {
            StateWords("Bid failed")
        }
    }

/**
 * "£250.00 over your £1,900.00 max", or, when only eBay's next bid is above the max,
 * "Next bid £121.00 is over your £120.00 max".
 */
private fun overMaxWords(status: LotStatus.OverMax): String =
    when {
        status.current.isAbove(status.max) -> {
            "${(status.current - status.max).formatted()} over your ${status.max.formatted()} max"
        }

        status.minimumBid != null -> {
            "Next bid ${status.minimumBid.formatted()} is over your ${status.max.formatted()} max"
        }

        else -> {
            "Over your ${status.max.formatted()} max"
        }
    }

private fun wonDetail(
    wonFor: Money?,
    max: Money?,
): String =
    when {
        wonFor == null -> {
            "You won"
        }

        max == null || max.currency != wonFor.currency -> {
            "You won · won for ${wonFor.formatted()}"
        }

        max.isAbove(wonFor) -> {
            "You won · won for ${wonFor.formatted()}, ${(max - wonFor).formatted()} under your ${max.formatted()} max"
        }

        else -> {
            "You won · won for ${wonFor.formatted()}, your max"
        }
    }

/** True only for two amounts in the same currency, so pounds are never compared with euros. */
internal fun Money.isAbove(other: Money): Boolean = currency == other.currency && minor > other.minor

private fun lostDetail(
    soldFor: Money?,
    max: Money?,
): String =
    when {
        soldFor == null && max == null -> "Someone else won."
        soldFor == null -> "Someone else won. Your max was ${max?.formatted()}."
        max == null -> "Sold for ${soldFor.formatted()}."
        else -> "Outbid · sold for ${soldFor.formatted()}. Your max was ${max.formatted()}."
    }

/** "at 18:21:34" on the same day, "Mon 10 Nov at 20:10" on another. Seconds only within the hour. */
private fun atMoment(
    now: Instant,
    instant: Instant,
    zone: ZoneId,
): String =
    if (isSameDay(
            now,
            instant,
            zone,
        )
    ) {
        "at ${clockFor(now, instant, zone)}"
    } else {
        "${instant.dayDate(zone)} at ${clockFor(now, instant, zone)}"
    }

/** "18:21:34" on the same day, "Mon 10 Nov at 20:10" on another. */
private fun moment(
    now: Instant,
    instant: Instant,
    zone: ZoneId,
): String =
    if (isSameDay(
            now,
            instant,
            zone,
        )
    ) {
        clockFor(now, instant, zone)
    } else {
        "${instant.dayDate(zone)} at ${clockFor(now, instant, zone)}"
    }

/** True when [text] ends with a full stop, a question or exclamation mark, or an ellipsis. */
private fun endsASentence(text: String): Boolean = text.trimEnd().lastOrNull()?.let { it in ".!?…" } == true
