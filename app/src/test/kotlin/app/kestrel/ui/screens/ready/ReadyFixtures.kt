package app.kestrel.ui.screens.ready

import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import androidx.core.content.UnusedAppRestrictionsConstants
import app.kestrel.engine.ClockSources
import app.kestrel.engine.ClockSync
import app.kestrel.model.EbaySite
import app.kestrel.model.Snipe
import app.kestrel.platform.DryRunReport
import app.kestrel.platform.NetworkReport
import app.kestrel.platform.ReadinessEvaluator
import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessProbes
import app.kestrel.ui.fixture.CatalogueFixture
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The phone as S5 reads it, through the platform's own ReadinessEvaluator, so every row starts from the words and
 * verdicts the app really produces. The defaults are DESIGN Appendix A: battery restricted, Live Updates off,
 * everything else passing; S5 renders at 18:13:52, after the 18:11:40 dry run.
 */
internal data class FakePhone(
    val notificationsOn: Boolean = true,
    val blockedChannels: List<String> = emptyList(),
    val exactAlarms: Boolean = true,
    val batteryUnrestricted: Boolean = false,
    val promotedNotifications: Boolean = false,
    val signedIn: Boolean = true,
    val online: Boolean = true,
    val dryRunPassed: Boolean? = true,
    val clockOffsetMillis: Long = 40,
) {
    fun probes(): ReadinessProbes = Probes(this)

    fun items(): List<ReadinessItem> =
        runBlocking { ReadinessEvaluator(probes(), ReadyFixtures::dayAndTime).evaluate() }

    private class Probes(
        private val phone: FakePhone,
    ) : ReadinessProbes {
        override fun notificationsEnabled(): Boolean = phone.notificationsOn

        override fun blockedChannels(): List<String> = phone.blockedChannels

        override fun liveChannelImportance(): Int = NotificationManager.IMPORTANCE_DEFAULT

        override fun canScheduleExactAlarms(): Boolean = phone.exactAlarms

        override fun isIgnoringBatteryOptimizations(): Boolean = phone.batteryUnrestricted

        override fun standbyBucket(): Int = UsageStatsManager.STANDBY_BUCKET_ACTIVE

        override fun isBackgroundRestricted(): Boolean = false

        override suspend fun unusedAppRestrictionsStatus(): Int = UnusedAppRestrictionsConstants.DISABLED

        override fun canPostPromotedNotifications(): Boolean = phone.promotedNotifications

        override fun hasSessionCookies(host: String): Boolean = phone.signedIn

        override suspend fun sitesInUse(): Set<EbaySite> = setOf(EbaySite.UK)

        override suspend fun signedInName(): String? = if (phone.signedIn) CatalogueFixture.ACCOUNT else null

        override fun lastClockSync(): ClockSync =
            ClockSync(
                phone.clockOffsetMillis,
                roundTripMillis = 31,
                source = ClockSources.SNTP,
                atElapsedRealtimeMillis = 0,
            )

        override fun elapsedRealtimeMillis(): Long = ReadyFixtures.CLOCK_AGE_MILLIS

        override suspend fun latestDryRun(): DryRunReport? =
            phone.dryRunPassed?.let { passed ->
                DryRunReport(
                    atUtcMillis = ReadyFixtures.dryRunAt.toEpochMilli(),
                    passed = passed,
                    message =
                        if (passed) {
                            "Dry run passed: eBay's bid layer opened and took your max £120.00. No bid was placed."
                        } else {
                            "Dry run failed: eBay asked you to sign in before bidding. No bid was placed."
                        },
                )
            }

        override fun network(): NetworkReport =
            NetworkReport(connected = phone.online, transport = "Wi-Fi", dataSaverOn = false)
    }
}

internal object ReadyFixtures {
    private val fixture = CatalogueFixture

    /** S5 renders at 18:13:52 (DESIGN MF31), after the 18:11:40 dry run. */
    val now: Instant = fixture.readyScreenNow
    val dryRunAt: Instant = fixture.at("2025-11-09T18:11:40.200Z")

    /** The clock was synced a minute before the check. */
    const val CLOCK_AGE_MILLIS = 60_000L

    fun dayAndTime(utcMillis: Long): String =
        DateTimeFormatter
            .ofPattern("EEE HH:mm", Locale.UK)
            .withZone(ZoneId.of("Europe/London"))
            .format(Instant.ofEpochMilli(utcMillis))

    fun facts(
        phone: FakePhone,
        checkedAt: Instant = now,
        dryRunSnipe: Snipe = fixture.canon,
    ): CheckFacts =
        CheckFacts(
            checkedAt = checkedAt,
            signedInName = if (phone.signedIn) fixture.ACCOUNT else null,
            clock =
                ClockReading(
                    phone.clockOffsetMillis,
                    ClockReference.Google,
                    checkedAt.minusMillis(CLOCK_AGE_MILLIS),
                ),
            latestDryRun =
                phone.dryRunPassed?.let {
                    DryRunReading(
                        dryRunSnipe.id,
                        dryRunSnipe.title,
                        dryRunAt,
                        it,
                    )
                },
            alertsInDoNotDisturb = false,
        )

    /** S5 as the ViewModel would build it for [phone] and [snipes]. */
    fun state(
        phone: FakePhone = FakePhone(),
        snipes: List<Snipe> = fixture.snipes,
        now: Instant = this.now,
        checking: Boolean = false,
    ): ReadyUiState {
        val nextBid = CheckRows.nextBid(snipes, now)
        return ReadyUiState(
            now = now,
            checking = checking,
            checkedAt = now,
            rows = CheckRows.build(phone.items(), facts(phone, now), nextBid, now),
            nextBid = nextBid,
        )
    }

    /** The first check has not finished yet. */
    val checking: ReadyUiState
        get() = ReadyUiState(now = now, checking = true, checkedAt = null, rows = emptyList(), nextBid = null)

    /** Nothing armed: only finished lots and one that is only watched. */
    val notArmedSnipes: List<Snipe>
        get() = listOf(fixture.polaroid, fixture.gameAndWatch, fixture.nintendo64)
}
