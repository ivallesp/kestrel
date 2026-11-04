package app.kestrel.ui.screens.home

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.EditResult
import app.kestrel.data.Settings
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.TimelineEvent
import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.shell.ShellMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The S1 ViewModel: its calm clock, pull to refresh, the undoable changes and the readiness checks. Robolectric
 * supplies Android's log for the failure paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    private val fixture = CatalogueFixture
    private val dispatcher = StandardTestDispatcher()
    private val sources = FakeHomeSources { fixture.now.toEpochMilli() + dispatcher.scheduler.currentTime }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.watching(viewModel: HomeViewModel): Pair<Job, MutableList<HomeUiState>> {
        val seen = mutableListOf<HomeUiState>()
        val job = backgroundScope.launch { viewModel.state.collect { seen += it } }
        runCurrent()
        return job to seen
    }

    @Test
    fun theStateFollowsTheList() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(sources, dispatcher)
            watching(viewModel)
            val state = viewModel.state.value
            assertTrue(state.loaded)
            assertEquals(
                listOf(HomeBucket.EndingSoon, HomeBucket.ThisWeek, HomeBucket.Done),
                state.sections.map { it.bucket },
            )
            assertEquals(fixture.ACCOUNT, state.accountName)
            sources.snipes.value = sources.snipes.value.filter { it.id != fixture.omega.id }
            runCurrent()
            assertEquals(
                1,
                viewModel.state.value.sections
                    .first()
                    .lotCount,
            )
        }

    @Test
    fun theClockTicksEverySecondInsideTheLastHourAndEveryMinuteOtherwise() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(sources, dispatcher)
            val (_, seen) = watching(viewModel)
            val before = seen.size
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals("one state a second while the Canon is inside its hour", 5, seen.size - before)
            sources.snipes.value = sources.snipes.value.filter { it.id != fixture.canon.id }
            runCurrent()
            val calm = seen.size
            advanceTimeBy(3 * 60_000)
            runCurrent()
            assertEquals("one state a minute otherwise", 3, seen.size - calm)
            val minutes = seen.drop(calm).map { it.now.toEpochMilli() % 60_000 }
            assertTrue("aligned to the minute: $minutes", minutes.all { it == 0L })
        }

    @Test
    fun nothingTicksOnceTheScreenStopsWatching() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(sources, dispatcher)
            val (job, _) = watching(viewModel)
            assertTrue(sources.snipes.subscriptionCount.value > 0)
            job.cancel()
            // The state stops five seconds after its last collector, then the shared list five seconds later.
            advanceTimeBy(11_000)
            runCurrent()
            assertEquals(0, sources.snipes.subscriptionCount.value)
        }

    @Test
    fun pullToRefreshAsksOnlyForStaleLotsAndNeverTwiceInFiveMinutes() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(sources, dispatcher)
            watching(viewModel)
            viewModel.refresh()
            runCurrent()
            assertEquals(
                listOf(
                    fixture.canon,
                    fixture.omega,
                    fixture.miniDisc,
                    fixture.walkman,
                    fixture.polaroid,
                    fixture.gameBoy,
                ).map { it.id },
                sources.checksAsked,
            )
            assertTrue(viewModel.state.value.refreshing)
            // The checks land: each lot records its attempt.
            val checkedAt = sources.nowUtcMillis()
            sources.snipes.value = sources.snipes.value.map { it.copy(lastCheckedAtUtcMillis = checkedAt) }
            runCurrent()
            assertFalse(viewModel.state.value.refreshing)
            advanceTimeBy(60_000)
            viewModel.refresh()
            runCurrent()
            assertEquals(6, sources.checksAsked.size)
            assertEquals("Every lot was checked in the last 5 minutes.", viewModel.nextMessage()?.text)
        }

    @Test
    fun pullToRefreshStopsWaitingAfterTwentySeconds() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(sources, dispatcher)
            watching(viewModel)
            viewModel.refresh()
            runCurrent()
            assertTrue(viewModel.state.value.refreshing)
            advanceTimeBy(20_001)
            runCurrent()
            assertFalse(viewModel.state.value.refreshing)
        }

    @Test
    fun offlinePullsAskForNothing() =
        runTest(dispatcher) {
            sources.connectivity.value = Connectivity.Offline(fixture.millis("2025-11-09T18:02:00Z"))
            val viewModel = HomeViewModel(sources, dispatcher)
            watching(viewModel)
            viewModel.refresh()
            runCurrent()
            assertTrue(sources.checksAsked.isEmpty())
            assertEquals(
                "The phone is offline. Kestrel checks again when it reconnects.",
                viewModel.nextMessage()?.text,
            )
        }

    @Test
    fun raisingToTheNextBidLeavesAnUndoThatPutsTheOldMaxBack() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(sources, dispatcher)
            watching(viewModel)
            viewModel.quickRaise(fixture.omega.id)
            runCurrent()
            assertEquals(listOf(fixture.omega.id to fixture.gbp("2200.00")), sources.maxChanges)
            val message = viewModel.nextMessage()
            assertEquals("Max raised to £2,200.00", message?.text)
            assertEquals("Undo", message?.actionLabel)
            assertEquals(ShellMessage.UNDO_MILLIS, message?.durationMillis)
            message?.onAction?.invoke()
            runCurrent()
            assertEquals(fixture.omega.id to fixture.gbp("1900.00"), sources.maxChanges.last())
        }

    @Test
    fun stoppingSnipingWatchesTheLotWithAnUndo() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(sources, dispatcher)
            watching(viewModel)
            viewModel.stopSniping(fixture.omega.id)
            runCurrent()
            assertEquals(listOf<Pair<Long, Money?>>(fixture.omega.id to null), sources.maxChanges)
            val message = viewModel.nextMessage()
            assertEquals("Stopped sniping Omega Speedmaster", message?.text)
            message?.onAction?.invoke()
            runCurrent()
            assertEquals(fixture.omega.id to fixture.gbp("1900.00"), sources.maxChanges.last())
        }

    @Test
    fun aRefusedChangeSaysWhyInCurlyQuotes() =
        runTest(dispatcher) {
            sources.edit = { _, _ -> EditResult.NotAllowed("The bid window is open, so this can't change now.") }
            val viewModel = HomeViewModel(sources, dispatcher)
            watching(viewModel)
            viewModel.quickRaise(fixture.omega.id)
            runCurrent()
            assertEquals("The bid window is open, so this can’t change now.", viewModel.nextMessage()?.text)
        }

    @Test
    fun thePhoneIsCheckedWhenTheScreenIsShownAndAgainAfterFifteenMinutes() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(sources, dispatcher)
            watching(viewModel)
            assertEquals(PillKind.Checking, viewModel.state.value.pill.kind)
            viewModel.onShown()
            runCurrent()
            assertEquals(1, sources.readinessChecks)
            val pill = viewModel.state.value.pill
            assertEquals(PillKind.OneProblem, pill.kind)
            assertEquals("Battery restricted", pill.title)
            viewModel.onShown()
            runCurrent()
            assertEquals(2, sources.readinessChecks)
            sources.snipes.value = sources.snipes.value.filter { it.id != fixture.canon.id }
            // Minute ticks: the first one at least fifteen minutes after the check runs it again.
            advanceTimeBy(16 * 60_000)
            runCurrent()
            assertEquals(3, sources.readinessChecks)
        }

    @Test
    fun aFailedReadinessCheckKeepsTheLastAnswer() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(sources, dispatcher)
            watching(viewModel)
            viewModel.onShown()
            runCurrent()
            sources.readinessFails = true
            viewModel.onShown()
            runCurrent()
            assertEquals("Battery restricted", viewModel.state.value.pill.title)
            assertEquals("A bid could go out late", viewModel.state.value.pill.detail)
        }

    private suspend fun HomeViewModel.nextMessage(): ShellMessage? = withTimeoutOrNull(1_000) { messages.first() }
}

