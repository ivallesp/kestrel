package app.kestrel.ui.screens.lot

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.AppContainer
import app.kestrel.data.EditResult
import app.kestrel.data.PricePoint
import app.kestrel.ebay.MoneyText
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.format.clockWithSeconds
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.withCurlyQuotes
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.GroupContext
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotTimes
import app.kestrel.ui.model.UndoCancel
import app.kestrel.ui.screens.add.MaxRules
import app.kestrel.ui.screens.result.ResultVariant
import app.kestrel.ui.shell.ShellMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** One-off things the lot screens ask their destination to do. */
sealed interface LotEffect {
    /** Shows a snackbar on this screen. */
    data class Show(
        val message: ShellMessage,
    ) : LotEffect

    /** Leaves a snackbar for the next screen and goes back, after the snipe was deleted. */
    data class LeaveAndClose(
        val message: ShellMessage,
    ) : LotEffect

    /** Opens [snipeId] in place of this screen with [message]: the snipe a group cancelled, armed again. */
    data class Reopen(
        val snipeId: Long,
        val message: ShellMessage,
    ) : LotEffect
}

/**
 * When the lot screens tick: every 100 ms in the final minute; once a second, on the second, in the final hour and
 * after the end while the result is read; once a minute, on the minute, otherwise. A tick never sleeps past the start
 * of a faster band.
 */
object LotTicks {
    const val FINAL_MINUTE_TICK_MILLIS = 100L
    private const val SECOND_MILLIS = 1_000L
    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS

    fun delayMillis(
        nowMillis: Long,
        endMillis: Long?,
        finished: Boolean,
    ): Long {
        val untilNextMinute = MINUTE_MILLIS - Math.floorMod(nowMillis, MINUTE_MILLIS)
        if (endMillis == null || finished) return untilNextMinute
        val left = endMillis - nowMillis
        val untilNextSecond = SECOND_MILLIS - Math.floorMod(nowMillis, SECOND_MILLIS)
        return when {
            left <= 0 -> untilNextSecond
            left <= MINUTE_MILLIS -> FINAL_MINUTE_TICK_MILLIS
            left <= HOUR_MILLIS -> minOf(untilNextSecond, left - MINUTE_MILLIS)
            else -> minOf(untilNextMinute, left - HOUR_MILLIS)
        }.coerceAtLeast(1L)
    }
}

