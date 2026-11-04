package app.kestrel.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.testSnipe
import app.kestrel.engine.BidPage
import app.kestrel.engine.BidPageFactory
import app.kestrel.engine.BidReply
import app.kestrel.engine.LayerState
import app.kestrel.engine.PageState
import app.kestrel.engine.SubmitDispatch
import app.kestrel.engine.TypedAmount
import app.kestrel.model.AutoRefreshState
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.Money
import app.kestrel.model.SnipeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineJobRunnerTest {
    private fun runner(store: FakeStore) =
        EngineJobRunner(
            store = store,
            engine = { error("the engine must not run") },
            dryRun = { error("the dry run must not run") },
            listingCheck = { error("the check must not run") },
            pages = { error("no page may be created") },
        )

    @Test
    fun aDeletedSnipeLoadsNoPage() =
        runTest {
            for (kind in JobKind.entries) runner(FakeStore()).run(kind, snipeId = 1)
        }

    @Test
    fun aFinishedSnipeLoadsNoPage() =
        runTest {
            val store = FakeStore(testSnipe(id = 1, status = SnipeStatus.WON))

            for (kind in JobKind.entries) runner(store).run(kind, snipeId = 1)
        }

    @Test
    fun thePageIsClosedAfterUse() =
        runTest {
            val factory = RecordingPageFactory()

            withPage(
                factory,
                StandardTestDispatcher(testScheduler),
            ) { page -> assertEquals(factory.pages.single(), page) }

            assertTrue(factory.pages.single().closed)
        }

    @Test
    fun thePageIsClosedWhenTheJobFails() =
        runTest {
            val factory = RecordingPageFactory()

            val outcome =
                runCatching {
                    withPage(
                        factory,
                        StandardTestDispatcher(testScheduler),
                    ) { throw IllegalStateException("renderer gone") }
                }

            assertTrue(outcome.exceptionOrNull() is IllegalStateException)
            assertTrue(factory.pages.single().closed)
        }

    @Test
    fun thePageIsClosedWhenTheJobIsCancelled() =
        runTest {
            val factory = RecordingPageFactory()
            val job = launch { withPage(factory, StandardTestDispatcher(testScheduler)) { awaitCancellation() } }
            testScheduler.runCurrent()

            job.cancel(CancellationException("user cancelled"))
            testScheduler.advanceUntilIdle()

            assertTrue(factory.pages.single().closed)
        }

    class RecordingPageFactory : BidPageFactory {
        val pages = mutableListOf<RecordingPage>()

        override suspend fun create(): BidPage = RecordingPage().also { pages += it }
    }

    /** Only [close] is used here; any other call means a job touched the page it should not have. */
    class RecordingPage : BidPage {
        var closed = false

        override fun close() {
            closed = true
        }

        override suspend fun autoRefresh(): AutoRefreshState? = unexpected()

        override suspend fun armReplyObserver() = unexpected()

        override suspend fun submit(): SubmitDispatch = unexpected()

        override suspend fun awaitReply(timeoutMillis: Long): BidReply = unexpected()

        override suspend fun report(): String = unexpected()

        override suspend fun openItem(ref: ItemRef): PageState = unexpected()

        override suspend fun readListing(): Listing? = unexpected()

        override suspend fun openBidLayer(): LayerState = unexpected()

        override suspend fun typeMax(amount: Money): TypedAmount = unexpected()

        override suspend fun closeLayer() = unexpected()

        private fun unexpected(): Nothing = throw AssertionError("unexpected page call")
    }
}
