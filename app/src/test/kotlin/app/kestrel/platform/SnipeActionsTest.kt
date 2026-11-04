package app.kestrel.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.AddResult
import app.kestrel.data.ArmedSnipesCopy
import app.kestrel.data.EditResult
import app.kestrel.data.KestrelDatabase
import app.kestrel.data.SnipeRepository
import app.kestrel.data.TEST_NOW
import app.kestrel.data.gbp
import app.kestrel.data.testFormatMoney
import app.kestrel.data.testListing
import app.kestrel.model.SnipeStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SnipeActionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: KestrelDatabase
    private lateinit var repository: SnipeRepository
    private lateinit var actions: SnipeActions
    private val calls = mutableListOf<String>()

    @Before
    fun setUp() {
        database = KestrelDatabase.inMemory(context)
        val armedCopy = ArmedSnipesCopy(File(context.noBackupFilesDir, "armed-${System.nanoTime()}.json"))
        repository = SnipeRepository(database, armedCopy, { TEST_NOW }, ::testFormatMoney)
        actions =
            SnipeActions(
                repository = repository,
                reschedule = { calls += "schedule ${it.id} ${it.status}" },
                cancelSchedule = { calls += "cancel alarms $it" },
                stopJobs = { calls += "stop jobs $it" },
                clearNotifications = { calls += "clear notifications $it" },
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun add() = (actions.add(testListing(), gbp("48.00"), leadSeconds = 6) as AddResult.Added).snipe

    @Test
    fun aNewSnipeIsScheduledAtOnce() =
        runTest {
            val snipe = add()

            assertEquals(listOf("schedule ${snipe.id} ARMED"), calls)
        }

    @Test
    fun aRefusedAddSchedulesNothing() =
        runTest {
            val result = actions.add(testListing(isAuction = false), gbp("48.00"), leadSeconds = 6)

            assertTrue(result is AddResult.Rejected)
            assertTrue(calls.isEmpty())
        }

    @Test
    fun editsAreRescheduledOnlyWhenTheyHappen() =
        runTest {
            val snipe = add()
            calls.clear()

            actions.updateMax(snipe.id, gbp("60.00"))
            actions.setLead(snipe.id, 8)
            actions.updateMax(snipe.id, gbp("0.00"))

            assertEquals(listOf("schedule ${snipe.id} ARMED", "schedule ${snipe.id} ARMED"), calls)
        }

    @Test
    fun cancelStoresFirstThenStopsJobsAlarmsAndNotifications() =
        runTest {
            val snipe = add()
            calls.clear()

            val result = actions.cancel(snipe.id)

            assertEquals(SnipeStatus.CANCELLED, (result as EditResult.Done).snipe.status)
            assertEquals(
                listOf("stop jobs ${snipe.id}", "cancel alarms ${snipe.id}", "clear notifications ${snipe.id}"),
                calls,
            )
        }

    @Test
    fun deleteStopsEverythingAndUndoArmsAgain() =
        runTest {
            val snipe = add()
            calls.clear()

            actions.delete(snipe.id)
            actions.undoDelete(snipe.id)

            assertEquals(
                listOf(
                    "stop jobs ${snipe.id}",
                    "cancel alarms ${snipe.id}",
                    "clear notifications ${snipe.id}",
                    "schedule ${snipe.id} ARMED",
                ),
                calls,
            )
        }

    @Test
    fun aRefusedCancelLeavesEverythingRunning() =
        runTest {
            val snipe = add()
            repository.update(repository.snipe(snipe.id)!!.copy(status = SnipeStatus.BID_SENT))
            calls.clear()

            assertTrue(actions.cancel(snipe.id) is EditResult.NotAllowed)
            assertTrue(calls.isEmpty())
        }
}
