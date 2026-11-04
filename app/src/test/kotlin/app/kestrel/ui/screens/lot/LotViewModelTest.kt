package app.kestrel.ui.screens.lot

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.kestrel.data.EditResult
import app.kestrel.data.PricePoint
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.GroupContext
import app.kestrel.ui.screens.result.ResultVariant
import app.kestrel.ui.shell.ShellMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** The lot ViewModel over a fake [LotData]: what it shows, how often it ticks, and what each action does. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fixture = CatalogueFixture

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModelFor(data: FakeLotData): LotViewModel {
        val viewModel = LotViewModel(data.snipeId, data, photoOf = { fixture.photoOf(it) }, work = dispatcher)
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { data.effects += it } }
        runCurrent()
        return viewModel
    }

    @Test
    fun theLivePageFollowsTheSnipe() =
        runTest(dispatcher) {
            val data = FakeLotData(fixture.canonFinalMinute, testScheduler::currentTime)
            val viewModel = viewModelFor(data)
            val page = viewModel.state.value as LotPage.Live
            assertEquals("Final minute", page.state.chipText)
            assertEquals("36", page.state.countdown.figure)

            data.snipe.value = data.snipe.value!!.copy(status = SnipeStatus.BID_SENT)
            data.events.value = fixture.canonBidSent.events
            runCurrent()
            assertEquals("Bid sent", (viewModel.state.value as LotPage.Live).state.chipText)

            data.snipe.value = null
            runCurrent()
            assertEquals(LotPage.Gone, viewModel.state.value)
        }

    @Test
    fun theFinalMinuteTicksTenTimesASecond() =
        runTest(dispatcher) {
            val data = FakeLotData(fixture.canonFinalMinute, testScheduler::currentTime)
            val viewModel = viewModelFor(data)
            val before = data.clockReads
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(10, data.clockReads - before)
            assertEquals("35", (viewModel.state.value as LotPage.Live).state.countdown.figure)
        }

    @Test
    fun theFinalHourTicksOnceASecondAndFurtherOutOnceAMinute() =
        runTest(dispatcher) {
            val hour = FakeLotData(LotFixtures.canonArmedToday, testScheduler::currentTime)
            viewModelFor(hour)
            val atStart = hour.clockReads
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(10, hour.clockReads - atStart)

            val far = FakeLotData(fixture.gameBoyArmed, testScheduler::currentTime)
            viewModelFor(far)
            val farStart = far.clockReads
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(2, far.clockReads - farStart)
        }

    @Test
    fun savingAnEditStoresItAndOffersUndoUntilTheEditsLock() =
        runTest(dispatcher) {
            val data = FakeLotData(LotFixtures.canonArmedToday, testScheduler::currentTime)
            val viewModel = viewModelFor(data)
            viewModel.openSheet(SheetKind.Edit)
            runCurrent()
            assertEquals("120.00", viewModel.maxInput.text.toString())
            assertEquals("Edit your max", (viewModel.state.value as LotPage.Live).state.sheet?.title)

            viewModel.maxInput.setTextAndPlaceCursorAtEnd("130.00")
            viewModel.save()
            runCurrent()
            assertEquals(listOf(CatalogueFixture.gbp("130.00")), data.maxUpdates)
            assertNull("the sheet closes", (viewModel.state.value as LotPage.Live).state.sheet)
            val message = (data.effects.single() as LotEffect.Show).message
            assertEquals("Max changed to £130.00", message.text)
            assertEquals("Undo", message.actionLabel)
            assertEquals(ShellMessage.UNDO_MILLIS, message.durationMillis)

            message.onAction!!.invoke()
            data.runDetached()
            assertEquals(CatalogueFixture.gbp("120.00"), data.maxUpdates.last())
        }

    @Test
    fun aMaxFarAboveThePriceIsSpeltOutBeforeItIsSaved() =
        runTest(dispatcher) {
            // Armed at £120.00 with the price at £88.00: an extra zero typed by mistake.
            val data = FakeLotData(LotFixtures.canonArmedToday, testScheduler::currentTime)
            val viewModel = viewModelFor(data)

            fun sheet() = (viewModel.state.value as LotPage.Live).state.sheet
            viewModel.openSheet(SheetKind.Edit)
            viewModel.maxInput.setTextAndPlaceCursorAtEnd("1200.00")
            viewModel.save()
            runCurrent()
            assertTrue("nothing is stored yet", data.maxUpdates.isEmpty())
            assertEquals("£1,200.00", sheet()?.wordsCheck?.amount)
            assertEquals("Save max", sheet()?.wordsCheck?.confirmLabel)

            viewModel.dismissWords()
            runCurrent()
            assertNull(sheet()?.wordsCheck)
            assertTrue(data.maxUpdates.isEmpty())

            viewModel.save()
            runCurrent()
            viewModel.confirmWords()
            runCurrent()
            assertEquals(listOf<Money?>(CatalogueFixture.gbp("1200.00")), data.maxUpdates)
        }

    @Test
    fun aRefusedSaveStaysInTheSheetWithKestrelsWords() =
        runTest(dispatcher) {
            val data = FakeLotData(fixture.canonFinalMinute, testScheduler::currentTime)
            data.updateMaxResult = { EditResult.NotAllowed("The bid window is open, so this can't change now.") }
            val viewModel = viewModelFor(data)
            viewModel.openSheet(SheetKind.Edit)
            viewModel.maxInput.setTextAndPlaceCursorAtEnd("130.00")
            viewModel.save()
            runCurrent()
            val sheet = (viewModel.state.value as LotPage.Live).state.sheet!!
            assertEquals("The bid window is open, so this can’t change now.", sheet.error)
            assertTrue(data.effects.isEmpty())
        }

    @Test
    fun raisingPrefillsEbaysNextBid() =
        runTest(dispatcher) {
            val data = FakeLotData(fixture.omegaOverMax, testScheduler::currentTime)
            val viewModel = viewModelFor(data)
            viewModel.openSheet(SheetKind.Raise)
            assertEquals("2200.00", viewModel.maxInput.text.toString())
        }

    @Test
    fun settingAMaxAlsoStoresTheChosenLead() =
        runTest(dispatcher) {
            val data = FakeLotData(fixture.polaroidWatching, testScheduler::currentTime)
            val viewModel = viewModelFor(data)
            viewModel.openSheet(SheetKind.Set)
            viewModel.selectLead(10)
            viewModel.maxInput.setTextAndPlaceCursorAtEnd("70")
            viewModel.save()
            runCurrent()
            assertEquals(listOf(10), data.leadUpdates)
            assertEquals(listOf(CatalogueFixture.gbp("70.00")), data.maxUpdates)
            assertTrue((data.effects.single() as LotEffect.Show).message.text.startsWith("Armed: bids Thu 13 Nov at "))
        }

    @Test
    fun aWatchedListingOnAnotherSiteCannotBeArmed() =
        runTest(dispatcher) {
            val polaroid = fixture.polaroidWatching
            val german =
                polaroid.snipe.copy(
                    ref = ItemRef(EbaySite.DE, polaroid.snipe.ref.itemId),
                    lastPrice = Money.of("60.00", "EUR"),
                    postage = null,
                )
            val data = FakeLotData(polaroid.copy(snipe = german), testScheduler::currentTime)
            val viewModel = viewModelFor(data)
            viewModel.openSheet(SheetKind.Set)
            viewModel.maxInput.setTextAndPlaceCursorAtEnd("70")
            viewModel.save()
            runCurrent()
            assertTrue(data.maxUpdates.isEmpty())
            assertTrue(data.leadUpdates.isEmpty())
            assertEquals(
                "Kestrel can sign in to ebay.co.uk only, so it can’t bid on this ebay.de listing. You can still watch it without a max.",
                (viewModel.state.value as LotPage.Live).state.sheet?.error,
            )
        }

    @Test
    fun stoppingSnipingGoesBackToWatchingWithUndo() =
        runTest(dispatcher) {
            val data = FakeLotData(LotFixtures.canonArmedToday, testScheduler::currentTime)
            val viewModel = viewModelFor(data)
            viewModel.stopSniping()
            runCurrent()
            assertEquals(listOf<Money?>(null), data.maxUpdates)
            val message = (data.effects.single() as LotEffect.Show).message
            assertEquals("Stopped sniping Canon AE-1", message.text)
            assertEquals(ShellMessage.UNDO_MILLIS, message.durationMillis)
            message.onAction!!.invoke()
            data.runDetached()
            assertEquals(CatalogueFixture.gbp("120.00"), data.maxUpdates.last())
        }

    @Test
    fun inTheFinalMinuteStoppingCancelsWithoutAnUndo() =
        runTest(dispatcher) {
            val data = FakeLotData(fixture.canonFinalMinute, testScheduler::currentTime)
            data.updateMaxResult = { EditResult.NotAllowed("The bid window is open, so this can't change now.") }
            val viewModel = viewModelFor(data)
            viewModel.stopSniping()
            runCurrent()
            assertEquals(1, data.cancels)
            val message = (data.effects.single() as LotEffect.Show).message
            assertEquals("Stopped sniping Canon AE-1", message.text)
            // Less than a minute is left, so the snipe could not come back (SnipeRules.uncancelRefusal).
            assertNull(message.actionLabel)
        }

    @Test
    fun insideTheWindowStoppingCancelsWithAnUndoWhileItCanComeBack() =
        runTest(dispatcher) {
            val data =
                FakeLotData(
                    fixture.canonFinalMinute.copy(
                        now = Instant.ofEpochMilli(fixture.canonFinalMinute.snipe.endTimeUtcMillis - 110_000),
                    ),
                    testScheduler::currentTime,
                )
            data.updateMaxResult = { EditResult.NotAllowed("The bid window is open, so this can't change now.") }
            val viewModel = viewModelFor(data)
            viewModel.stopSniping()
            runCurrent()
            assertEquals(1, data.cancels)
            val message = (data.effects.single() as LotEffect.Show).message
            assertEquals("Stopped sniping Canon AE-1", message.text)
            assertEquals("Undo", message.actionLabel)
            message.onAction!!.invoke()
            data.runDetached()
            assertEquals(1, data.undoCancels)
        }

    @Test
    fun markingAnUnreadResultStoresWhatTheUserSawOnEbay() =
        runTest(dispatcher) {
            val data = FakeLotData(LotFixtures.canonResultUnknown, testScheduler::currentTime)
            val viewModel = viewModelFor(data)
            viewModel.settle(won = true)
            runCurrent()
            assertEquals(listOf(true), data.settles)

            data.settleResult =
                { EditResult.NotAllowed("The auction has not ended yet, or Kestrel is still reading the result.") }
            viewModel.settle(won = false)
            runCurrent()
            assertEquals(
                "The auction has not ended yet, or Kestrel is still reading the result.",
                (data.effects.last() as LotEffect.Show).message.text,
            )
        }

    @Test
    fun deletingLeavesAnUndoForTheNextScreen() =
        runTest(dispatcher) {
            val data = FakeLotData(LotFixtures.canonArmedToday, testScheduler::currentTime)
            val viewModel = viewModelFor(data)
            viewModel.delete()
            runCurrent()
            val message = (data.effects.single() as LotEffect.LeaveAndClose).message
            assertEquals("Deleted Canon AE-1", message.text)
            // The page does not turn into "not on your list" while the screen closes.
            data.snipe.value = null
            runCurrent()
            assertTrue(viewModel.state.value is LotPage.Live)
            message.onAction!!.invoke()
            data.runDetached()
            assertEquals(1, data.undoDeletes)
        }

    @Test
    fun theSealStampsOnTheFirstOpenOnly() =
        runTest(dispatcher) {
            val data = FakeLotData(fixture.canonWon, testScheduler::currentTime)
            val first = viewModelFor(data)
            runCurrent()
            val page = first.state.value as LotPage.Finished
            assertEquals(ResultVariant.Won, page.state.variant)
            assertTrue(page.state.stampSeal)

            val second = viewModelFor(data)
            runCurrent()
            assertEquals(false, (second.state.value as LotPage.Finished).state.stampSeal)
        }
}

