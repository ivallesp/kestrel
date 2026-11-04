package app.kestrel.platform

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.kestrel.data.HOUR
import app.kestrel.data.MINUTE
import app.kestrel.data.TEST_NOW
import app.kestrel.data.testSnipe
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class SnipeSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val clock = FakeClock(now = TEST_NOW, elapsed = 9_000_000L)
    private val systemClockSkewMillis = 2_500L
    private val problems = RecordingProblemNotifier()
    private var pending: List<Snipe> = emptyList()
    private val runningWindows = mutableSetOf<Long>()
    private lateinit var scheduler: SnipeScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        setBatteryUnrestricted(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler =
            SnipeScheduler(
                context = context,
                clock = clock,
                // The phone's own clock runs 2.5 s behind true time.
                systemUtcMillis = { clock.now - systemClockSkewMillis },
                planner = SchedulePlanner(FakePolicy()),
                pendingSnipes = { pending },
                permissions = AndroidAlarmPermissions(context),
                workManager = { WorkManager.getInstance(context) },
                problemNotifier = problems,
                isWindowRunning = { it in runningWindows },
            )
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun setBatteryUnrestricted(unrestricted: Boolean) {
        shadowOf(
            context.getSystemService(PowerManager::class.java),
        ).setIgnoringBatteryOptimizations(context.packageName, unrestricted)
    }

    private fun alarms(): List<ShadowAlarmManager.ScheduledAlarm> = shadowOf(alarmManager).scheduledAlarms

    private fun alarmFor(
        action: SnipeAction,
        snipeId: Long,
    ): ShadowAlarmManager.ScheduledAlarm? =
        alarms().firstOrNull {
            val intent = it.savedIntent
            SnipeRequest.from(intent)?.let { request -> request.action == action && request.snipeId == snipeId } == true
        }

    private fun checkWork(snipeId: Long): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(CheckWorker.uniqueName(snipeId)).get()

    private fun elapsedAt(utcMillis: Long): Long = clock.elapsed + (utcMillis - clock.now)

    @Test
    fun aSnipeInItsLastHourGetsExactAlarmsOnElapsedRealtimeForEveryStep() {
        val end = clock.now + 45 * MINUTE
        val snipe = testSnipe(id = 7, endTimeUtcMillis = end, lastCheckedAtUtcMillis = clock.now - 10 * MINUTE)

        scheduler.schedule(snipe)

        assertEquals(4, alarms().size)
        val expected =
            mapOf(
                SnipeAction.CHECK to clock.now + 5 * MINUTE,
                SnipeAction.DRY_RUN to end - 10 * MINUTE,
                SnipeAction.WINDOW to end - 120_000,
                SnipeAction.WINDOW_BACKUP to end - 75_000,
            )
        for ((action, atUtc) in expected) {
            val alarm = alarmFor(action, snipe.id)
            assertNotNull("$action", alarm)
            alarm!!
            assertEquals("$action type", AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.getType())
            assertEquals("$action time", elapsedAt(atUtc), alarm.triggerAtMs)
            assertTrue("$action allow while idle", alarm.isAllowWhileIdle)
            assertEquals("$action exact", ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
            assertNull("$action is not an alarm clock", alarm.alarmClockInfo)
            assertEquals(AlarmReceiver::class.java.name, alarm.savedIntent.component?.className)
        }
        assertTrue(checkWork(snipe.id).none { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun theLastDeferrableCheckBeforeTheHourGetsAnExactBackstopAtTheHour() {
        // 90 min before the end: WorkManager takes the next check, which Doze may hold until just before the dry run.
        val end = clock.now + 90 * MINUTE
        val snipe = testSnipe(id = 4, endTimeUtcMillis = end, lastCheckedAtUtcMillis = clock.now - 50 * MINUTE)

        scheduler.schedule(snipe)

        assertEquals(WorkInfo.State.ENQUEUED, checkWork(snipe.id).single().state)
        val backstop = alarmFor(SnipeAction.CHECK, snipe.id)
        assertNotNull(backstop)
        assertEquals(elapsedAt(end - HOUR), backstop!!.triggerAtMs)
    }

    @Test
    fun aFarCheckGoesToWorkManagerWithANetworkConstraint() {
        val end = clock.now + 72 * HOUR
        val snipe = testSnipe(id = 3, endTimeUtcMillis = end, lastCheckedAtUtcMillis = clock.now - HOUR)

        scheduler.schedule(snipe)

        val work = checkWork(snipe.id).single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertEquals(5 * HOUR, work.initialDelayMillis)
        assertNull(alarmFor(SnipeAction.CHECK, snipe.id))
        assertNotNull(alarmFor(SnipeAction.WINDOW, snipe.id))
        assertNotNull(alarmFor(SnipeAction.DRY_RUN, snipe.id))
    }

    @Test
    fun withoutUnrestrictedBatteryTheBidAlarmsBecomeAlarmClocks() {
        setBatteryUnrestricted(false)
        val end = clock.now + 45 * MINUTE
        val snipe = testSnipe(id = 5, endTimeUtcMillis = end)

        scheduler.schedule(snipe)

        for ((action, atUtc) in mapOf(
            SnipeAction.DRY_RUN to end - 10 * MINUTE,
            SnipeAction.WINDOW to end - 120_000,
            SnipeAction.WINDOW_BACKUP to end - 75_000,
        )) {
            val alarm = alarmFor(action, snipe.id)!!
            val info = alarm.alarmClockInfo
            assertNotNull("$action is an alarm clock", info)
            assertEquals("$action type", AlarmManager.RTC_WAKEUP, alarm.getType())
            // Placed on the phone's own clock, which runs behind true time.
            assertEquals("$action time", atUtc - systemClockSkewMillis, info!!.triggerTime)
            assertNotNull(info.showIntent)
        }
        val check = alarmFor(SnipeAction.CHECK, snipe.id)!!
        assertNull(check.alarmClockInfo)
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, check.getType())
    }

    @Test
    fun cancelClearsEveryAlarmAndTheBackgroundCheck() {
        val far = testSnipe(id = 1, endTimeUtcMillis = clock.now + 72 * HOUR)
        val near = testSnipe(id = 2, endTimeUtcMillis = clock.now + 45 * MINUTE)
        scheduler.schedule(far)
        scheduler.schedule(near)

        scheduler.cancel(far.id)
        scheduler.cancel(near.id)

        assertTrue(alarms().isEmpty())
        assertTrue(checkWork(far.id).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun aFinishedSnipeHasEverythingCancelled() {
        val snipe = testSnipe(id = 4, endTimeUtcMillis = clock.now + 45 * MINUTE)
        scheduler.schedule(snipe)

        scheduler.schedule(snipe.copy(status = SnipeStatus.WON))

        assertTrue(alarms().isEmpty())
    }

    @Test
    fun reschedulingReplacesAlarmsInsteadOfAddingMore() {
        val snipe = testSnipe(id = 4, endTimeUtcMillis = clock.now + 45 * MINUTE)
        scheduler.schedule(snipe)
        val moved = snipe.copy(endTimeUtcMillis = snipe.endTimeUtcMillis + 2 * MINUTE)

        scheduler.schedule(moved)

        assertEquals(4, alarms().size)
        assertEquals(elapsedAt(moved.endTimeUtcMillis - 120_000), alarmFor(SnipeAction.WINDOW, snipe.id)?.triggerAtMs)
    }

    @Test
    fun eachSnipeHasItsOwnAlarms() {
        scheduler.schedule(testSnipe(id = 1, endTimeUtcMillis = clock.now + 45 * MINUTE))
        scheduler.schedule(testSnipe(id = 2, endTimeUtcMillis = clock.now + 50 * MINUTE))

        assertEquals(8, alarms().size)
        scheduler.cancel(1)
        assertEquals(4, alarms().size)
        assertNotNull(alarmFor(SnipeAction.WINDOW, 2))
    }

    @Test
    fun aWatchedSnipeOnlyGetsItsCheck() {
        scheduler.schedule(
            testSnipe(
                id = 9,
                status = SnipeStatus.WATCHING,
                maxBid = null,
                endTimeUtcMillis =
                    clock.now + 45 * MINUTE,
            ),
        )

        assertEquals(
            listOf(SnipeAction.CHECK),
            alarms().map { SnipeRequest.from(it.savedIntent)!!.action },
        )
    }

    @Test
    fun aLateSnipeOpensItsWindowAsSoonAsAndroidAllows() {
        val snipe = testSnipe(id = 6, endTimeUtcMillis = clock.now + 60_000)

        scheduler.schedule(snipe)

        assertEquals(clock.elapsed, alarmFor(SnipeAction.WINDOW, snipe.id)?.triggerAtMs)
    }

    @Test
    fun aWindowCutOffBeforeItsBidOpensAgainUnlessItIsStillRunning() {
        val interrupted = testSnipe(id = 6, status = SnipeStatus.BIDDING, endTimeUtcMillis = clock.now + 60_000)

        runningWindows += interrupted.id
        scheduler.schedule(interrupted)
        assertNull("a running window is left alone", alarmFor(SnipeAction.WINDOW, interrupted.id))

        runningWindows.clear()
        scheduler.schedule(interrupted)
        assertEquals(clock.elapsed, alarmFor(SnipeAction.WINDOW, interrupted.id)?.triggerAtMs)
    }

    @Test
    fun withoutExactAlarmsAnInexactAlarmIsSetAndTheProblemIsReported() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val snipe = testSnipe(id = 8, endTimeUtcMillis = clock.now + 45 * MINUTE)

        scheduler.schedule(snipe)

        val window = alarmFor(SnipeAction.WINDOW, snipe.id)!!
        assertEquals(ShadowAlarmManager.WINDOW_HEURISTIC, window.windowLengthMs)
        assertTrue(window.isAllowWhileIdle)
        assertEquals(ScheduleProblem.EXACT_ALARMS_DENIED, scheduler.problem.value)
        assertEquals(listOf("show EXACT_ALARMS_DENIED"), problems.calls)

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule(snipe)
        assertNull(scheduler.problem.value)
        assertEquals(listOf("show EXACT_ALARMS_DENIED", "clear"), problems.calls)
    }

    @Test
    fun rescheduleAllArmsEveryPendingSnipe() =
        runTest {
            pending =
                listOf(
                    testSnipe(id = 1, endTimeUtcMillis = clock.now + 45 * MINUTE),
                    testSnipe(id = 2, endTimeUtcMillis = clock.now + 50 * MINUTE),
                )

            scheduler.rescheduleAll()

            assertNotNull(alarmFor(SnipeAction.WINDOW, 1))
            assertNotNull(alarmFor(SnipeAction.WINDOW, 2))
        }

    @Test
    fun thePublicConstructorStillWorksThroughTheAppContainer() {
        assertNotNull(SnipeScheduler(context as Context))
    }

    class RecordingProblemNotifier : ScheduleProblemNotifier {
        val calls = mutableListOf<String>()

        override fun showScheduleProblem(problem: ScheduleProblem) {
            calls += "show $problem"
        }

        override fun clearScheduleProblem() {
            calls += "clear"
        }
    }
}