/** Sources over the design fixture at 18:08:33, with a clock the test drives. */
class FakeHomeSources(
    private val clock: () -> Long,
) : HomeSources {
    private val fixture = CatalogueFixture

    override val snipes = MutableStateFlow(fixture.snipes)

    private val events: Map<Long, List<TimelineEvent>> =
        HomeFixtures.listMoments.associate { it.snipe.id to it.events }

    override fun timeline(snipeId: Long): Flow<List<TimelineEvent>> = MutableStateFlow(events[snipeId].orEmpty())

    override val groups = MutableStateFlow<List<SnipeGroup>>(listOf(fixture.portableAudio))

    override val settings = MutableStateFlow(Settings(signedInName = fixture.ACCOUNT))

    override val connectivity = MutableStateFlow<Connectivity>(Connectivity.Online)

    var readinessItems: List<ReadinessItem> = HomeFixtures.batteryRestricted
    var readinessChecks = 0
    var readinessFails = false

    override suspend fun readiness(): List<ReadinessItem> {
        readinessChecks++
        if (readinessFails) error("No phone to check")
        return readinessItems
    }

    override fun fixIntent(key: ReadinessKey): Intent? = null

    override fun nowUtcMillis(): Long = clock()

    val maxChanges = mutableListOf<Pair<Long, Money?>>()

    var edit: (Long, Money?) -> EditResult = { id, max ->
        val snipe = snipes.value.single { it.id == id }
        EditResult.Done(snipe.copy(maxBid = max))
    }

    override suspend fun updateMax(
        snipeId: Long,
        max: Money?,
    ): EditResult {
        maxChanges += snipeId to max
        return edit(snipeId, max)
    }

    val checksAsked = mutableListOf<Long>()

    override fun requestCheck(snipe: Snipe) {
        checksAsked += snipe.id
    }
}
