package app.kestrel.ui.screens.handoff

import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.platform.SnipeAction
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.screens.ready.MainDispatcherRule
import app.kestrel.ui.screens.signinbrowser.BrowserSheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

/** The hand-off ViewModel: what it shows for a snipe, and what Done starts. */
class HandOffViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeSources(
        moment: LotMoment,
    ) : HandOffSources {
        val snipe = MutableSharedFlow<Snipe?>(replay = 1).apply { tryEmit(moment.snipe) }
        val events = MutableStateFlow(moment.events)
        val connection = MutableStateFlow<Connectivity>(Connectivity.Online)
        val started = mutableListOf<Pair<SnipeAction, Long>>()
        val now = moment.now

        override fun snipe(id: Long): Flow<Snipe?> = snipe

        override fun timeline(id: Long): Flow<List<TimelineEvent>> = events

        override fun connectivity(): Flow<Connectivity> = connection

        override fun nowUtcMillis(): Long = now.toEpochMilli()

        override fun start(
            action: SnipeAction,
            snipe: Snipe,
        ) {
            started += action to snipe.id
        }
    }

    private fun viewModelOf(sources: FakeSources) =
        HandOffViewModel(snipeId = 1, sources = sources, tickMillis = 60_000)

    @Test
    fun itShowsTheSnipesOwnStatusAndItsListing() =
        runTest(main.dispatcher) {
            val sources = FakeSources(CatalogueFixture.canonCaptcha)
            val viewModel = viewModelOf(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            val state = viewModel.state.value
            assertTrue(state.status is LotStatus.Captcha)
            assertEquals("https://www.ebay.co.uk/itm/126771134521", state.url)
            assertEquals("www.ebay.co.uk", state.page.host)
            assertFalse(state.gone)
        }

    @Test
    fun doneRunsTheDryRunAgainForASnipeThatWillBid() =
        runTest(main.dispatcher) {
            val sources = FakeSources(CatalogueFixture.canonCaptcha)
            val viewModel = viewModelOf(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            assertEquals(HandOffViewModel.DRY_RUN_AGAIN, viewModel.done())
            assertEquals(listOf(SnipeAction.DRY_RUN to CatalogueFixture.canon.id), sources.started)
        }

    @Test
    fun doneChecksTheListingOfASnipeThatOnlyWatches() =
        runTest(main.dispatcher) {
            val watching = CatalogueFixture.polaroidWatching
            val sources = FakeSources(watching.copy(snipe = watching.snipe.copy(status = SnipeStatus.NEEDS_YOU)))
            val viewModel = viewModelOf(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            assertEquals(HandOffViewModel.CHECK_AGAIN, viewModel.done())
            assertEquals(listOf(SnipeAction.CHECK to CatalogueFixture.polaroid.id), sources.started)
        }

    @Test
    fun doneStartsNothingForAFinishedSnipeOrARunningWindow() {
        assertNull(HandOffViewModel.recheckAction(CatalogueFixture.canonWon.snipe))
        assertNull(HandOffViewModel.recheckAction(CatalogueFixture.canon.copy(status = SnipeStatus.BIDDING)))
        assertNull(HandOffViewModel.recheckAction(CatalogueFixture.canon.copy(status = SnipeStatus.BID_SENT)))
        assertEquals(SnipeAction.DRY_RUN, HandOffViewModel.recheckAction(CatalogueFixture.canonCaptcha.snipe))
        assertEquals(SnipeAction.DRY_RUN, HandOffViewModel.recheckAction(CatalogueFixture.canonDryRunFailed.snipe))
    }

    @Test
    fun aDeletedSnipeIsGoneAndDoneOnlyCloses() =
        runTest(main.dispatcher) {
            val sources = FakeSources(CatalogueFixture.canonCaptcha)
            val viewModel = viewModelOf(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            sources.snipe.emit(null)
            runCurrent()
            assertTrue(viewModel.state.value.gone)
            assertNull(viewModel.done())
            assertTrue(sources.started.isEmpty())
        }

    @Test
    fun aPageThatFinishesOfflineOffersToTryAgain() =
        runTest(main.dispatcher) {
            val sources = FakeSources(CatalogueFixture.canonCaptcha)
            val viewModel = viewModelOf(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            sources.connection.value = Connectivity.Offline(sinceUtcMillis = null)
            runCurrent()
            viewModel.onPageFinished("https://www.ebay.co.uk/itm/126771134521")
            runCurrent()
            assertEquals(BrowserSheet.Offline, viewModel.state.value.page.sheet)
            viewModel.onSheetAction()
            runCurrent()
            assertNull(viewModel.state.value.page.sheet)
            assertEquals(1, viewModel.state.value.page.generation)
        }

    @Test
    fun theBarFollowsThePagesHostAndProgress() =
        runTest(main.dispatcher) {
            val sources = FakeSources(CatalogueFixture.canonCaptcha)
            val viewModel = viewModelOf(sources)
            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.onProgress(30, "https://www.ebay.co.uk/splashui/captcha?ap=1")
            runCurrent()
            assertEquals(0.3f, viewModel.state.value.page.progress)
            viewModel.onPageFinished("https://www.ebay.co.uk/splashui/captcha?ap=1")
            runCurrent()
            assertNull(viewModel.state.value.page.progress)
        }
}