/**
 * The lot detail (S3) and its result (S4): reads the snipe, its timeline and its prices, ticks the clock (see
 * [LotTicks]) and turns them into a [LotPage]. Every change goes through [LotData]; Undo actions run in the app's
 * scope so they still work after the screen closes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LotViewModel(
    private val snipeId: Long,
    private val data: LotData,
    private val photoOf: (Snipe) -> LotPhoto = LotPhoto::of,
    work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    constructor(snipeId: Long, container: AppContainer) : this(snipeId, ContainerLotData(container))

    /** The edit sheet's amount, kept here so recomposition and rotation never rebuild it (DESIGN 3.18). */
    val maxInput = TextFieldState()

    private val sheet = MutableStateFlow<SheetRequest?>(null)
    private val closing = MutableStateFlow(false)

    /** Whether the WON seal stamps: null until Kestrel knows if this result was opened before. */
    private val sealDecision = MutableStateFlow<Boolean?>(null)
    private var sealCheckStarted = false

    @Volatile
    private var latest: Snipe? = null

    private val effectChannel = Channel<LotEffect>(Channel.BUFFERED)
    val effects: Flow<LotEffect> = effectChannel.receiveAsFlow()

    private val snipe: Flow<Snipe?> =
        data
            .snipe(snipeId)
            .onEach { latest = it }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), replay = 1)

    private val group: Flow<GroupContext?> =
        snipe.map { it?.groupId }.distinctUntilChanged().flatMapLatest { data.group(it, snipeId) }

    private val clock: Flow<Instant> =
        snipe
            .map { it?.endTimeUtcMillis to (it?.status?.isFinal ?: true) }
            .distinctUntilChanged()
            .flatMapLatest { (end, finished) -> ticker(end, finished) }

    private val records: Flow<Records> =
        combine(snipe, data.timeline(snipeId), data.prices(snipeId), group) { snipe, events, prices, group ->
            Records(snipe, events, prices, group)
        }

    private val surroundings: Flow<Surroundings> =
        combine(data.accountName, data.connectivity, sheet, sealDecision) { account, connectivity, sheet, seal ->
            Surroundings(account, connectivity, sheet, seal)
        }

    val state: StateFlow<LotPage> =
        combine(records, surroundings, clock) { records, surroundings, now -> page(records, surroundings, now) }
            .flowOn(work)
            .onEach { decideSeal(it) }
            .filter { page ->
                !(page.isWon() && sealDecision.value == null) && !(page == LotPage.Gone && closing.value)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), LotPage.Loading)

    private fun page(
        records: Records,
        surroundings: Surroundings,
        now: Instant,
    ): LotPage {
        val snipe = records.snipe ?: return LotPage.Gone
        return LotPageBuilder.page(
            LotInputs(
                snipe = snipe,
                events = records.events,
                prices = records.prices,
                now = now,
                connectivity = surroundings.connectivity,
                group = records.group,
                accountName = surroundings.accountName,
                photo = photoOf(snipe),
                sheet = surroundings.sheet,
                stampSeal = surroundings.seal == true,
            ),
        )
    }

    private fun ticker(
        endMillis: Long?,
        finished: Boolean,
    ): Flow<Instant> =
        flow {
            while (true) {
                val now = data.nowUtcMillis()
                emit(Instant.ofEpochMilli(now))
                delay(LotTicks.delayMillis(now, endMillis, finished))
            }
        }

    private fun LotPage.isWon(): Boolean = this is LotPage.Finished && state.variant == ResultVariant.Won

    /** The seal stamps on the first open of a won result only, so the decision is made once and remembered. */
    private fun decideSeal(page: LotPage) {
        if (sealCheckStarted || !page.isWon()) return
        sealCheckStarted = true
        viewModelScope.launch {
            val stamped = data.sealStamped(snipeId)
            if (!stamped) data.markSealStamped(snipeId)
            sealDecision.value = !stamped
        }
    }

    // The edit sheet.

    fun openSheet(kind: SheetKind) {
        val snipe = latest ?: return
        when (kind) {
            SheetKind.Edit -> {
                maxInput.setTextAndPlaceCursorAtEnd(snipe.maxBid?.let(::plainAmount).orEmpty())
            }

            SheetKind.Raise -> {
                maxInput.setTextAndSelectAll(
                    (minimumNextBid(snipe) ?: snipe.maxBid)?.let(::plainAmount).orEmpty(),
                )
            }

            SheetKind.Set -> {
                maxInput.clearText()
            }
        }
        sheet.value = SheetRequest(kind, leadSeconds = if (kind == SheetKind.Set) snipe.leadSeconds else null)
        if (kind == SheetKind.Set) {
            viewModelScope.launch {
                val suggest = data.suggestOddPence()
                sheet.update { it?.copy(suggestOddPence = suggest) }
            }
        }
    }

    fun closeSheet() {
        sheet.value = null
    }

    fun selectLead(seconds: Int) {
        sheet.update { it?.copy(leadSeconds = seconds, error = null) }
    }

    /** "Use £90.37" (and any suggested amount): writes into the field and puts the cursor at the end. */
    fun useAmount(amount: Money) {
        maxInput.setTextAndPlaceCursorAtEnd(plainAmount(amount))
    }

    fun save() {
        val request = sheet.value ?: return
        if (request.saving) return
        val snipe = latest ?: return
        val currency = snipe.maxBid?.currency ?: snipe.lastPrice?.currency ?: snipe.ref.site.currency
        val max = MoneyText.parseUserInput(maxInput.text.toString(), currency) ?: return
        val previous = snipe.maxBid
        // Arming a watched listing on another site would only lead to a sign-in Kestrel cannot do there.
        val siteRefusal = if (previous == null) MaxRules.siteRefusal(snipe.ref.site) else null
        if (siteRefusal != null) {
            sheet.value = request.copy(error = siteRefusal)
            return
        }
        // An extra digit is the easy slip: a max far above the price is spelt out first, as S2 and Home's sheet do.
        if (!request.wordsConfirmed && MaxRules.needsWordsCheck(max, snipe.lastPrice)) {
            sheet.value = request.copy(checkingWords = max)
            return
        }
        sheet.value = request.copy(saving = true, error = null, wordsConfirmed = false)
        viewModelScope.launch {
            val lead = request.leadSeconds
            if (request.kind == SheetKind.Set && lead != null && lead != snipe.leadSeconds) {
                val leadResult = data.setLead(snipeId, lead)
                if (leadResult is EditResult.NotAllowed) {
                    sheet.value =
                        request.copy(
                            saving = false,
                            error = leadResult.reason.withCurlyQuotes(),
                            wordsConfirmed = false,
                        )
                    return@launch
                }
            }
            when (val result = data.updateMax(snipeId, max)) {
                is EditResult.Done -> {
                    sheet.value = null
                    if (previous !=
                        max
                    ) {
                        savedMessage(
                            request.kind,
                            result.snipe,
                            previous,
                        )?.let { effectChannel.send(LotEffect.Show(it)) }
                    }
                }

                is EditResult.NotAllowed -> {
                    sheet.value =
                        request.copy(saving = false, error = result.reason.withCurlyQuotes(), wordsConfirmed = false)
                }

                EditResult.NotFound -> {
                    sheet.value = null
                }
            }
        }
    }

    /** The max spelt out is what the user meant: save it. */
    fun confirmWords() {
        sheet.update { it?.copy(checkingWords = null, wordsConfirmed = true) }
        save()
    }

    /** "Change max": back to the field. */
    fun dismissWords() {
        sheet.update { it?.copy(checkingWords = null) }
    }

    /** "Max changed to £130.00" with Undo until the edits lock; "Armed: bids Thu 13 Nov at 19:29:54" after a set. */
    private fun savedMessage(
        kind: SheetKind,
        saved: Snipe,
        previous: Money?,
    ): ShellMessage? {
        val max = saved.maxBid ?: return null
        val now = Instant.ofEpochMilli(data.nowUtcMillis())
        val undoFor = LotTimes.editsLockAt(saved).toEpochMilli() - now.toEpochMilli() - UNDO_MARGIN_MILLIS
        val undo: () -> Unit = { data.runDetached { data.updateMax(snipeId, previous) } }
        val text =
            when (kind) {
                SheetKind.Edit -> {
                    "Max changed to ${max.formatted()}"
                }

                SheetKind.Raise -> {
                    "Max raised to ${max.formatted()}"
                }

                SheetKind.Set -> {
                    val bidAt = LotTimes.bidAt(saved)
                    val day = if (isSameDay(now, bidAt, AuctionZone)) "today" else bidAt.dayDate()
                    "Armed: bids $day at ${bidAt.clockWithSeconds()}"
                }
            }
        return if (undoFor >= MINIMUM_UNDO_MILLIS) {
            ShellMessage(text, UNDO, minOf(ShellMessage.UNDO_MILLIS, undoFor), undo)
        } else {
            ShellMessage(text)
        }
    }

    // The overflow menu.

    /**
     * "Stop sniping": the lot goes back to watching, with Undo. Inside the bid window the data layer refuses a new max,
     * so the snipe is cancelled instead; a cancel cannot be undone yet.
     */
    fun stopSniping() {
        val snipe = latest ?: return
        val previous = snipe.maxBid ?: return
        val text = "Stopped sniping ${shortTitle(snipe.title)}"
        viewModelScope.launch {
            when (val result = data.updateMax(snipeId, null)) {
                is EditResult.Done -> {
                    val undo: () -> Unit = { data.runDetached { data.updateMax(snipeId, previous) } }
                    effectChannel.send(LotEffect.Show(ShellMessage(text, UNDO, ShellMessage.UNDO_MILLIS, undo)))
                }

                is EditResult.NotAllowed -> {
                    when (val cancelled = data.cancel(snipeId)) {
                        is EditResult.Done -> {
                            effectChannel.send(LotEffect.Show(ShellMessage(text)))
                        }

                        is EditResult.NotAllowed -> {
                            effectChannel.send(
                                LotEffect.Show(ShellMessage(cancelled.reason.withCurlyQuotes())),
                            )
                        }

                        EditResult.NotFound -> {
                            Unit
                        }
                    }
                }

                EditResult.NotFound -> {
                    return@launch
                }
            }
        }
    }

    /**
     * "Undo cancel" (DESIGN S4 "Cancelled by group"): arms the listing again on its own and opens the snipe that bids
     * now, with "Sniping Sony Walkman again, on its own".
     */
    fun undoCancel() {
        val snipe = latest ?: return
        viewModelScope.launch {
            when (val result = data.undoCancel(snipeId)) {
                is EditResult.Done -> {
                    closing.value = true
                    val message = ShellMessage(UndoCancel.message(shortTitle(snipe.title)))
                    effectChannel.send(LotEffect.Reopen(result.snipe.id, message))
                }

                is EditResult.NotAllowed -> {
                    effectChannel.send(LotEffect.Show(ShellMessage(result.reason.withCurlyQuotes())))
                }

                EditResult.NotFound -> {
                    return@launch
                }
            }
        }
    }

    /** Deletes the snipe and goes back, leaving "Deleted Canon AE-1" with Undo for the next screen. */
    fun delete() {
        val snipe = latest ?: return
        viewModelScope.launch {
            when (val result = data.delete(snipeId)) {
                is EditResult.Done -> {
                    closing.value = true
                    val undo: () -> Unit = { data.runDetached { data.undoDelete(snipeId) } }
                    val message =
                        ShellMessage("Deleted ${shortTitle(snipe.title)}", UNDO, ShellMessage.UNDO_MILLIS, undo)
                    effectChannel.send(LotEffect.LeaveAndClose(message))
                }

                is EditResult.NotAllowed -> {
                    effectChannel.send(LotEffect.Show(ShellMessage(result.reason.withCurlyQuotes())))
                }

                EditResult.NotFound -> {
                    return@launch
                }
            }
        }
    }

    private class Records(
        val snipe: Snipe?,
        val events: List<TimelineEvent>,
        val prices: List<PricePoint>,
        val group: GroupContext?,
    )

    private class Surroundings(
        val accountName: String?,
        val connectivity: Connectivity,
        val sheet: SheetRequest?,
        val seal: Boolean?,
    )

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
        const val UNDO = "Undo"

        /** Undo of a max edit must land before the edits lock, with room for the write. */
        const val UNDO_MARGIN_MILLIS = 1_000L
        const val MINIMUM_UNDO_MILLIS = 2_000L

        /** "120.00": what the max field holds, without symbol or separators. */
        fun plainAmount(money: Money): String = money.major.toPlainString()
    }
}
