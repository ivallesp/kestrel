package app.kestrel.platform

import app.kestrel.data.HOUR
import app.kestrel.data.MINUTE
import app.kestrel.data.TEST_NOW
import app.kestrel.data.testSnipe
import app.kestrel.model.SnipeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePlannerTest {
    private val now = TEST_NOW
    private val planner = SchedulePlanner(FakePolicy())

    @Test
    fun threeDaysOutTheNextCheckGoesToWorkManagerAndTheWindowIsSetAhead() {
        val end = now + 72 * HOUR
        val plan = planner.plan(testSnipe(endTimeUtcMillis = end, lastCheckedAtUtcMillis = now - HOUR), now)

        assertEquals(PlannedCheck(now + 5 * HOUR, deferrable = true), plan.check)
        assertEquals(end - 10 * MINUTE, plan.dryRunAtUtcMillis)
        assertEquals(end - 120_000, plan.windowAtUtcMillis)
        assertEquals(end - 75_000, plan.backupAtUtcMillis)
    }

    @Test
    fun aDeferrableCheckNearTheLastHourHasAnExactBackstopAtTheHour() {
        val end = now + 90 * MINUTE
        val plan = planner.plan(testSnipe(endTimeUtcMillis = end, lastCheckedAtUtcMillis = now - 50 * MINUTE), now)

        assertTrue(plan.check!!.deferrable)
        assertEquals(end - HOUR, plan.exactCheckBackstopAtUtcMillis)
        // Far from the end the next deferrable check comes long before the hour, so it needs none.
        val far = planner.plan(testSnipe(endTimeUtcMillis = now + 72 * HOUR, lastCheckedAtUtcMillis = now - HOUR), now)
        assertNull(far.exactCheckBackstopAtUtcMillis)
    }

    @Test
    fun checksInTheLastHourUseExactAlarms() {
        val end = now + 45 * MINUTE
        val plan = planner.plan(testSnipe(endTimeUtcMillis = end, lastCheckedAtUtcMillis = now - 10 * MINUTE), now)

        assertEquals(PlannedCheck(now + 5 * MINUTE, deferrable = false), plan.check)
    }

    @Test
    fun theOneHourBoundaryBelongsToExactAlarms() {
        val end = now + 2 * HOUR
        val atBoundary = planner.plan(testSnipe(endTimeUtcMillis = end, lastCheckedAtUtcMillis = now), now)
        val justBefore = planner.plan(testSnipe(endTimeUtcMillis = end, lastCheckedAtUtcMillis = now - MINUTE), now)

        assertEquals(PlannedCheck(end - HOUR, deferrable = false), atBoundary.check)
        assertEquals(PlannedCheck(end - HOUR - MINUTE, deferrable = true), justBefore.check)
    }

    @Test
    fun inTheLastTenMinutesOnlyTheWindowBackupAndAResultCheckRemain() {
        val end = now + 5 * MINUTE
        val plan = planner.plan(testSnipe(endTimeUtcMillis = end), now)

        assertNull(plan.dryRunAtUtcMillis)
        assertEquals(end - 120_000, plan.windowAtUtcMillis)
        assertEquals(end - 75_000, plan.backupAtUtcMillis)
        assertEquals(PlannedCheck(end + SchedulePlanner.FIRST_RESOLVE_DELAY_MILLIS, deferrable = true), plan.check)
    }

    @Test
    fun aSnipeArmedBetweenWindowAndBackupOpensTheWindowNow() {
        val end = now + 90_000
        val plan = planner.plan(testSnipe(endTimeUtcMillis = end), now)

        assertEquals(now, plan.windowAtUtcMillis)
        assertEquals(end - 75_000, plan.backupAtUtcMillis)
    }

    @Test
    fun aSnipeArmedAfterTheBackupTimeStillOpensTheWindowNow() {
        val plan = planner.plan(testSnipe(endTimeUtcMillis = now + 60_000), now)

        assertEquals(now, plan.windowAtUtcMillis)
        assertNull(plan.backupAtUtcMillis)
    }

    @Test
    fun aPassedWindowIsNotReopenedForASnipeTheWindowAlreadyHandled() {
        for (status in listOf(SnipeStatus.PRICE_OVER_MAX, SnipeStatus.NEEDS_SIGN_IN, SnipeStatus.NEEDS_YOU)) {
            val plan = planner.plan(testSnipe(status = status, endTimeUtcMillis = now + 60_000), now)
            assertNull("$status", plan.windowAtUtcMillis)
        }
    }

    @Test
    fun aWindowCutOffBeforeItsBidReopensOnlyWhenNothingRunsIt() {
        val interrupted = testSnipe(status = SnipeStatus.BIDDING, endTimeUtcMillis = now + 60_000)

        assertEquals(now, planner.plan(interrupted, now, windowRunning = false).windowAtUtcMillis)
        assertNull(planner.plan(interrupted, now, windowRunning = true).windowAtUtcMillis)
        assertNull(
            "too late once the auction ended",
            planner.plan(interrupted, now + 60_000, windowRunning = false).windowAtUtcMillis,
        )
    }

    @Test
    fun aBidAlreadyWithEbayNeverOpensAnotherWindow() {
        for (status in listOf(SnipeStatus.BID_SENT, SnipeStatus.LEADING, SnipeStatus.OUTBID, SnipeStatus.EXTENDED)) {
            val plan =
                planner.plan(
                    testSnipe(status = status, endTimeUtcMillis = now + 60_000),
                    now,
                    windowRunning = false,
                )
            assertNull("$status", plan.windowAtUtcMillis)
            assertNull("$status", plan.backupAtUtcMillis)
        }
    }

    @Test
    fun attentionStatusesKeepTheirFutureWindow() {
        val end = now + 5 * MINUTE
        for (status in listOf(SnipeStatus.DRY_RUN_FAILED, SnipeStatus.NEEDS_SIGN_IN, SnipeStatus.PRICE_OVER_MAX)) {
            assertEquals(
                "$status",
                end - 120_000,
                planner.plan(testSnipe(status = status, endTimeUtcMillis = end), now).windowAtUtcMillis,
            )
        }
    }

    @Test
    fun watchOnlySnipesOnlyGetChecks() {
        val plan =
            planner.plan(
                testSnipe(
                    status = SnipeStatus.WATCHING,
                    maxBid = null,
                    endTimeUtcMillis =
                        now + 3 * HOUR,
                ),
                now,
            )

        assertNull(plan.dryRunAtUtcMillis)
        assertNull(plan.windowAtUtcMillis)
        assertNull(plan.backupAtUtcMillis)
        assertTrue(plan.check != null)
    }

    @Test
    fun finishedSnipesNeedNothing() {
        for (status in SnipeStatus.entries.filter { it.isFinal }) {
            assertTrue("$status", planner.plan(testSnipe(status = status), now).isEmpty)
        }
    }

    @Test
    fun afterTheEndAFewSpacedChecksSettleAnUnresolvedResultThenStop() {
        val end = now - MINUTE
        val sent =
            testSnipe(
                status = SnipeStatus.BID_SENT,
                endTimeUtcMillis = end,
                lastCheckedAtUtcMillis =
                    end - 15 * MINUTE,
            )

        val first = planner.plan(sent, now)
        assertEquals(PlannedCheck(end + 3 * MINUTE, deferrable = true), first.check)
        assertNull(first.windowAtUtcMillis)

        val second = planner.plan(sent, nowUtcMillis = end + 3 * MINUTE, lastCheckAttemptUtcMillis = end + 3 * MINUTE)
        assertEquals(end + 33 * MINUTE, second.check?.atUtcMillis)

        // Past six hours (the phone was off overnight, or eBay kept answering with sign-in pages) every six hours.
        val later = planner.plan(sent, nowUtcMillis = end + 7 * HOUR, lastCheckAttemptUtcMillis = end + 6 * HOUR)
        assertEquals(end + 12 * HOUR, later.check?.atUtcMillis)
        val backOn = planner.plan(sent, nowUtcMillis = end + 30 * HOUR)
        assertEquals(end + 30 * HOUR + MINUTE, backOn.check?.atUtcMillis)

        val tooLate =
            planner.plan(sent, nowUtcMillis = end + 3 * 24 * HOUR, lastCheckAttemptUtcMillis = end + 3 * 24 * HOUR)
        assertTrue(tooLate.isEmpty)
    }

    @Test
    fun anOverdueCheckWaitsAtLeastAMinute() {
        val overdue = SchedulePlanner(FakePolicy { current, _, _ -> current - 5 * MINUTE })

        assertEquals(now + MINUTE, overdue.plan(testSnipe(), now).check?.atUtcMillis)
    }

    @Test
    fun aFailedCheckStillCountsSoTheNextOneWaitsItsInterval() {
        val stale = testSnipe(endTimeUtcMillis = now + 3 * HOUR, lastCheckedAtUtcMillis = now - 3 * HOUR)

        assertEquals(now + MINUTE, planner.plan(stale, now).check?.atUtcMillis)
        assertEquals(now + HOUR, planner.plan(stale, now, lastCheckAttemptUtcMillis = now).check?.atUtcMillis)
    }
}
