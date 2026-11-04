package app.kestrel.platform

import app.kestrel.engine.MonitorPolicy
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus

/** The timing rules the scheduler follows; the engine's [MonitorPolicy] in the app, a fake in tests. */
internal interface SchedulePolicy {
    fun nextCheckAt(
        nowUtcMillis: Long,
        endUtcMillis: Long,
        lastCheckUtcMillis: Long?,
    ): Long?

    fun dryRunAt(endUtcMillis: Long): Long

    fun windowOpensAt(endUtcMillis: Long): Long

    fun backupWindowAt(endUtcMillis: Long): Long
}

internal object MonitorSchedulePolicy : SchedulePolicy {
    override fun nextCheckAt(
        nowUtcMillis: Long,
        endUtcMillis: Long,
        lastCheckUtcMillis: Long?,
    ): Long? = MonitorPolicy.nextCheckAt(nowUtcMillis, endUtcMillis, lastCheckUtcMillis)

    override fun dryRunAt(endUtcMillis: Long): Long = MonitorPolicy.dryRunAt(endUtcMillis)

    override fun windowOpensAt(endUtcMillis: Long): Long = MonitorPolicy.windowOpensAt(endUtcMillis)

    override fun backupWindowAt(endUtcMillis: Long): Long = MonitorPolicy.backupWindowAt(endUtcMillis)
}

/** A check far from the end can wait for WorkManager; one near the end needs an exact alarm. */
internal data class PlannedCheck(
    val atUtcMillis: Long,
    val deferrable: Boolean,
)

/** Every wake-up one snipe needs, in true UTC. Null means "none, cancel any that exists". */
internal data class SchedulePlan(
    val check: PlannedCheck?,
    val dryRunAtUtcMillis: Long?,
    val windowAtUtcMillis: Long?,
    val backupAtUtcMillis: Long?,
    /**
     * An exact check at the start of the last hour, next to a deferrable check close to it: Doze can hold that work
     * until shortly before the dry run, and the last hour's exact checks are only planned once a check has run.
     */
    val exactCheckBackstopAtUtcMillis: Long? = null,
) {
    val isEmpty: Boolean
        get() = check == null && dryRunAtUtcMillis == null && windowAtUtcMillis == null && backupAtUtcMillis == null

    companion object {
        val NOTHING =
            SchedulePlan(check = null, dryRunAtUtcMillis = null, windowAtUtcMillis = null, backupAtUtcMillis = null)
    }
}

/** Turns a snipe and the time into a [SchedulePlan]. Pure, so every distance from the end is testable on the JVM. */
internal class SchedulePlanner(
    private val policy: SchedulePolicy,
) {
    /**
     * [lastCheckAttemptUtcMillis] is when a check last ran, even if it failed, so a failing check cannot loop.
     * [windowRunning] says whether this process is running the snipe's window right now.
     */
    fun plan(
        snipe: Snipe,
        nowUtcMillis: Long,
        lastCheckAttemptUtcMillis: Long? = null,
        windowRunning: Boolean = false,
    ): SchedulePlan {
        if (snipe.status.isFinal) return SchedulePlan.NOTHING
        val end = snipe.endTimeUtcMillis
        val willBid = snipe.maxBid != null && snipe.status.isActiveBid
        val check = checkAt(snipe, nowUtcMillis, lastCheckAttemptUtcMillis)
        return SchedulePlan(
            check = check,
            dryRunAtUtcMillis = if (willBid) policy.dryRunAt(end).takeIf { it > nowUtcMillis } else null,
            windowAtUtcMillis = if (willBid) windowAt(snipe, nowUtcMillis, windowRunning) else null,
            backupAtUtcMillis = if (willBid) policy.backupWindowAt(end).takeIf { it > nowUtcMillis } else null,
            exactCheckBackstopAtUtcMillis = backstopFor(check, end, nowUtcMillis),
        )
    }

    private fun backstopFor(
        check: PlannedCheck?,
        end: Long,
        now: Long,
    ): Long? {
        if (check == null || !check.deferrable || check.atUtcMillis >= end) return null
        if (end - check.atUtcMillis > BACKSTOP_FOR_CHECKS_WITHIN_MILLIS) return null
        return (end - EXACT_CHECKS_BEFORE_END_MILLIS).takeIf { it > now }
    }

    private fun windowAt(
        snipe: Snipe,
        now: Long,
        windowRunning: Boolean,
    ): Long? {
        val opens = policy.windowOpensAt(snipe.endTimeUtcMillis)
        return when {
            opens > now -> opens

            now >= snipe.endTimeUtcMillis -> null

            // Added late, or the max was raised after the window time: open it now. Every status a window leaves
            // behind is something else, so this cannot reopen a window that already ran.
            snipe.status == SnipeStatus.ARMED -> now

            // A window cut off before it sent the bid (the app was updated or killed): open it again. It reads the
            // stored max afresh, which the user can only have changed before the edits locked, 3 s before the bid.
            snipe.status == SnipeStatus.BIDDING && !windowRunning -> now

            else -> null
        }
    }

    private fun checkAt(
        snipe: Snipe,
        now: Long,
        lastCheckAttempt: Long?,
    ): PlannedCheck? {
        val end = snipe.endTimeUtcMillis
        val lastCheck = listOfNotNull(snipe.lastCheckedAtUtcMillis, lastCheckAttempt).maxOrNull()
        val next = policy.nextCheckAt(now, end, lastCheck)
        val target =
            if (next != null) {
                maxOf(next, now + MINIMUM_CHECK_DELAY_MILLIS)
            } else {
                resolveCheckAt(end, now, lastCheck) ?: return null
            }
        val deferrable = target >= end || end - target > EXACT_CHECKS_BEFORE_END_MILLIS
        return PlannedCheck(target, deferrable)
    }

    /**
     * When the policy has no more checks, a few spaced checks after the end settle a result the window could not
     * (the phone was off, or the app was killed mid-window). They stop a few hours after the end.
     */
    private fun resolveCheckAt(
        end: Long,
        now: Long,
        lastCheck: Long?,
    ): Long? {
        val afterEndCheck = lastCheck?.takeIf { it >= end }
        val spaced = afterEndCheck?.plus(RESOLVE_SPACING_MILLIS) ?: (end + FIRST_RESOLVE_DELAY_MILLIS)
        val target = maxOf(spaced, now + MINIMUM_CHECK_DELAY_MILLIS)
        return target.takeIf { it <= end + RESOLVE_GIVE_UP_AFTER_MILLIS }
    }

    companion object {
        private const val MINUTE_MILLIS = 60_000L
        const val MINIMUM_CHECK_DELAY_MILLIS = MINUTE_MILLIS
        const val EXACT_CHECKS_BEFORE_END_MILLIS = 60 * MINUTE_MILLIS

        /** A deferrable check this close to the end is the last one before the hour, and gets an exact backstop. */
        const val BACKSTOP_FOR_CHECKS_WITHIN_MILLIS = 2 * 60 * MINUTE_MILLIS
        const val FIRST_RESOLVE_DELAY_MILLIS = 3 * MINUTE_MILLIS
        const val RESOLVE_SPACING_MILLIS = 30 * MINUTE_MILLIS
        const val RESOLVE_GIVE_UP_AFTER_MILLIS = 6 * 60 * MINUTE_MILLIS
    }
}
