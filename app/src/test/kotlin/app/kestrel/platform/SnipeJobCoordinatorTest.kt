package app.kestrel.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SnipeJobCoordinatorTest {
    private val runner = FakeJobRunner(holdJobs = true)
    private val registry = SnipeJobRegistry()
    private val wakeLocks = FakeWakeLocks()
    private val aftermath = RecordingAftermath()
    private var changes = 0

    private fun TestScope.coordinator() =
        SnipeJobCoordinator(
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            runner = runner,
            registry = registry,
            wakeLocks = wakeLocks,
            aftermath = aftermath,
            nowUtcMillis = { 1_000L },
            onJobsChanged = { changes++ },
        )

    private fun request(
        action: SnipeAction,
        snipeId: Long = 1L,
    ) = SnipeRequest(action, snipeId, title = "Canon AE-1", endTimeUtcMillis = 9_000_000L)

    @Test
    fun aWindowRunsWithAWakeLockAndIsRescheduledAfterwards() =
        runTest {
            val coordinator = coordinator()

            assertEquals(Submission.STARTED, coordinator.submit(request(SnipeAction.WINDOW)))
            runCurrent()
            assertEquals(listOf(JobKind.WINDOW to 1L), runner.runs)
            assertEquals(1, wakeLocks.held)
            assertEquals(listOf("kestrel:window" to JobKind.WINDOW.wakeLockMillis), wakeLocks.acquired)

            runner.release(JobKind.WINDOW, 1)
            advanceUntilIdle()

            assertEquals(0, wakeLocks.held)
            assertTrue(registry.isEmpty())
            assertEquals(listOf("finished 1 WINDOW at 1000"), aftermath.calls)
            assertTrue(changes >= 2)
        }

    @Test
    fun theSameJobIsNeverRunTwiceAndTheBackupDoesNothingWhileTheWindowRuns() =
        runTest {
            val coordinator = coordinator()
            coordinator.submit(request(SnipeAction.WINDOW))
            runCurrent()

            assertEquals(Submission.ALREADY_RUNNING, coordinator.submit(request(SnipeAction.WINDOW)))
            assertEquals(Submission.ALREADY_RUNNING, coordinator.submit(request(SnipeAction.WINDOW_BACKUP)))
            runCurrent()

            assertEquals(1, runner.runs.size)
        }

    @Test
    fun aRunningWindowCoversDryRunsAndChecksOfTheSameSnipe() =
        runTest {
            val coordinator = coordinator()
            coordinator.submit(request(SnipeAction.WINDOW))
            runCurrent()

            assertEquals(Submission.COVERED_BY_RUNNING_JOB, coordinator.submit(request(SnipeAction.DRY_RUN)))
            assertEquals(Submission.COVERED_BY_RUNNING_JOB, coordinator.submit(request(SnipeAction.CHECK)))
        }

    @Test
    fun aWindowReplacesACheckStillLoadingTheSameListing() =
        runTest {
            val coordinator = coordinator()
            coordinator.submit(request(SnipeAction.CHECK))
            runCurrent()

            assertEquals(Submission.STARTED, coordinator.submit(request(SnipeAction.WINDOW)))
            runCurrent()

            assertEquals(setOf(JobKind.WINDOW), registry.runningKinds(1))
            assertEquals(listOf(JobKind.CHECK to 1L, JobKind.WINDOW to 1L), runner.runs)
            assertTrue("the cancelled check is still rescheduled", "finished 1 CHECK at 1000" in aftermath.calls)
        }

    @Test
    fun windowsOfDifferentSnipesRunSideBySide() =
        runTest {
            val coordinator = coordinator()

            coordinator.submit(request(SnipeAction.WINDOW, snipeId = 1))
            coordinator.submit(request(SnipeAction.WINDOW, snipeId = 2))
            runCurrent()

            assertEquals(listOf(1L, 2L), registry.runningWindows().map { it.snipeId })
            assertEquals(2, wakeLocks.held)
            runner.release(JobKind.WINDOW, 1)
            // runCurrent, not advanceUntilIdle: skipping ahead would run the second window into its timeout.
            runCurrent()
            assertEquals(listOf(2L), registry.runningWindows().map { it.snipeId })
            assertEquals(1, wakeLocks.held)
        }

    @Test
    fun aFailingJobIsReportedAndStillRescheduled() =
        runTest {
            runner.holdJobs = false
            runner.failure = IllegalStateException("page crashed")
            val coordinator = coordinator()

            coordinator.submit(request(SnipeAction.DRY_RUN))
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "failed 1 DRY_RUN: Dry run stopped by an error: IllegalStateException: page crashed",
                    "finished 1 DRY_RUN at 1000",
                ),
                aftermath.calls,
            )
            assertEquals(0, wakeLocks.held)
        }

    @Test
    fun aJobThatHangsIsStoppedAtItsTimeout() =
        runTest {
            val coordinator = coordinator()
            coordinator.submit(request(SnipeAction.CHECK))
            runCurrent()

            advanceTimeBy(JobKind.CHECK.timeoutMillis + 1)
            runCurrent()

            assertEquals(
                listOf("failed 1 CHECK: Check stopped after 3 min without finishing.", "finished 1 CHECK at 1000"),
                aftermath.calls,
            )
            assertTrue(registry.isEmpty())
        }

    @Test
    fun cancellingASnipeStopsItsJobs() =
        runTest {
            val coordinator = coordinator()
            coordinator.submit(request(SnipeAction.WINDOW, snipeId = 1))
            coordinator.submit(request(SnipeAction.WINDOW, snipeId = 2))
            runCurrent()

            registry.cancelAll(1)
            runCurrent()

            assertEquals(listOf(2L), registry.runningWindows().map { it.snipeId })
            assertEquals(1, wakeLocks.held)
        }

    internal class RecordingAftermath : JobAftermath {
        val calls = mutableListOf<String>()

        override suspend fun jobFailed(
            snipeId: Long,
            kind: JobKind,
            message: String,
        ) {
            calls += "failed $snipeId $kind: $message"
        }

        override suspend fun jobFinished(
            snipeId: Long,
            kind: JobKind,
            startedAtUtcMillis: Long,
        ) {
            calls += "finished $snipeId $kind at $startedAtUtcMillis"
        }
    }
}
