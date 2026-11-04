package app.kestrel.ui.screens.add

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.data.EditResult
import app.kestrel.engine.BidTiming
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.clockWithSeconds
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.format.withCurlyQuotes
import app.kestrel.ui.format.zoneName
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotTimes
import app.kestrel.ui.shell.ShellMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** The three edit sheets (DESIGN "Edit-max sheet"): change the max, raise it past eBay's next bid, or set the first. */
enum class EditMaxMode {
    /** "Edit your max", from "Edit max". */
    Edit,

    /** "Raise your max", from "Raise max": prefilled with eBay's next bid. */
    Raise,

    /** "Set your max", from "Set a max" on a watched lot: empty, with odd pence and the lead time. */
    Set,
    ;

    companion object {
        /** The sheet a snipe asks for: no max sets one, a max below eBay's next bid raises it, else it edits. */
        fun of(
            snipe: Snipe,
            minimum: Money?,
        ): EditMaxMode {
            val max = snipe.maxBid ?: return Set
            return if (minimum != null && minimum.currency == max.currency && minimum > max) Raise else Edit
        }
    }
}

/** What the line above the buttons says. */
enum class SheetLineKind {
    /** "Change it until 18:21:31, 3 s before the bid." */
    Deadline,

    /** "Bids at 21:02:09 GMT", or the full plan on a new max. */
    Plan,

    /** "The bid window is open, so this can’t change now." over "Kestrel bids £120.00 at 18:21:34." */
    Locked,

    /** The data layer refused the save, in its words, then the plan that stands. */
    Refused,
}

/** One line with a glyph: [figure] is the clock time inside [text], kept with its words (tabular in a plan). */
@Immutable
data class SheetLine(
    val kind: SheetLineKind,
    val text: String,
    val figure: String? = null,
)

/** The lot line of a sheet: the 40 dp plate, the title on one line, the price and the end. */
@Immutable
data class SheetLotLine(
    val title: String,
    val photo: LotPhoto?,
    /** "£96.00 now · ends 18:21:40 GMT" */
    val line: String,
)

/** Everything the edit sheet shows at [now]. */
@Immutable
data class EditMaxUiState(
    val now: Instant,
    val mode: EditMaxMode,
    val title: String,
    val lot: SheetLotLine,
    val max: MaxEntry,
    val worstCase: WorstCase,
    /** The odd-pence card and the lead time belong to "Set your max" only. */
    val oddPence: OddPence?,
    val timing: BidTimingChoice?,
    val line: SheetLine,
    /** True once the edits have locked or the data layer refused the save: the field shows [standingMax]. */
    val blocked: Boolean,
    /** The max the bid goes out with, whatever is typed. */
    val standingMax: Money?,
    val primaryLabel: String,
    val primaryEnabled: Boolean,
    /** "Cancel", or "Close" once nothing can change. */
    val dismissLabel: String,
    val wordsCheck: WordsCheck? = null,
    /** Which opening of the sheet this state belongs to. */
    val sessionId: Int = 0,
)

/** The sheet is done: close it and, when there is one, leave [message] for the screen under it. */
data class EditMaxDone(
    val message: ShellMessage?,
)

/**
 * The edit sheet's logic, shared by Home, Lot and the screens a notification opens. It saves through the app's
 * snipe actions, never above what the user typed, and stops accepting a new max when the edits lock
 * ([LotTimes.editsLockAt], 3 s before the bid).
 */
