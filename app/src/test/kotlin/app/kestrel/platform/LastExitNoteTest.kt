package app.kestrel.platform

import app.kestrel.data.MINUTE
import app.kestrel.data.TEST_NOW
import app.kestrel.data.testSnipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LastExitNoteTest {
    private val events = mutableListOf<Pair<Long, String>>()
    private var noted = 0L
    private var exit: ProcessExit? = ProcessExit(TEST_NOW, "Android ended it to free memory")

    private val cutOff = testSnipe(id = 1, status = SnipeStatus.BIDDING).copy(updatedAtUtcMillis = TEST_NOW - MINUTE)
    private val armed = testSnipe(id = 2, status = SnipeStatus.ARMED).copy(updatedAtUtcMillis = TEST_NOW - MINUTE)
    private val finishedWindow =
        testSnipe(id = 3, status = SnipeStatus.BID_SENT).copy(
            updatedAtUtcMillis = TEST_NOW - MINUTE,
            result = SnipeResult(SnipeOutcome.UNKNOWN, null, null, null, null, null, "Check eBay."),
        )

    private fun note() =
        LastExitNote(
            lastExit = { exit },
            pendingSnipes = { listOf(cutOff, armed, finishedWindow) },
            addEvent = { id, message -> events += id to message },
            alreadyNoted = { noted },
            markNoted = { noted = it },
        )

    @Test
    fun aWindowTheLastProcessExitCutOffSaysWhy() =
        runTest {
            note().noteIfNew()

            assertEquals(
                listOf(1L to "Android ended Kestrel while the bid window ran (Android ended it to free memory)."),
                events,
            )
        }

    @Test
    fun eachExitIsNotedOnce() =
        runTest {
            note().noteIfNew()
            note().noteIfNew()

            assertEquals(1, events.size)
        }

    @Test
    fun aSnipeWrittenAfterTheExitWasNotCutOffByIt() =
        runTest {
            exit = ProcessExit(TEST_NOW - 2 * MINUTE, "it crashed")

            note().noteIfNew()

            assertEquals(emptyList<Pair<Long, String>>(), events)
        }
}
