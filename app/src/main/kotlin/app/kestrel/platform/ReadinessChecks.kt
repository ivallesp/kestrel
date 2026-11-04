package app.kestrel.platform

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE
import android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import app.kestrel.data.SettingsStore
import app.kestrel.data.SnipeRepository
import app.kestrel.engine.ClockSync
import app.kestrel.model.EbaySite
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal data class DryRunReport(
    val atUtcMillis: Long,
    val passed: Boolean,
    val message: String?,
)

internal data class NetworkReport(
    val connected: Boolean,
    /** "Wi-Fi", "mobile data" or similar; null when unknown. */
    val transport: String?,
    val dataSaverOn: Boolean,
)

/** Everything the readiness checklist reads from the phone, behind one seam so the wording is testable. */
internal interface ReadinessProbes {
    fun notificationsEnabled(): Boolean

    /** Channel ids whose importance the user set to none. */
    fun blockedChannels(): List<String>

    fun liveChannelImportance(): Int?

    fun canScheduleExactAlarms(): Boolean

    fun isIgnoringBatteryOptimizations(): Boolean

    fun standbyBucket(): Int

    fun isBackgroundRestricted(): Boolean

    /** One of androidx UnusedAppRestrictionsConstants. */
    suspend fun unusedAppRestrictionsStatus(): Int

    fun canPostPromotedNotifications(): Boolean

    fun hasSessionCookies(host: String): Boolean

    suspend fun sitesInUse(): Set<EbaySite>

    suspend fun signedInName(): String?

    fun lastClockSync(): ClockSync?

    fun elapsedRealtimeMillis(): Long

    suspend fun latestDryRun(): DryRunReport?

    fun network(): NetworkReport
}

