package app.kestrel.ui.screens.lot

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import app.kestrel.model.Money
import app.kestrel.ui.components.MoneyFigures
import app.kestrel.ui.components.TimelineEntry
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.screens.add.WordsCheck
import app.kestrel.ui.screens.result.ResultUiState
import app.kestrel.ui.theme.StatusTone
import java.time.Instant

/** What the lot destination shows: nothing yet, a missing snipe, the live detail (S3) or, once known, the result. */
@Immutable
sealed interface LotPage {
    /** Before the first read of the snipe (local, so this lasts a moment and shows no loader). */
    data object Loading : LotPage

    /** The snipe is not on the list: deleted, or purged after a delete. */
    data object Gone : LotPage

    data class Live(
        val state: LotUiState,
    ) : LotPage

    /** The result is known: S3 crossfades into S4 in place. */
    data class Finished(
        val state: ResultUiState,
    ) : LotPage
}

/**
 * S3 at one moment (DESIGN S3): the photo band, the sheet with the state chip, the countdown and times, the
 * final-minute scale, the status card, the money, the edit row, the checks that passed and the timeline. Every state
 * word comes from the status model; the ViewModel ticks [now].
 */
@Immutable
data class LotUiState(
    val now: Instant,
    val id: Long,
    val title: String,
    val photo: LotPhoto,
    val status: LotStatus,
    val chipText: String,
    val countdown: CountdownText,
    val times: TimesColumnText,
    val money: MoneyFigures,
    val menu: LotMenuState,
    val offline: OfflineNote? = null,
    val scale: FinalMinuteScaleState? = null,
    val card: StatusCard? = null,
    /** A quiet line where a lot far from its bid has no card: "Dry run about 21:33, 10 min before the end." */
    val note: String? = null,
    /** The extended-bidding explanation (rule MF33), for listings that use it. */
    val extendedNote: String? = null,
    val edit: EditRowState? = null,
    /** The checks that passed for this lot, one sentence each ("Dry run at 18:11:40 · nothing was sent"). */
    val checks: List<String> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    /** What the polite live region says now; it changes only at the moments of DESIGN S3 ("30 seconds to the bid"). */
    val announcement: String? = null,
    val sheet: EditSheetState? = null,
    /** The first two words of the title, for the bar once the sheet is scrolled up ("Canon AE-1"). */
    val shortTitle: String = title,
)

/**
 * The countdown: [figure] in countdown ("36") with [unit] ("s left") in the final minute, otherwise countdownMedium
 * ("13 m 07 s") with "left". With an empty [figure] only the words show, in the countdown unit style: "Ended" from
 * the end on, and "Ends 18:21:40" once a bid failed. [spoken] is its one sentence for TalkBack (rule MF47).
 */
@Immutable
data class CountdownText(
    val figure: String,
    val unit: String,
    val finalMinute: Boolean,
    val spoken: String,
)

/**
 * The times column (from C2): "TODAY · GMT" over "BIDS 18:21:34", "ENDS 18:21:40" and a ticking "NOW 18:21:04".
 * More than an hour from the end only "ENDS 21:43" shows, with [note] ("Bids 6 s before") under it.
 */
@Immutable
data class TimesColumnText(
    val heading: String,
    val rows: List<TimeRowText>,
    val spoken: String,
    val note: String? = null,
)

@Immutable
data class TimeRowText(
    val label: String,
    val time: String,
    val ticks: Boolean = false,
)

/**
 * The final-minute scale (DESIGN 3.12): [elapsedSeconds] of the 60 before the end (whole seconds, so progress steps
 * once a second), the bid tick at [bidFraction] (null when the bid falls outside the scale), the start label ("Final
 * minute" or "Extended"), and [flat] once the bid is sent.
 */
@Immutable
data class FinalMinuteScaleState(
    val elapsedSeconds: Int,
    val bidFraction: Float?,
    val startLabel: String,
    val flat: Boolean,
    val spoken: String,
)

/** The glyph at the start of a status card: the gavel, a waiting indicator, or the attention and failure glyphs. */
enum class CardGlyph { Gavel, Waiting, Warning, Error }

/**
 * The bid card of DESIGN 3.13 and its attention and failure variants: [tone] picks the container (Bidding, Attention or
 * the red Failed), [title] is line 1 (with a tabular span on a ticking figure) and [detail] line 2; [action] adds the
 * status's button ("Open eBay page", "Sign in").
 */
@Immutable
data class StatusCard(
    val tone: StatusTone,
    val glyph: CardGlyph,
    val title: AnnotatedString,
    val detail: String,
    val action: LotAction? = null,
)

/** "Edit max" (or "Set a max") with its deadline: "Until 18:21:31, 3 s before the bid". */
@Immutable
data class EditRowState(
    val action: LotAction,
    val note: String?,
    val enabled: Boolean,
)

/**
 * The overflow menu: "Open on eBay" always, "Test bidding now" while a dry run can run ([testBidding]), "Stop sniping"
 * ([stopSniping], null when hidden), "Delete" if allowed.
 */
@Immutable
data class LotMenuState(
    val stopSniping: MenuEntry?,
    val canDelete: Boolean,
    val testBidding: Boolean = false,
    val group: GroupMenu? = null,
)

/**
 * The lot's group items, while its group may still change (not finished, bid window not open): "Add to a group" for
 * an armed lot outside any group; "Move to another group" and "Remove from [currentGroup]" for a lot in one.
 */
@Immutable
data class GroupMenu(
    val currentGroup: String?,
    val canChoose: Boolean,
)

@Immutable
data class MenuEntry(
    val label: String,
    val enabled: Boolean,
)

/** The offline banner (DESIGN 3.20): "Offline since 18:02" over what it means. */
@Immutable
data class OfflineNote(
    val title: String,
    val detail: String,
)

/** Which edit sheet is open: edit an armed max, raise one the price passed, or set the first max of a watched lot. */
enum class SheetKind { Edit, Raise, Set }

/**
 * The edit-max sheet (DESIGN "Edit-max sheet"): its words and limits. The typed amount lives in the ViewModel's
 * TextFieldState; [EditMaxForm] turns it into the sum row, the error and whether Save is enabled.
 */
@Immutable
data class EditSheetState(
    val kind: SheetKind,
    val title: String,
    val photo: LotPhoto,
    val lotTitle: String,
    val lotLine: String,
    val currency: String,
    val currencySymbol: String,
    val minimum: Money?,
    val postage: Money?,
    val current: Money?,
    /** "Change it until 18:21:31, 3 s before the bid." or, when raising, "Bids at 21:02:09 GMT". */
    val deadline: String?,
    /** Set once the edits locked: "The bid window is open, so this can’t change now. Kestrel bids £120.00 at …" */
    val locked: String?,
    val primary: String,
    val endAt: Instant,
    val leadOptions: List<Int> = emptyList(),
    val selectedLead: Int? = null,
    val leadHint: String? = null,
    /** Why the last save was refused, in Kestrel's words. */
    val error: String? = null,
    val saving: Boolean = false,
    /** Settings "Suggest odd pence". */
    val suggestOddPence: Boolean = true,
    /** The max spelt out before it is saved, when it is more than three times the price (as in S2). */
    val wordsCheck: WordsCheck? = null,
)
