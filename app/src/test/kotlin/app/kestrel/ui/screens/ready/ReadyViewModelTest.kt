package app.kestrel.ui.screens.ready

import android.content.Intent
import app.kestrel.model.Snipe
import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.format.forDisplay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** S5's ViewModel: when the checks run, what the state says meanwhile, and what each row's button opens. */
class ReadyViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeSources(
        var phone: FakePhone = FakePhone(),
        snipes: List<Snipe> = CatalogueFixture.snipes,
    ) : ReadySources {
        val snipeFlow = MutableStateFlow(snipes)
        var checks = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun check(): List<ReadinessItem> {
            checks++
            gate?.await()
            return phone.items()
        }

        override suspend fun facts(checkedAt: Instant): CheckFacts = ReadyFixtures.facts(phone, checkedAt)

        override fun snipes(): Flow<List<Snipe>> = snipeFlow

        override fun fixIntent(key: ReadinessKey): Intent? = null

        override fun nowUtcMillis(): Long = ReadyFixtures.now.toEpochMilli()
    }

    @Test
    fun nothingIsShownBeforeTheFirstCheckEnds() =
        runTest(main.dispatcher) {
            val viewModel = ReadyViewModel(FakeSources())
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            val state = viewModel.state.value
            assertNull(state.checkedAt)
            assertTrue(state.rows.isEmpty())
            assertTrue("the button reads Checking… until a check ends", state.checkInProgress)
        }

    @Test
    fun aCheckFillsTheRowsWithProblemsFirstAvailable() =
        runTest(main.dispatcher) {
            val viewModel = ReadyViewModel(FakeSources())
            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.checkAgain()
            runCurrent()
            val state = viewModel.state.value
            assertEquals(ReadyFixtures.now, state.checkedAt)
            assertFalse(state.checkInProgress)
            assertEquals(listOf(ReadinessKey.BATTERY), state.problems.map { it.key })
            assertEquals("Canon AE-1 Program".forDisplay(), state.nextBid?.title)
        }

    @Test
    fun aTapWhileCheckingDoesNothingAndTheButtonSaysChecking() =
        runTest(main.dispatcher) {
            val sources = FakeSources().apply { gate = CompletableDeferred() }
            val viewModel = ReadyViewModel(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.checkAgain()
            runCurrent()
            viewModel.checkAgain()
            runCurrent()
            assertEquals(1, sources.checks)
            assertTrue(viewModel.state.value.checking)
            sources.gate?.complete(Unit)
            runCurrent()
            assertFalse(viewModel.state.value.checking)
        }

    @Test
    fun aFixedSettingMovesIntoAllGoodOnTheNextCheck() =
        runTest(main.dispatcher) {
            val sources = FakeSources()
            val viewModel = ReadyViewModel(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.checkAgain()
            runCurrent()
            sources.phone = FakePhone(batteryUnrestricted = true)
            viewModel.checkAgain()
            runCurrent()
            val battery =
                viewModel.state.value.rows
                    .single { it.key == ReadinessKey.BATTERY }
            assertEquals(CheckStanding.Passed, battery.standing)
            assertTrue(
                viewModel.state.value.problems
                    .isEmpty(),
            )
        }

    @Test
    fun theDeadlineFollowsTheSnipes() =
        runTest(main.dispatcher) {
            val sources = FakeSources()
            val viewModel = ReadyViewModel(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.checkAgain()
            runCurrent()
            sources.snipeFlow.value = ReadyFixtures.notArmedSnipes
            runCurrent()
            assertNull(viewModel.state.value.nextBid)
            val dryRun =
                viewModel.state.value.rows
                    .single { it.key == ReadinessKey.DRY_RUN }
            assertEquals(CheckStanding.NotYet, dryRun.standing)
        }
}