internal class AndroidReadinessProbes(
    private val context: Context,
    private val repository: SnipeRepository,
    private val settings: SettingsStore,
    private val sessionCookies: (host: String) -> Boolean,
    private val clockSync: () -> ClockSync?,
    private val elapsedRealtime: () -> Long,
) : ReadinessProbes {
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    override fun notificationsEnabled(): Boolean = notifications?.areNotificationsEnabled() == true

    override fun blockedChannels(): List<String> =
        NotificationChannels.essential.filter { id ->
            notifications?.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
        }

    override fun liveChannelImportance(): Int? =
        notifications?.getNotificationChannel(NotificationChannels.LIVE)?.importance

    override fun canScheduleExactAlarms(): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    override fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    override fun standbyBucket(): Int =
        context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket
            ?: UsageStatsManager.STANDBY_BUCKET_ACTIVE

    override fun isBackgroundRestricted(): Boolean =
        context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

    override suspend fun unusedAppRestrictionsStatus(): Int =
        withContext(Dispatchers.IO) {
            PackageManagerCompat.getUnusedAppRestrictionsStatus(context).get(STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

    override fun canPostPromotedNotifications(): Boolean = notifications?.canPostPromotedNotifications() == true

    override fun hasSessionCookies(host: String): Boolean = sessionCookies(host)

    // Watching needs no session: only a snipe that will bid has to be signed in on its site.
    override suspend fun sitesInUse(): Set<EbaySite> =
        repository
            .pendingSnipes()
            .filter { it.maxBid != null }
            .map { it.ref.site }
            .toSet()

    override suspend fun signedInName(): String? = settings.current().signedInName

    override fun lastClockSync(): ClockSync? = clockSync()

    override fun elapsedRealtimeMillis(): Long = elapsedRealtime()

    override suspend fun latestDryRun(): DryRunReport? {
        val snipe = repository.latestDryRun() ?: return null
        val at = snipe.dryRunAtUtcMillis ?: return null
        return DryRunReport(
            at,
            snipe.dryRunPassed == true,
            repository.latestEvent(snipe.id, TimelineKind.DRY_RUN)?.message,
        )
    }

    override fun network(): NetworkReport {
        val connectivity =
            context.getSystemService(ConnectivityManager::class.java) ?: return NetworkReport(false, null, false)
        val capabilities = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val connected =
            capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val transport =
            when {
                capabilities == null -> null
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> null
            }
        val dataSaver = connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        return NetworkReport(connected, transport, dataSaver)
    }

    private companion object {
        const val STATUS_TIMEOUT_SECONDS = 5L
    }
}

/** Why Live Updates fail when the channel is fine: Android does not let the countdown show in the status bar. */
internal const val LIVE_UPDATES_NOT_PROMOTED = "The final countdown can't show in the status bar."

/** Turns probe readings into the checklist, in plain words. A probe that throws gives a "Couldn't check" row. */
internal class ReadinessEvaluator(
    private val probes: ReadinessProbes,
    private val dayAndTime: (Long) -> String,
) {
    suspend fun evaluate(): List<ReadinessItem> =
        listOf(
            item(ReadinessKey.EBAY_SESSION) { session() },
            item(ReadinessKey.NOTIFICATIONS) { notifications() },
            item(ReadinessKey.EXACT_ALARMS) { exactAlarms() },
            item(ReadinessKey.BATTERY) { battery() },
            item(ReadinessKey.BACKGROUND) { background() },
            item(ReadinessKey.UNUSED_APP_PAUSE) { unusedAppPause() },
            item(ReadinessKey.LIVE_UPDATES) { liveUpdates() },
            item(ReadinessKey.CLOCK) { clock() },
            item(ReadinessKey.NETWORK) { network() },
            item(ReadinessKey.DRY_RUN) { dryRun() },
        )

    private suspend fun item(
        key: ReadinessKey,
        check: suspend () -> Pair<Boolean, String>,
    ): ReadinessItem =
        try {
            val (ok, detail) = check()
            ReadinessItem(key, ok, detail)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Readiness check $key failed", error)
            ReadinessItem(key, ok = false, detail = "Couldn't check this on this phone.")
        }

    private suspend fun session(): Pair<Boolean, String> {
        val sites = probes.sitesInUse().ifEmpty { setOf(EbaySite.UK) }.sortedBy { it.ordinal }
        val missing = sites.filterNot { probes.hasSessionCookies(it.host) }
        if (missing.isNotEmpty()) return false to "Not signed in to ${domains(missing)}. Sign in so Kestrel can bid."
        val signedIn = probes.signedInName()?.let { "Signed in as $it" } ?: "Signed in to ${domains(sites)}"
        return true to signedIn
    }

    private fun domains(sites: List<EbaySite>): String = sites.joinToString(" and ") { it.host.removePrefix("www.") }

    private fun notifications(): Pair<Boolean, String> {
        if (!probes.notificationsEnabled()) {
            return false to "Notifications are off, so you won't hear when you win or need to act."
        }
        val blocked = probes.blockedChannels().map { "\"${NotificationChannels.displayName(it)}\"" }
        if (blocked.isNotEmpty()) return false to "${blocked.joinToString(" and ")} turned off."
        return true to "Results and alerts allowed"
    }

    private fun exactAlarms(): Pair<Boolean, String> =
        if (probes.canScheduleExactAlarms()) {
            true to "Granted automatically"
        } else {
            false to "Exact alarms are off, so bids may be late."
        }

    private fun battery(): Pair<Boolean, String> =
        if (probes.isIgnoringBatteryOptimizations()) {
            true to "Unrestricted: snipes run on time while the phone sleeps"
        } else {
            false to "Optimised: snipes may run late while the phone sleeps."
        }

    private fun background(): Pair<Boolean, String> {
        val bucket = probes.standbyBucket()
        val problem =
            when {
                probes.isBackgroundRestricted() -> "Background use is restricted for Kestrel."
                bucket >= STANDBY_BUCKET_RESTRICTED -> "Android has restricted Kestrel in the background."
                bucket >= STANDBY_BUCKET_RARE -> "Android rarely lets Kestrel run in the background."
                else -> null
            }
        return if (problem == null) true to "Not restricted" else false to problem
    }

    private suspend fun unusedAppPause(): Pair<Boolean, String> =
        when (probes.unusedAppRestrictionsStatus()) {
            UnusedAppRestrictionsConstants.DISABLED -> true to "Off: Kestrel stays armed even if you don't open it"
            UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE -> true to "Not used on this phone"
            UnusedAppRestrictionsConstants.ERROR -> false to "Couldn't check \"Pause app activity if unused\"."
            else -> false to "On: Android may pause Kestrel after a few months unused."
        }

    private fun liveUpdates(): Pair<Boolean, String> {
        val importance = probes.liveChannelImportance()
        if (importance != null && importance <= NotificationManager.IMPORTANCE_MIN) {
            val channel = NotificationChannels.displayName(NotificationChannels.LIVE)
            return false to "The \"$channel\" channel is turned down too far."
        }
        return if (probes.canPostPromotedNotifications()) {
            true to "Allowed in the status bar"
        } else {
            false to LIVE_UPDATES_NOT_PROMOTED
        }
    }

    private fun clock(): Pair<Boolean, String> {
        val sync = probes.lastClockSync() ?: return true to "Checked against time.google.com before each bid"
        val age = describeAge(probes.elapsedRealtimeMillis() - sync.atElapsedRealtimeMillis)
        val offset = describeOffset(sync.offsetMillis)
        return if (abs(sync.offsetMillis) <= LARGE_OFFSET_MILLIS) {
            true to "$offset, corrected ($age)"
        } else {
            false to "$offset. Kestrel corrects it, but turn on automatic time ($age)."
        }
    }

    private fun network(): Pair<Boolean, String> {
        val report = probes.network()
        if (!report.connected) return false to "No internet connection."
        val connection = report.transport?.let { "Connected ($it)" } ?: "Connected"
        return true to if (report.dataSaverOn) "$connection. Data Saver may delay background checks." else connection
    }

    private suspend fun dryRun(): Pair<Boolean, String> {
        val report = probes.latestDryRun() ?: return true to "No dry run yet"
        val at = dayAndTime(report.atUtcMillis)
        return if (report.passed) {
            true to "Last dry run passed $at"
        } else {
            false to ("Last dry run failed $at" + (report.message?.let { ": $it" } ?: "."))
        }
    }

    private fun describeOffset(offsetMillis: Long): String {
        val size = abs(offsetMillis)
        if (size < MILLIS_PER_TENTH) return "Phone clock is right"
        val seconds = String.format(Locale.UK, "%.1f s", size / 1000.0)
        val amount = if (size < MILLIS_PER_MINUTE) seconds else "${size / MILLIS_PER_MINUTE} min"
        // The offset is true time minus phone time, so a positive one means the phone is behind.
        return if (offsetMillis > 0) "Phone clock $amount behind" else "Phone clock $amount ahead"
    }

    private fun describeAge(ageMillis: Long): String =
        when {
            ageMillis < MILLIS_PER_MINUTE -> "checked just now"
            ageMillis < MILLIS_PER_HOUR -> "checked ${ageMillis / MILLIS_PER_MINUTE} min ago"
            else -> "checked ${ageMillis / MILLIS_PER_HOUR} h ago"
        }

    private companion object {
        const val TAG = "Readiness"
        const val MILLIS_PER_TENTH = 100L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
        const val LARGE_OFFSET_MILLIS = 30_000L
    }
}

/** The settings screen that fixes each item, or null when the fix is inside the app (sign-in, dry run). */
internal class ReadinessFixes(
    private val context: Context,
    private val probes: ReadinessProbes,
) {
    private val packageUri: Uri get() = Uri.fromParts("package", context.packageName, null)

    fun intent(key: ReadinessKey): Intent? =
        when (key) {
            ReadinessKey.EBAY_SESSION, ReadinessKey.DRY_RUN -> {
                null
            }

            ReadinessKey.NOTIFICATIONS -> {
                notificationSettings()
            }

            ReadinessKey.EXACT_ALARMS -> {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            }

            ReadinessKey.BATTERY -> {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
            }

            ReadinessKey.BACKGROUND -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            }

            ReadinessKey.UNUSED_APP_PAUSE -> {
                unusedAppSettings()
            }

            ReadinessKey.LIVE_UPDATES -> {
                liveUpdatesSettings()
            }

            ReadinessKey.CLOCK -> {
                Intent(Settings.ACTION_DATE_SETTINGS)
            }

            ReadinessKey.NETWORK -> {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            }
        }

    private fun notificationSettings(): Intent {
        val blocked =
            try {
                if (probes.notificationsEnabled()) probes.blockedChannels().firstOrNull() else null
            } catch (error: RuntimeException) {
                null
            }
        return if (blocked != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, blocked)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    /**
     * A countdown channel turned down to Minimise (or off) is fixed on that channel's page; the Live Updates page can
     * only allow promotion, which is not what is wrong then.
     */
    private fun liveUpdatesSettings(): Intent {
        val importance =
            try {
                probes.liveChannelImportance()
            } catch (error: RuntimeException) {
                null
            }
        return if (importance != null && importance <= NotificationManager.IMPORTANCE_MIN) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.LIVE)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    /** Android documents this one for startActivityForResult. */
    private fun unusedAppSettings(): Intent =
        try {
            IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
        } catch (error: UnsupportedOperationException) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
}
