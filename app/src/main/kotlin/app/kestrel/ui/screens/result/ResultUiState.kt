package app.kestrel.ui.screens.result

import androidx.compose.runtime.Immutable
import app.kestrel.model.Snipe
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.components.MoneyFigures
import app.kestrel.ui.components.TimelineEntry
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.GroupContext
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.LotStatusMapper
import app.kestrel.ui.model.StateWords
import app.kestrel.ui.theme.StatusTone
import java.time.Instant

/** Everything S4 is built from. [stampSeal] is true the first time a won result opens (DESIGN 2.10). */
data class ResultInputs(
    val snipe: Snipe,
    val events: List<TimelineEvent>,
    val now: Instant,
    val connectivity: Connectivity = Connectivity.Online,
    val group: GroupContext? = null,
    val photo: LotPhoto = LotPhoto.of(snipe),
    val stampSeal: Boolean = false,
) {
    val status: LotStatus get() = LotStatusMapper.statusOf(snipe, events, now, connectivity, group)
}

/** The kinds of result S4 shows (DESIGN S4). [Unknown] is a sent bid whose result Kestrel could not read. */
enum class ResultVariant { Won, Lost, EndedEarly, Cancelled, BidNotSent, BidFailed, Unknown }

/**
 * S4 at one moment: the plate (with the WON seal when won), the status line with day, time and zone, the headline,
 * the card for this kind of result, eBay's reply as a pull-quote, and the receipt. [shareText] is the text receipt
 * the share button sends.
 */
@Immutable
data class ResultUiState(
    val now: Instant,
    val id: Long,
    val variant: ResultVariant,
    val title: String,
    val photo: LotPhoto,
    val tone: StatusTone,
    val statusLine: StateWords,
    val headline: String,
    val receipt: List<TimelineEntry>,
    val shareText: String,
    val canDelete: Boolean,
    /** "Mark as won" and "Mark as lost": a sent bid whose result Kestrel could not read (SnipeRules.settleRefusal). */
    val canSettle: Boolean = false,
    val stampSeal: Boolean = false,
    val paid: PaidCardText? = null,
    val sold: SoldCardText? = null,
    val outcome: OutcomeCardText? = null,
    val secondChance: Boolean = false,
    val quote: PullQuoteText? = null,
)

/** The won card: "WON FOR £101.00" against "£19.00 under your £120.00 max", the ledger and the payment note. */
@Immutable
data class PaidCardText(
    val wonFor: String,
    val under: String?,
    val maxLine: String?,
    val ledger: List<LedgerLine>,
    val total: LedgerLine?,
    val note: String,
    val spoken: String,
)

@Immutable
data class LedgerLine(
    val label: String,
    val value: String,
)

/** The lost card: "SOLD FOR" against "YOUR MAX", then why, in one title and one explanation. */
@Immutable
data class SoldCardText(
    val figures: MoneyFigures,
    val reason: String,
    val explanation: String,
)

/** The card of the other results: [tone] Failed for the two red failures, Ended otherwise; [action] adds a button. */
@Immutable
data class OutcomeCardText(
    val tone: StatusTone,
    val text: String,
    val action: LotAction? = null,
)

/** eBay's reply (DESIGN 3.14): the timing line in sentence case and the quote in curly quotes. */
@Immutable
data class PullQuoteText(
    val timing: String,
    val quote: String,
    val spoken: String,
)