/** A [LotData] over one fixture moment, with time from the test scheduler and every change recorded. */
private class FakeLotData(
    moment: LotMoment,
    private val elapsedMillis: () -> Long,
) : LotData {
    val snipeId = moment.snipe.id
    val snipe = MutableStateFlow<Snipe?>(moment.snipe)
    val events = MutableStateFlow(moment.events)
    private val prices = MutableStateFlow(moment.pricePoints)
    private val startMillis = moment.now.toEpochMilli()
    val effects = mutableListOf<LotEffect>()
    val maxUpdates = mutableListOf<Money?>()
    val leadUpdates = mutableListOf<Int>()
    var cancels = 0
    var undoDeletes = 0
    var clockReads = 0
    var updateMaxResult: (Money?) -> EditResult = { max -> EditResult.Done(snipe.value!!.copy(maxBid = max)) }
    private val stamped = mutableSetOf<Long>()
    private val detached = mutableListOf<suspend () -> Unit>()

    override fun snipe(id: Long): Flow<Snipe?> = snipe

    override fun timeline(id: Long): Flow<List<TimelineEvent>> = events

    override fun prices(id: Long): Flow<List<PricePoint>> = prices

    override fun group(
        groupId: Long?,
        snipeId: Long,
    ): Flow<GroupContext?> = flowOf(null)

    override val accountName: Flow<String?> = MutableStateFlow(CatalogueFixture.ACCOUNT)

    override val connectivity: Flow<Connectivity> = MutableStateFlow(Connectivity.Online)

    override fun nowUtcMillis(): Long {
        clockReads++
        return startMillis + elapsedMillis()
    }

    override suspend fun updateMax(
        id: Long,
        max: Money?,
    ): EditResult = updateMaxResult(max).also { maxUpdates += max }

    override suspend fun setLead(
        id: Long,
        leadSeconds: Int,
    ): EditResult {
        leadUpdates += leadSeconds
        return EditResult.Done(snipe.value!!.copy(leadSeconds = leadSeconds))
    }

    override suspend fun cancel(id: Long): EditResult {
        cancels++
        return EditResult.Done(snipe.value!!.copy(status = SnipeStatus.CANCELLED))
    }

    override suspend fun delete(id: Long): EditResult = EditResult.Done(snipe.value!!)

    override suspend fun undoDelete(id: Long): EditResult {
        undoDeletes++
        return snipe.value?.let { EditResult.Done(it) } ?: EditResult.NotFound
    }

    var undoCancels = 0
    val settles = mutableListOf<Boolean>()
    var settleResult: (Boolean) -> EditResult = { won ->
        EditResult.Done(snipe.value!!.copy(status = if (won) SnipeStatus.WON else SnipeStatus.LOST))
    }

    override suspend fun settle(
        id: Long,
        won: Boolean,
    ): EditResult {
        settles += won
        return settleResult(won)
    }

    override suspend fun undoCancel(id: Long): EditResult {
        undoCancels++
        return EditResult.Done(snipe.value!!.copy(id = id + 100, status = SnipeStatus.ARMED, groupId = null))
    }

    override suspend fun sealStamped(id: Long): Boolean = id in stamped

    override suspend fun markSealStamped(id: Long) {
        stamped += id
    }

    override fun runDetached(action: suspend () -> Unit) {
        detached += action
    }

    /** Runs the Undo actions the screen handed to the app's scope. */
    suspend fun runDetached() {
        val actions = detached.toList()
        detached.clear()
        actions.forEach { it() }
    }
}
