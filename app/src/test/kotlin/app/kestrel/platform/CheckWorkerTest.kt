package app.kestrel.platform

import android.app.AlarmManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import app.kestrel.data.AddResult
import app.kestrel.data.HOUR
import app.kestrel.data.TEST_NOW
import app.kestrel.data.gbp
import app.kestrel.data.testListing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestKestrelApp::class)
class CheckWorkerTest {
    private val app = ApplicationProvider.getApplicationContext<TestKestrelApp>()

    /** Runs the worker as WorkManager would, letting its main-thread part run on Robolectric's main looper. */
    private fun runWorker(snipeId: Long?): ListenableWorker.Result =
        finish(
            TestListenableWorkerBuilder<CheckWorker>(app)
                .apply { if (snipeId != null) setInputData(workDataOf(CheckWorker.KEY_SNIPE_ID to snipeId)) }
                .build(),
        )

    private fun finish(worker: ListenableWorker): ListenableWorker.Result {
        val outcome = worker.startWork()
        repeat(500) {
            if (outcome.isDone) return outcome.get()
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        throw AssertionError("the worker did not finish")
    }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun theWorkerRunsTheCheckThroughTheAppContainerAndQueuesTheNextOne() {
        val listing = testListing(endTimeUtcMillis = TEST_NOW + 3 * HOUR)
        val snipe =
            runBlocking {
                app.testContainer.repository.addFromListing(listing, gbp("48.00"), 6) as AddResult.Added
            }.snipe

        assertEquals(ListenableWorker.Result.success(), runWorker(snipe.id))
        assertEquals(listOf(JobKind.CHECK to snipe.id), app.testContainer.fakeRunner.runs)
        val next =
            WorkManager
                .getInstance(
                    app,
                ).getWorkInfosForUniqueWork(CheckWorker.uniqueName(snipe.id))
                .get()
                .single()
        assertEquals(WorkInfo.State.ENQUEUED, next.state)
        assertEquals("an hour after this attempt", HOUR, next.initialDelayMillis)
    }

    @Test
    fun aDeletedSnipeEndsTheWorkQuietly() {
        assertEquals(ListenableWorker.Result.success(), runWorker(snipeId = 404))
        assertTrue(
            app.testContainer.fakeRunner.runs
                .isEmpty(),
        )
    }

    @Test
    fun theRescheduleFallbackRearmsEveryPendingSnipe() {
        val listing = testListing(endTimeUtcMillis = TEST_NOW + 3 * HOUR)
        val snipe =
            runBlocking {
                app.testContainer.repository.addFromListing(listing, gbp("48.00"), 6) as AddResult.Added
            }.snipe

        assertEquals(
            ListenableWorker.Result.success(),
            finish(TestListenableWorkerBuilder<RescheduleWorker>(app).build()),
        )

        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertTrue(alarms.any { SnipeRequest.from(it.savedIntent) == SnipeRequest.of(SnipeAction.WINDOW, snipe) })
    }

    @Test
    fun workWithoutASnipeFails() {
        assertEquals(ListenableWorker.Result.failure(), runWorker(snipeId = null))
    }
}