class EditMaxViewModel(
    private val snipeId: Long,
    private val services: EditMaxServices,
) : ViewModel() {
    /** The max field, hoisted here and keyed to the snipe, so typing never rebuilds it. */
    val maxField = TextFieldState()

    private val session = MutableStateFlow<Session?>(null)
    private val doneChannel = Channel<EditMaxDone>(Channel.BUFFERED)

    val done: Flow<EditMaxDone> = doneChannel.receiveAsFlow()

    /** Null until [open] has read the snipe, and when the snipe no longer exists. */
    val uiState: StateFlow<EditMaxUiState?> =
        combine(
            services.snipe(snipeId),
            services.ticks().onStart { emit(Instant.ofEpochMilli(services.nowUtcMillis())) },
            snapshotFlow { maxField.text.toString() },
            session,
        ) { snipe, now, text, session ->
            if (snipe == null || session == null) null else EditMaxPlanner.state(snipe, session, maxField, text, now)
        }.onEach { state -> if (state != null && state.blocked) showStandingMax(state.standingMax) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Once nothing can change, the field shows the max the bid goes out with, not a max that will not count. */
    private fun showStandingMax(standing: Money?) {
        val text = standing?.let(MaxRules::fieldText).orEmpty()
        if (maxField.text.toString() != text) maxField.setTextAndPlaceCursorAtEnd(text)
    }

    /** How many times a sheet has opened; each opening's state carries its number. */
    private var openings = 0

    /**
     * Starts a sheet: [requested] is the variant the user tapped, or null to let the snipe decide. Returns the
     * opening's id: show [uiState] only once its [EditMaxUiState.sessionId] matches, so a sheet opened again never
     * shows the last opening's field for a frame.
     */
    fun open(requested: EditMaxMode?): Int {
        val id = ++openings
        session.value = null
        viewModelScope.launch {
            val snipe = services.snipe(snipeId).first() ?: return@launch
            val minimum = EditMaxPlanner.minimum(snipe)
            val mode = requested ?: EditMaxMode.of(snipe, minimum)
            when (mode) {
                EditMaxMode.Edit -> {
                    snipe.maxBid?.let { maxField.setTextAndPlaceCursorAtEnd(MaxRules.fieldText(it)) }
                }

                EditMaxMode.Raise -> {
                    minimum?.let { maxField.setTextAndSelectAll(MaxRules.fieldText(it)) }
                        ?: maxField.clearText()
                }

                EditMaxMode.Set -> {
                    maxField.clearText()
                }
            }
            session.value =
                Session(
                    id = id,
                    mode = mode,
                    leadSeconds = snipe.leadSeconds,
                    previousMax = snipe.maxBid,
                    suggestOddPence = services.suggestOddPence(),
                )
        }
        return id
    }

    fun selectLead(index: Int) {
        val seconds = MaxRules.LeadOptions.getOrNull(index) ?: return
        session.update { it?.copy(leadSeconds = seconds) }
    }

    fun useOddPence() {
        val state = uiState.value ?: return
        val suggestion = state.oddPence as? OddPence.Suggestion ?: return
        maxField.setTextAndPlaceCursorAtEnd(MaxRules.fieldText(suggestion.amount))
    }

    fun save() {
        val state = uiState.value ?: return
        val current = session.value ?: return
        if (!state.primaryEnabled) return
        viewModelScope.launch {
            val snipe = services.snipe(snipeId).first() ?: return@launch
            val typed = EditMaxPlanner.typed(snipe, maxField.text.toString()) as? TypedMax.Valid ?: return@launch
            if (MaxRules.needsWordsCheck(typed.max, snipe.lastPrice) && !current.wordsConfirmed) {
                session.update { it?.copy(checkingWords = true) }
                return@launch
            }
            store(snipe, typed.max, current)
        }
    }

    fun confirmWords() {
        session.update { it?.copy(checkingWords = false, wordsConfirmed = true) }
        save()
    }

    fun dismissWords() {
        session.update { it?.copy(checkingWords = false) }
    }

    private suspend fun store(
        snipe: Snipe,
        max: Money,
        current: Session,
    ) {
        session.update { it?.copy(saving = true, refusal = null) }
        try {
            if (current.mode == EditMaxMode.Set && current.leadSeconds != snipe.leadSeconds) {
                val lead = services.setLead(snipeId, current.leadSeconds)
                if (lead !is EditResult.Done) return refuse(lead)
            }
            if (max == snipe.maxBid) {
                doneChannel.trySend(EditMaxDone(message = null))
                return
            }
            when (val result = services.updateMax(snipeId, max)) {
                is EditResult.Done -> {
                    doneChannel.trySend(
                        EditMaxDone(messageFor(current.mode, result.snipe, current.previousMax)),
                    )
                }

                else -> {
                    refuse(result)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            session.update { it?.copy(refusal = error.message?.withCurlyQuotes()) }
        } finally {
            session.update { it?.copy(saving = false, wordsConfirmed = false) }
        }
    }

    private fun refuse(result: EditResult) {
        val reason =
            when (result) {
                is EditResult.NotAllowed -> result.reason.withCurlyQuotes()
                EditResult.NotFound -> "This snipe no longer exists."
                is EditResult.Done -> null
            }
        session.update { it?.copy(refusal = reason) }
    }

    /** "Max changed to £130.00" or "Max raised to £2,200.00" with Undo while the edits are open; "Armed: …" for a new max. */
    private fun messageFor(
        mode: EditMaxMode,
        saved: Snipe,
        previous: Money?,
    ): ShellMessage {
        val max = saved.maxBid
        val now = services.nowUtcMillis()
        if (mode == EditMaxMode.Set || previous == null || max == null) {
            return ShellMessage(AddPlanner.armedMessage(LotTimes.bidAt(saved), Instant.ofEpochMilli(now)))
        }
        val text =
            if (mode ==
                EditMaxMode.Raise
            ) {
                "Max raised to ${max.formatted()}"
            } else {
                "Max changed to ${max.formatted()}"
            }
        // Undo is offered only while it can still reach the bid, for as long as the snackbar shows.
        val undoOpen = LotTimes.editsLockAt(saved).toEpochMilli() - now > ShellMessage.UNDO_MILLIS
        if (!undoOpen) return ShellMessage(text)
        return ShellMessage(text, actionLabel = "Undo", durationMillis = ShellMessage.UNDO_MILLIS) {
            services.runDetached { services.updateMax(snipeId, previous) }
        }
    }

    /** One opening of the sheet. */
    internal data class Session(
        val mode: EditMaxMode,
        val id: Int = 0,
        val leadSeconds: Int,
        val previousMax: Money?,
        val checkingWords: Boolean = false,
        val wordsConfirmed: Boolean = false,
        val saving: Boolean = false,
        val refusal: String? = null,
        /** Settings "Suggest odd pence". */
        val suggestOddPence: Boolean = true,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The edit sheet's words and figures, from the snipe and what is typed. Pure. */
internal object EditMaxPlanner {
    fun minimum(snipe: Snipe): Money? {
        val price = snipe.lastPrice ?: return null
        return MaxRules.minimumBid(snipe.ref.site, price, snipe.lastBidCount ?: 0, snipe.buyerProtectionFee != null)
    }

    fun typed(
        snipe: Snipe,
        text: String,
    ): TypedMax {
        val currency = snipe.lastPrice?.currency ?: snipe.maxBid?.currency ?: snipe.ref.site.currency
        return MaxRules.read(text, currency, minimum(snipe))
    }

    fun state(
        snipe: Snipe,
        session: EditMaxViewModel.Session,
        field: TextFieldState,
        text: String,
        now: Instant,
    ): EditMaxUiState {
        val mode = session.mode
        val typed = typed(snipe, text)
        val lockAt = LotTimes.editsLockAt(snipe)
        val locked = now >= lockAt
        val blocked = locked || session.refusal != null
        // Once nothing can change, the sheet shows the max the bid goes out with, never one that will not count.
        val shown = if (blocked) snipe.maxBid?.let { TypedMax.Valid(it) } ?: TypedMax.Empty else typed
        val currency = snipe.lastPrice?.currency ?: snipe.maxBid?.currency ?: snipe.ref.site.currency
        val minimum = minimum(snipe)
        val lead = if (mode == EditMaxMode.Set) session.leadSeconds else snipe.leadSeconds
        val bidAt = Instant.ofEpochMilli(BidTiming.fireAtUtcMillis(snipe.endTimeUtcMillis, lead))
        val primaryLabel = primaryLabelOf(mode)
        return EditMaxUiState(
            now = now,
            mode = mode,
            title = titleOf(mode),
            lot = SheetLotLine(title = snipe.title, photo = LotPhoto.of(snipe), line = lotLine(snipe, now)),
            max =
                MaxEntry(
                    field = field,
                    currencySymbol = MaxRules.currencySymbol(currency),
                    minimum = (minimum ?: Money(1, currency)).formatted(),
                    error =
                        (typed as? TypedMax.TooLow)?.takeIf { !blocked }?.let {
                            MaxRules.tooLowMessage(
                                it.minimum,
                            )
                        },
                    readOnly = blocked,
                ),
            worstCase = AddPlanner.worstCase(shown, snipe.postage),
            oddPence =
                if (mode == EditMaxMode.Set && !blocked && session.suggestOddPence) {
                    MaxRules.oddPence(typed, currency)
                } else {
                    null
                },
            timing =
                if (mode ==
                    EditMaxMode.Set
                ) {
                    AddPlanner.timing(session.leadSeconds, snipe.extendedBidding)
                } else {
                    null
                },
            line = line(mode, snipe, typed, lockAt, bidAt, locked, session.refusal, now),
            blocked = blocked,
            standingMax = snipe.maxBid,
            primaryLabel = primaryLabel,
            primaryEnabled = typed is TypedMax.Valid && !blocked && !session.saving,
            dismissLabel = if (blocked) "Close" else "Cancel",
            wordsCheck = wordsCheck(typed, snipe.lastPrice, session.checkingWords, primaryLabel),
            sessionId = session.id,
        )
    }

    private fun titleOf(mode: EditMaxMode): String =
        when (mode) {
            EditMaxMode.Edit -> "Edit your max"
            EditMaxMode.Raise -> "Raise your max"
            EditMaxMode.Set -> "Set your max"
        }

    private fun primaryLabelOf(mode: EditMaxMode): String =
        when (mode) {
            EditMaxMode.Edit -> "Save max"
            EditMaxMode.Raise -> "Raise max"
            EditMaxMode.Set -> "Arm snipe"
        }

    private fun wordsCheck(
        typed: TypedMax,
        current: Money?,
        checking: Boolean,
        confirmLabel: String,
    ): WordsCheck? {
        if (!checking || typed !is TypedMax.Valid || current == null) return null
        return AddPlanner.wordsCheck(typed.max, current, confirmLabel)
    }

    /**
     * "£96.00 now · ends 18:21:40 GMT"; another day: "£61.00 now · ends Thu 13 Nov, 19:30 GMT". The zone is always
     * there, as on S3's sheets, so the same sheet never reads two ways.
     */
    fun lotLine(
        snipe: Snipe,
        now: Instant,
    ): String {
        val end = Instant.ofEpochMilli(snipe.endTimeUtcMillis)
        val clock = "${clockFor(now, end)} ${end.zoneName()}"
        val ends = if (isSameDay(now, end)) clock else "${end.dayDate()}, $clock"
        val price = snipe.lastPrice?.let { "${it.formatted()} now · " }.orEmpty()
        return "${price}ends $ends"
    }

    private fun line(
        mode: EditMaxMode,
        snipe: Snipe,
        typed: TypedMax,
        lockAt: Instant,
        bidAt: Instant,
        locked: Boolean,
        refusal: String?,
        now: Instant,
    ): SheetLine {
        val bidClock = clockFor(now, bidAt)
        val bidMoment = if (isSameDay(now, bidAt)) bidClock else "${bidAt.dayDate()} at $bidClock"
        val standing = snipe.maxBid?.let { "Kestrel bids ${it.formatted()} at $bidMoment." }
        if (locked) {
            val text = standing?.let { "${MaxRules.LOCKED_SENTENCE}\n$it" } ?: "Too late to set a max for this auction."
            return SheetLine(SheetLineKind.Locked, text, figure = bidClock.takeIf { standing != null })
        }
        if (refusal != null) {
            // The refusal and the plan that stands read as two lines.
            val text = listOfNotNull(refusal, standing).joinToString("\n")
            return SheetLine(SheetLineKind.Refused, text, figure = bidClock.takeIf { standing != null })
        }
        return when (mode) {
            EditMaxMode.Edit -> {
                val gap = secondsText((bidAt.toEpochMilli() - lockAt.toEpochMilli()) / 1_000)
                val lockClock = clockFor(now, lockAt)
                val until = if (isSameDay(now, lockAt)) lockClock else "${lockAt.dayDate()} at $lockClock"
                SheetLine(SheetLineKind.Deadline, "Change it until $until, $gap before the bid.", figure = lockClock)
            }

            EditMaxMode.Raise -> {
                val day = if (isSameDay(now, bidAt)) "" else "${bidAt.dayDate()} "
                val clock = bidAt.clockWithSeconds()
                SheetLine(SheetLineKind.Plan, "Bids ${day}at $clock ${bidAt.zoneName()}", figure = clock)
            }

            EditMaxMode.Set -> {
                if (typed is TypedMax.Valid) {
                    val plan = AddPlanner.plan(typed.max, bidAt, now)
                    SheetLine(SheetLineKind.Plan, plan.text, plan.figure)
                } else {
                    SheetLine(SheetLineKind.Plan, "No max yet: Kestrel keeps watching the price.")
                }
            }
        }
    }
}
