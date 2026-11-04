package app.kestrel.platform

import app.kestrel.data.TEST_NOW
import app.kestrel.data.testSnipe
import app.kestrel.engine.NeedsUserReason
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnipeJobAftermathTest {
    private val notifier = RecordingNotifier()
    private val rescheduled = mutableListOf<Pair<Snipe, Long?>>()
    private val cancelled = mutableListOf<Long>()

    private fun aftermath(store: FakeStore) =
        SnipeJobAftermath(
            store = store,
            notifier = notifier,
            reschedule = { snipe, attempt -> rescheduled += snipe to attempt },
            cancelSchedule = { cancelled += it },
            nowUtcMillis = { TEST_NOW },
        )

    @Test
    fun aCheckIsRescheduledCountingItsAttempt() =
        runTest {
            val snipe = testSnipe(id = 1)
            aftermath(FakeStore(snipe)).jobFinished(1, JobKind.CHECK, startedAtUtcMillis = TEST_NOW - 5_000)

            assertEquals(listOf(snipe to TEST_NOW - 5_000), rescheduled)
        }

    @Test
    fun aWindowThatLeftTheSnipeArmedTellsTheUserInsteadOfLooping() =
        runTest {
            for (status in listOf(SnipeStatus.ARMED, SnipeStatus.BIDDING)) {
                rescheduled.clear()
                notifier.needsUser.clear()
                val store = FakeStore(testSnipe(id = 1, status = status))

                aftermath(store).jobFinished(1, JobKind.WINDOW, startedAtUtcMillis = TEST_NOW - 120_000)

                assertEquals("$status", SnipeStatus.NEEDS_YOU, store.snipes.getValue(1).status)
                assertEquals(listOf(NeedsUserReason.BID_FAILED to SnipeJobAftermath.WINDOW_STOPPED), notifier.needsUser)
                assertEquals(
                    listOf(Triple(1L, TimelineKind.PROBLEM, SnipeJobAftermath.WINDOW_STOPPED)),
                    store.events.takeLast(1),
                )
                assertEquals(SnipeStatus.NEEDS_YOU, rescheduled.single().first.status)
                assertEquals(null, rescheduled.single().second)
            }
        }

    @Test
    fun aWindowThatReachedAResultIsRescheduledAndItsLiveUpdateClosed() =
        runTest {
            val won = testSnipe(id = 1, status = SnipeStatus.WON)

            aftermath(FakeStore(won)).jobFinished(1, JobKind.WINDOW, startedAtUtcMillis = TEST_NOW)

            assertEquals(listOf(won to null), rescheduled)
            assertEquals(listOf("windowClosed 1"), notifier.calls)
        }

    @Test
    fun otherJobsLeaveTheLiveUpdateAlone() =
        runTest {
            aftermath(FakeStore(testSnipe(id = 1))).jobFinished(1, JobKind.DRY_RUN, startedAtUtcMillis = TEST_NOW)

            assertTrue(notifier.calls.isEmpty())
        }

    @Test
    fun aDeletedSnipeHasItsAlarmsCancelled() =
        runTest {
            aftermath(FakeStore()).jobFinished(4, JobKind.DRY_RUN, startedAtUtcMillis = TEST_NOW)

            assertEquals(listOf(4L), cancelled)
            assertTrue(rescheduled.isEmpty())
        }

    @Test
    fun failuresGoToTheTimeline() =
        runTest {
            val store = FakeStore(testSnipe(id = 1))

            aftermath(store).jobFailed(1, JobKind.CHECK, "Check stopped by an error")

            assertEquals(listOf(Triple(1L, TimelineKind.PROBLEM, "Check stopped by an error")), store.events)
        }
}
