package app.kestrel.platform

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.ArmedSnipesCopy
import app.kestrel.data.KestrelDatabase
import app.kestrel.data.SettingsStore
import app.kestrel.data.SnipeRepository
import app.kestrel.data.TEST_NOW
import app.kestrel.data.testFormatMoney
import app.kestrel.engine.ClockSync
import app.kestrel.model.EbaySite
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReadinessTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val probes = FakeProbes()
    private val evaluator = ReadinessEvaluator(probes, dayAndTime = { "Sun 21:00" })

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private suspend fun item(key: ReadinessKey): ReadinessItem = evaluator.evaluate().single { it.key == key }

    @Test
    fun aReadyPhoneHasEveryItemOk() =
        runTest {
            val items = evaluator.evaluate()

            assertEquals(ReadinessKey.entries.toSet(), items.map { it.key }.toSet())
            for (item in items) assertTrue("${item.key}: ${item.detail}", item.ok)
            assertEquals("Signed in as ivan_v", item(ReadinessKey.EBAY_SESSION).detail)
            assertEquals("Granted automatically", item(ReadinessKey.EXACT_ALARMS).detail)
            assertEquals("Phone clock 0.3 s behind, corrected (checked 3 min ago)", item(ReadinessKey.CLOCK).detail)
            assertEquals("Connected (Wi-Fi)", item(ReadinessKey.NETWORK).detail)
            assertEquals("Last dry run passed Sun 21:00", item(ReadinessKey.DRY_RUN).detail)
        }

    @Test
    fun eachProblemIsNamedInPlainWords() =
        runTest {
            probes.cookies = setOf()
            probes.notificationsEnabled = false
            probes.exactAlarms = false
            probes.batteryUnrestricted = false
            probes.bucket = UsageStatsManager.STANDBY_BUCKET_RESTRICTED
            probes.hibernation = UnusedAppRestrictionsConstants.API_31
            probes.promoted = false
            probes.network = NetworkReport(connected = false, transport = null, dataSaverOn = false)
            probes.dryRun = DryRunReport(TEST_NOW, passed = false, message = "Sign-in needed")

            val items = evaluator.evaluate()

            for (item in items.filter { it.key != ReadinessKey.CLOCK }) assertFalse("${item.key}", item.ok)
            assertEquals(
                "Not signed in to ebay.co.uk. Sign in so Kestrel can bid.",
                item(ReadinessKey.EBAY_SESSION).detail,
            )
            assertEquals(
                "Notifications are off, so you won't hear when you win or need to act.",
                item(ReadinessKey.NOTIFICATIONS).detail,
            )
            assertEquals("Optimised: snipes may run late while the phone sleeps.", item(ReadinessKey.BATTERY).detail)
            assertEquals("Android has restricted Kestrel in the background.", item(ReadinessKey.BACKGROUND).detail)
            assertEquals(
                "On: Android may pause Kestrel after a few months unused.",
                item(ReadinessKey.UNUSED_APP_PAUSE).detail,
            )
            assertEquals("The final countdown can't show in the status bar.", item(ReadinessKey.LIVE_UPDATES).detail)
            assertEquals("No internet connection.", item(ReadinessKey.NETWORK).detail)
            assertEquals("Last dry run failed Sun 21:00: Sign-in needed", item(ReadinessKey.DRY_RUN).detail)
        }

    @Test
    fun theSessionIsCheckedOnEverySiteInUse() =
        runTest {
            probes.sites = setOf(EbaySite.UK, EbaySite.DE)
            probes.cookies = setOf(EbaySite.UK.host)

            assertEquals(
                "Not signed in to ebay.de. Sign in so Kestrel can bid.",
                item(ReadinessKey.EBAY_SESSION).detail,
            )
        }

    @Test
    fun aBlockedChannelIsNamed() =
        runTest {
            probes.blocked = listOf(NotificationChannels.ATTENTION)

            val notifications = item(ReadinessKey.NOTIFICATIONS)

            assertFalse(notifications.ok)
            assertEquals("\"Needs you\" turned off.", notifications.detail)
        }

    @Test
    fun aRareBucketOrBackgroundRestrictionIsAProblem() =
        runTest {
            probes.bucket = UsageStatsManager.STANDBY_BUCKET_RARE
            assertEquals("Android rarely lets Kestrel run in the background.", item(ReadinessKey.BACKGROUND).detail)

            probes.bucket = UsageStatsManager.STANDBY_BUCKET_WORKING_SET
            probes.backgroundRestricted = true
            assertEquals("Background use is restricted for Kestrel.", item(ReadinessKey.BACKGROUND).detail)
        }

    @Test
    fun clockOffsetsAreDescribedAndLargeOnesFlagged() =
        runTest {
            probes.sync = null
            assertEquals("Checked against time.google.com before each bid", item(ReadinessKey.CLOCK).detail)

            probes.sync =
                ClockSync(
                    offsetMillis = -1_250,
                    roundTripMillis = 40,
                    source = "sntp",
                    atElapsedRealtimeMillis =
                        probes.elapsed - 30_000,
                )
            assertEquals("Phone clock 1.3 s ahead, corrected (checked just now)", item(ReadinessKey.CLOCK).detail)

            probes.sync =
                ClockSync(
                    offsetMillis = 125_000,
                    roundTripMillis = 40,
                    source = "sntp",
                    atElapsedRealtimeMillis =
                        probes.elapsed - 2 * 3_600_000,
                )
            val large = item(ReadinessKey.CLOCK)
            assertFalse(large.ok)
            assertEquals(
                "Phone clock 2 min behind. Kestrel corrects it, but turn on automatic time (checked 2 h ago).",
                large.detail,
            )
        }

    @Test
    fun aLiveChannelTurnedDownToMinCannotShowTheCountdown() =
        runTest {
            probes.liveImportance = NotificationManager.IMPORTANCE_MIN

            assertFalse(item(ReadinessKey.LIVE_UPDATES).ok)
        }

    @Test
    fun dataSaverIsMentionedButNotABlocker() =
        runTest {
            probes.network = NetworkReport(connected = true, transport = "mobile data", dataSaverOn = true)

            val network = item(ReadinessKey.NETWORK)

            assertTrue(network.ok)
            assertEquals("Connected (mobile data). Data Saver may delay background checks.", network.detail)
        }

    @Test
    fun aProbeThatThrowsGivesACouldNotCheckRow() =
        runTest {
            probes.throwOnPromoted = true

            val live = item(ReadinessKey.LIVE_UPDATES)

            assertFalse(live.ok)
            assertEquals("Couldn't check this on this phone.", live.detail)
        }

    @Test
    fun everyFixOpensTheMatchingSettingsScreen() {
        val fixes = ReadinessFixes(context, probes)

        assertNull(fixes.intent(ReadinessKey.EBAY_SESSION))
        assertNull(fixes.intent(ReadinessKey.DRY_RUN))
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, fixes.intent(ReadinessKey.NOTIFICATIONS)?.action)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, fixes.intent(ReadinessKey.EXACT_ALARMS)?.action)
        val battery = fixes.intent(ReadinessKey.BATTERY)!!
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, battery.action)
        assertEquals("package:${context.packageName}", battery.dataString)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, fixes.intent(ReadinessKey.BACKGROUND)?.action)
        assertEquals(
            Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS,
            fixes.intent(ReadinessKey.LIVE_UPDATES)?.action,
        )
        assertEquals(
            context.packageName,
            fixes.intent(ReadinessKey.LIVE_UPDATES)?.getStringExtra(Settings.EXTRA_APP_PACKAGE),
        )
        assertEquals(Settings.ACTION_DATE_SETTINGS, fixes.intent(ReadinessKey.CLOCK)?.action)
        assertEquals(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, fixes.intent(ReadinessKey.NETWORK)?.action)
        assertTrue(fixes.intent(ReadinessKey.UNUSED_APP_PAUSE) != null)
    }

    @Test
    fun aBlockedChannelFixOpensThatChannel() {
        probes.blocked = listOf(NotificationChannels.RESULTS)

        val fix = ReadinessFixes(context, probes).intent(ReadinessKey.NOTIFICATIONS)!!

        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, fix.action)
        assertEquals(NotificationChannels.RESULTS, fix.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test
    fun theAndroidProbesReadThePhonesState() =
        runTest {
            val database = KestrelDatabase.inMemory(context)
            try {
                val repository =
                    SnipeRepository(
                        database,
                        ArmedSnipesCopy(
                            File(context.noBackupFilesDir, "armed-${System.nanoTime()}.json"),
                        ),
                        {
                            TEST_NOW
                        },
                        ::testFormatMoney,
                    )
                val android =
                    AndroidReadinessProbes(
                        context,
                        repository,
                        SettingsStore(
                            PreferenceDataStoreFactory.create(produceFile = {
                                File(context.filesDir, "settings-${System.nanoTime()}.preferences_pb")
                            }),
                        ),
                        { false },
                        { null },
                        { 0L },
                    )
                val notifications = context.getSystemService(NotificationManager::class.java)
                NotificationChannels.create(context)
                notifications.createNotificationChannel(
                    NotificationChannel(NotificationChannels.RESULTS, "Results", NotificationManager.IMPORTANCE_NONE),
                )
                shadowOf(notifications).setNotificationsEnabled(true)
                ShadowAlarmManager.setCanScheduleExactAlarms(false)
                shadowOf(
                    context.getSystemService(PowerManager::class.java),
                ).setIgnoringBatteryOptimizations(context.packageName, true)
                shadowOf(
                    context.getSystemService(UsageStatsManager::class.java),
                ).setCurrentAppStandbyBucket(UsageStatsManager.STANDBY_BUCKET_RARE)
                shadowOf(context.getSystemService(ActivityManager::class.java)).setBackgroundRestricted(true)

                assertTrue(android.notificationsEnabled())
                assertEquals(listOf(NotificationChannels.RESULTS), android.blockedChannels())
                assertFalse(android.canScheduleExactAlarms())
                assertTrue(android.isIgnoringBatteryOptimizations())
                assertEquals(UsageStatsManager.STANDBY_BUCKET_RARE, android.standbyBucket())
                assertTrue(android.isBackgroundRestricted())
                assertTrue(android.sitesInUse().isEmpty())
                assertNull(android.latestDryRun())
            } finally {
                database.close()
            }
        }

    internal class FakeProbes : ReadinessProbes {
        var notificationsEnabled = true
        var blocked: List<String> = emptyList()
        var liveImportance: Int? = NotificationManager.IMPORTANCE_DEFAULT
        var exactAlarms = true
        var batteryUnrestricted = true
        var bucket = UsageStatsManager.STANDBY_BUCKET_ACTIVE
        var backgroundRestricted = false
        var hibernation = UnusedAppRestrictionsConstants.DISABLED
        var promoted = true
        var throwOnPromoted = false
        var cookies = setOf(EbaySite.UK.host)
        var sites: Set<EbaySite> = setOf(EbaySite.UK)
        var name: String? = "ivan_v"
        val elapsed = 50_000_000L
        var sync: ClockSync? =
            ClockSync(
                offsetMillis = 300,
                roundTripMillis = 35,
                source = "sntp",
                atElapsedRealtimeMillis =
                    elapsed - 3 * 60_000,
            )
        var dryRun: DryRunReport? = DryRunReport(TEST_NOW, passed = true, message = "Dry run passed")
        var network = NetworkReport(connected = true, transport = "Wi-Fi", dataSaverOn = false)

        override fun notificationsEnabled() = notificationsEnabled

        override fun blockedChannels() = blocked

        override fun liveChannelImportance() = liveImportance

        override fun canScheduleExactAlarms() = exactAlarms

        override fun isIgnoringBatteryOptimizations() = batteryUnrestricted

        override fun standbyBucket() = bucket

        override fun isBackgroundRestricted() = backgroundRestricted

        override suspend fun unusedAppRestrictionsStatus() = hibernation

        override fun canPostPromotedNotifications(): Boolean {
            if (throwOnPromoted) throw SecurityException("not on this build")
            return promoted
        }

        override fun hasSessionCookies(host: String) = host in cookies

        override suspend fun sitesInUse() = sites

        override suspend fun signedInName() = name

        override fun lastClockSync() = sync

        override fun elapsedRealtimeMillis() = elapsed

        override suspend fun latestDryRun() = dryRun

        override fun network() = network
    }
}
