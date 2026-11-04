package app.kestrel.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import app.kestrel.data.TEST_NOW
import app.kestrel.data.testSnipe
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackgroundCheckTest {
    private val runner = FakeJobRunner()
    private val registry = SnipeJobRegistry()
    private val rescheduled = mutableListOf<Pair<Snipe, Long?>>()

    private fun kotlinx.coroutines.test.TestScope.check(store: FakeStore) =
        BackgroundCheck(
            runner = runner,
            store = store,
            reschedule = { snipe, attempt -> rescheduled += snipe to attempt },
            jobs = registry,
            nowUtcMillis = { TEST_NOW },
            mainDispatcher = StandardTestDispatcher(testScheduler),
        )

    @Test
    fun aCheckRunsAndTheNextOneIsScheduledFromThisAttempt() =
        runTest {
            val snipe = testSnipe(id = 1)

            check(FakeStore(snipe)).run(1)

            assertEquals(listOf(JobKind.CHECK to 1L), runner.runs)
            assertEquals(listOf(snipe to TEST_NOW), rescheduled)
        }

    @Test
    fun aFailedCheckIsRecordedAndStillRescheduled() =
        runTest {
            runner.failure = IllegalStateException("renderer gone")
            val store = FakeStore(testSnipe(id = 1))

            check(store).run(1)

            assertEquals(
                listOf(Triple(1L, TimelineKind.PROBLEM, "Background check failed: IllegalStateException")),
                store.events,
            )
            assertEquals(1, rescheduled.size)
        }

    @Test
    fun theServiceAlreadyHavingTheListingOpenSkipsTheCheck() =
        runTest {
            registry.register(JobKey(1, JobKind.WINDOW), SnipeRequest(SnipeAction.WINDOW, 1, null, null), Job())

            check(FakeStore(testSnipe(id = 1))).run(1)

            assertTrue(runner.runs.isEmpty())
            assertEquals(1, rescheduled.size)
        }

    @Test
    fun finishedOrDeletedSnipesAreLeftAlone() =
        runTest {
            check(FakeStore(testSnipe(id = 1, status = SnipeStatus.LOST))).run(1)
            check(FakeStore()).run(2)

            assertTrue(runner.runs.isEmpty())
            assertTrue(rescheduled.isEmpty())
        }

    @Test
    fun theWorkRequestWaitsForANetworkAndItsDelay() {
        val request = CheckWorker.request(snipeId = 4, delayMillis = TimeUnit.HOURS.toMillis(5))

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(5), request.workSpec.initialDelay)
        assertEquals(4L, request.workSpec.input.getLong(CheckWorker.KEY_SNIPE_ID, 0))
        assertTrue(CheckWorker.TAG in request.tags)
        assertEquals("check-4", CheckWorker.uniqueName(4))
    }
}
