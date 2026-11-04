package app.kestrel.engine

import kotlinx.coroutines.delay

/**
 * Waits until true time reaches [targetUtcMillis] in steps of at most [maximumStepMillis]. The steps are short so a
 * pause of the uptime clock (which coroutine delays use) is corrected by reading true time again after each one.
 */
internal suspend fun EngineClock.waitUntil(
    targetUtcMillis: Long,
    maximumStepMillis: Long = WindowTimings.HOLD_TICK,
) {
    while (true) {
        val remaining = targetUtcMillis - nowUtcMillis()
        if (remaining <= 0) return
        delay(minOf(remaining, maximumStepMillis))
    }
}

/** Coarse waits to [WindowTimings.COARSE_WAKE_BEFORE_FIRE] before [targetUtcMillis], then 5 ms steps checking the clock. */
internal suspend fun EngineClock.waitPreciselyUntil(targetUtcMillis: Long) {
    waitUntil(targetUtcMillis - WindowTimings.COARSE_WAKE_BEFORE_FIRE)
    while (true) {
        val remaining = targetUtcMillis - nowUtcMillis()
        if (remaining <= 0) return
        delay(minOf(WindowTimings.FINE_WAIT_STEP, remaining))
    }
}
