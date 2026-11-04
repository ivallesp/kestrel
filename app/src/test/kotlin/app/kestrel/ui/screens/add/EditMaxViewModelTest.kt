package app.kestrel.ui.screens.add

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.CatalogueFixture.at
import app.kestrel.ui.fixture.CatalogueFixture.gbp
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.shell.ShellMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** The edit sheet: the three variants, saving, Undo, the lock and refusals, with fake services. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditMaxViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Subject(
        val viewModel: EditMaxViewModel,
        val services: FakeEditMaxServices,
        val done: MutableList<EditMaxDone>,
    ) {
        val state: EditMaxUiState get() = requireNotNull(viewModel.uiState.value)

        fun type(text: String) {
            viewModel.maxField.setTextAndPlaceCursorAtEnd(text)
            Snapshot.sendApplyNotifications()
        }

        fun tick(time: String) {
            services.clock.value = at(time)
        }
    }

    private fun TestScope.subject(
        snipe: app.kestrel.model.Snipe,
        now: Instant,
        mode: EditMaxMode? = null,
    ): Subject {
        val services = FakeEditMaxServices(snipe, now)
        val viewModel = EditMaxViewModel(snipe.id, services)
        val done = mutableListOf<EditMaxDone>()
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        backgroundScope.launch(dispatcher) { viewModel.done.collect { done += it } }
        viewModel.open(mode)
        Snapshot.sendApplyNotifications()
        return Subject(viewModel, services, done)
    }

    private val canonAt1812 get() = CatalogueFixture.canonFinalMinute.snipe

    @Test
    fun editStartsFromTheMaxAndSavesWithUndo() =
        runTest(dispatcher) {
            val subject = subject(CatalogueFixture.canon, at("2025-11-09T18:08:33Z"))
            assertEquals(EditMaxMode.Edit, subject.state.mode)
            assertEquals(
                "120.00",
                subject.viewModel.maxField.text
                    .toString(),
            )
            assertEquals("Change it until 18:21:31, ${secondsText(3)} before the bid.", subject.state.line.text)
            subject.type("130.00")
            subject.viewModel.save()
            assertEquals(listOf(EditCall.Max(1, gbp("130.00"))), subject.services.calls)
            val message = requireNotNull(subject.done.single().message)
            assertEquals("Max changed to £130.00", message.text)
            assertEquals("Undo", message.actionLabel)
            assertEquals(ShellMessage.UNDO_MILLIS, message.durationMillis)
            message.onAction?.invoke()
            subject.services.detached
                .single()
                .invoke()
            assertEquals(EditCall.Max(1, gbp("120.00")), subject.services.calls.last())
        }

    @Test
    fun undoIsNotOfferedWhenTheLockIsCloserThanTheSnackbarLasts() =
        runTest(dispatcher) {
            val subject = subject(canonAt1812, at("2025-11-09T18:21:25Z"))
            subject.type("130.00")
            subject.viewModel.save()
            assertNull(
                subject.done
                    .single()
                    .message
                    ?.actionLabel,
            )
        }

    @Test
    fun raiseStartsFromEbaysNextBidSelected() =
        runTest(dispatcher) {
            val subject = subject(CatalogueFixture.omega, at("2025-11-09T18:08:40Z"))
            assertEquals(EditMaxMode.Raise, subject.state.mode)
            assertEquals("Raise your max", subject.state.title)
            assertEquals(
                "2200.00",
                subject.viewModel.maxField.text
                    .toString(),
            )
            assertEquals(TextRange(0, 7), subject.viewModel.maxField.selection)
            assertEquals("£2,200.00", subject.state.max.minimum)
            assertEquals(WorstCase.Sum("£2,200.00", "£12.95", "£2,212.95"), subject.state.worstCase)
            assertEquals("Bids at 21:02:09 GMT", subject.state.line.text)
            subject.viewModel.save()
            assertEquals(
                "Max raised to £2,200.00",
                subject.done
                    .single()
                    .message
                    ?.text,
            )
        }

    @Test
    fun setArmsAWatchedLotWithItsLeadTime() =
        runTest(dispatcher) {
            val subject = subject(CatalogueFixture.polaroid, at("2025-11-09T18:09:10Z"))
            assertEquals(EditMaxMode.Set, subject.state.mode)
            assertEquals(
                "",
                subject.viewModel.maxField.text
                    .toString(),
            )
            assertEquals("£63.00", subject.state.max.minimum)
            assertEquals(OddPence.Tip("GBP"), subject.state.oddPence)
            assertFalse(subject.state.primaryEnabled)
            subject.type("70.00")
            subject.viewModel.selectLead(2)
            assertEquals("Bids £70.00 on Thu 13 Nov at 19:29:50 GMT", subject.state.line.text)
            subject.viewModel.save()
            assertEquals(listOf(EditCall.Lead(5, 10), EditCall.Max(5, gbp("70.00"))), subject.services.calls)
            assertEquals(
                "Armed: bids Thu 13 Nov at 19:29:50",
                subject.done
                    .single()
                    .message
                    ?.text,
            )
        }

    @Test
    fun theSheetLocksAtTheDeadlineAndShowsTheMaxThatStands() =
        runTest(dispatcher) {
            val subject = subject(canonAt1812, at("2025-11-09T18:21:12Z"))
            subject.type("130.00")
            assertTrue(subject.state.primaryEnabled)
            subject.tick("2025-11-09T18:21:31.500Z")
            Snapshot.sendApplyNotifications()
            val state = subject.state
            assertTrue(state.blocked)
            assertFalse(state.primaryEnabled)
            assertEquals("Close", state.dismissLabel)
            assertEquals(
                "The bid window is open, so this can’t change now.\nKestrel bids £120.00 at 18:21:34.",
                state.line.text,
            )
            assertEquals(
                "120.00",
                subject.viewModel.maxField.text
                    .toString(),
            )
            assertEquals(WorstCase.Sum("£120.00", "£6.50", "£126.50"), state.worstCase)
            subject.viewModel.save()
            assertTrue(subject.services.calls.isEmpty())
        }

    @Test
    fun aRefusedSaveSaysWhyAndKeepsTheStandingMax() =
        runTest(dispatcher) {
            val subject = subject(canonAt1812, at("2025-11-09T18:21:12Z"))
            subject.services.refusal = "The bid window is open, so this can't change now."
            subject.type("130.00")
            subject.viewModel.save()
            Snapshot.sendApplyNotifications()
            val state = subject.state
            assertEquals(SheetLineKind.Refused, state.line.kind)
            assertEquals(
                "The bid window is open, so this can’t change now.\nKestrel bids £120.00 at 18:21:34.",
                state.line.text,
            )
            assertTrue(state.blocked)
            assertEquals(
                "120.00",
                subject.viewModel.maxField.text
                    .toString(),
            )
            assertTrue(subject.done.isEmpty())
        }

    @Test
    fun aMaxFarAboveThePriceIsSpeltOutFirst() =
        runTest(dispatcher) {
            val subject = subject(CatalogueFixture.canon, at("2025-11-09T18:08:33Z"))
            subject.type("1200.00")
            subject.viewModel.save()
            assertTrue(subject.services.calls.isEmpty())
            assertEquals("One thousand, two hundred pounds", subject.state.wordsCheck?.words)
            assertEquals("Save max", subject.state.wordsCheck?.confirmLabel)
            subject.viewModel.confirmWords()
            assertEquals(listOf(EditCall.Max(1, gbp("1200.00"))), subject.services.calls)
        }

    @Test
    fun savingTheSameMaxJustCloses() =
        runTest(dispatcher) {
            val subject = subject(CatalogueFixture.canon, at("2025-11-09T18:08:33Z"))
            subject.viewModel.save()
            assertTrue(subject.services.calls.isEmpty())
            assertNull(subject.done.single().message)
        }

    @Test
    fun theLotLineNamesThePriceAndTheEnd() {
        assertEquals(
            "£96.00 now · ends 18:21:40 GMT",
            EditMaxPlanner.lotLine(canonAt1812, at("2025-11-09T18:21:12Z")),
        )
        assertEquals(
            "£2,150.00 now · ends 21:02 GMT",
            EditMaxPlanner.lotLine(CatalogueFixture.omega, at("2025-11-09T18:08:40Z")),
        )
        assertEquals(
            "£61.00 now · ends Thu 13 Nov, 19:30 GMT",
            EditMaxPlanner.lotLine(CatalogueFixture.polaroid, at("2025-11-09T18:09:10Z")),
        )
    }
}
