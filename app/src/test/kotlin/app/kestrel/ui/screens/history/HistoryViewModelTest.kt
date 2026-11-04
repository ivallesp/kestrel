package app.kestrel.ui.screens.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.SnipeStats
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.fixture.CatalogueFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** History's ViewModel: the finished list with its totals, a minute clock, and nothing running unwatched. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HistoryViewModelTest {
    private val fixture = CatalogueFixture
    private val dispatcher = StandardTestDispatcher()
    private val sources = FakeHistorySources { fixture.now.toEpochMilli() + dispatcher.scheduler.currentTime }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theStateShowsTheFinishedSnipesAndTheTotals() =
        runTest(dispatcher) {
            val viewModel = HistoryViewModel(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            val state = viewModel.state.value
            assertTrue(state.loaded)
            assertEquals("Won 1 of 2 in November", state.summary?.title)
            assertEquals(listOf("November"), state.months.map { it.title })
        }

    @Test
    fun theClockTicksOnceAMinuteAndStopsUnwatched() =
        runTest(dispatcher) {
            val viewModel = HistoryViewModel(sources)
            val seen = mutableListOf<HistoryUiState>()
            val job = backgroundScope.launch { viewModel.state.collect { seen += it } }
            runCurrent()
            val before = seen.size
            advanceTimeBy(2 * 60_000)
            runCurrent()
            assertEquals(2, seen.size - before)
            job.cancel()
            advanceTimeBy(11_000)
            runCurrent()
            assertEquals(0, sources.history.subscriptionCount.value)
        }
}

/** The fixture's two finished snipes, with a clock the test drives. */
class FakeHistorySources(
    private val clock: () -> Long,
) : HistorySources {
    private val fixture = CatalogueFixture

    override val history = MutableStateFlow<List<Snipe>>(listOf(fixture.gameAndWatch, fixture.nintendo64))

    override val stats: Flow<SnipeStats> get() = history.map { SnipeStats.of(it) }

    private val events = HistoryFixtures.designMoments.associate { it.snipe.id to it.events }

    override fun timeline(snipeId: Long): Flow<List<TimelineEvent>> = MutableStateFlow(events[snipeId].orEmpty())

    override val groups = MutableStateFlow<List<SnipeGroup>>(listOf(fixture.portableAudio))

    override fun nowUtcMillis(): Long = clock()
}
