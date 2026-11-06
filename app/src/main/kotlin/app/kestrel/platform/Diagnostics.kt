package app.kestrel.platform

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager
import androidx.core.content.FileProvider
import app.kestrel.web.HeadlessProbe
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import kotlin.math.min

/**
 * Local diagnostics for "Send a problem report": main-thread stalls, uncaught crashes and Android's own record of how
 * the app's process ended (including the full thread dump of an "isn't responding" kill). Nothing leaves the phone
 * unless the user shares the report.
 */
internal class DiagnosticsLog(
    private val context: Context,
) {
    private val directory: File get() = File(context.noBackupFilesDir, DIRECTORY)

    fun recordStall(
        stalledMillis: Long,
        stack: Array<StackTraceElement>,
    ) = append(STALLS_FILE, "${Instant.now()} main thread stalled $stalledMillis ms\n${frames(stack)}")

    fun recordCrash(
        thread: Thread,
        error: Throwable,
    ) = append(CRASHES_FILE, "${Instant.now()} crash on thread ${thread.name}\n${error.stackTraceToString()}")

    fun read(name: String): String =
        runCatching {
            File(directory, name).takeIf { it.exists() }?.readText()
        }.getOrNull().orEmpty()

    private fun append(
        name: String,
        entry: String,
    ) {
        // Before the first unlock after a reboot, credential-encrypted storage is not available.
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return
        runCatching {
            directory.mkdirs()
            val file = File(directory, name)
            val kept = if (file.exists()) file.readText().takeLast(MAXIMUM_FILE_CHARS - entry.length - 2) else ""
            file.writeText(kept + entry + "\n\n")
        }
    }

    private fun frames(stack: Array<StackTraceElement>): String =
        stack.take(MAXIMUM_FRAMES).joinToString("\n") { "    at $it" }

    companion object {
        const val DIRECTORY = "diagnostics"
        const val STALLS_FILE = "stalls.txt"
        const val CRASHES_FILE = "crashes.txt"
        private const val MAXIMUM_FILE_CHARS = 128_000
        private const val MAXIMUM_FRAMES = 60
    }
}

/**
 * Notices when the main thread stops answering and keeps its stack. A stall is recorded once when it passes
 * [firstStallMillis] and once more if it is still stuck at [secondStallMillis], so a report shows where it was stuck
 * and whether it moved. Uptime does not advance in deep sleep, so sleep never counts as a stall.
 */
internal class MainThreadWatchdog(
    private val log: DiagnosticsLog,
    private val firstStallMillis: Long = 2_000L,
    private val secondStallMillis: Long = 8_000L,
    private val tickMillis: Long = 500L,
) {
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lastBeatUptimeMillis = SystemClock.uptimeMillis()
    private val beat = Runnable { lastBeatUptimeMillis = SystemClock.uptimeMillis() }

    fun start() {
        Thread({ watch() }, "kestrel-watchdog")
            .apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }.start()
    }

    private fun watch() {
        var recordedFirstFor = -1L
        var recordedSecondFor = -1L
        while (true) {
            handler.post(beat)
            try {
                Thread.sleep(tickMillis)
            } catch (_: InterruptedException) {
                return
            }
            val beatAt = lastBeatUptimeMillis
            val stalled = SystemClock.uptimeMillis() - beatAt
            val stack by lazy { Looper.getMainLooper().thread.stackTrace }
            if (stalled >= firstStallMillis && recordedFirstFor != beatAt) {
                recordedFirstFor = beatAt
                log.recordStall(stalled, stack)
            } else if (stalled >= secondStallMillis && recordedSecondFor != beatAt) {
                recordedSecondFor = beatAt
                log.recordStall(stalled, stack)
            }
        }
    }
}

/** Keeps the stack of an uncaught exception before Android's own handler ends the process. */
internal object CrashRecorder {
    fun install(log: DiagnosticsLog) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { log.recordCrash(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }
}

/** Builds the shareable report file and returns its content URI (FileProvider, read-only grant on share). */
internal object ProblemReport {
    private const val REPORTS_DIRECTORY = "reports"
    private const val AUTHORITY_SUFFIX = ".reports"
    private const val EXITS_KEPT = 12
    private const val MAXIMUM_TRACE_CHARS = 400_000

    suspend fun write(
        context: Context,
        versionName: String,
        versionCode: Int,
    ): Uri {
        val log = DiagnosticsLog(context)
        // Measured before the text is built: it takes about three seconds on the main thread's WebView.
        val probe = HeadlessProbe.run(context)
        val snipes = recentSnipes(context)
        val text =
            buildString {
                appendLine("Kestrel problem report")
                appendLine("Written ${Instant.now()}")
                appendLine("App $versionName ($versionCode)")
                appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), build ${Build.ID}")
                appendLine("Device ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine()
                appendLine("== How the app's last processes ended (Android's record) ==")
                appendLine(exits(context))
                appendLine("== Main-thread stalls (Kestrel's watchdog) ==")
                appendLine(log.read(DiagnosticsLog.STALLS_FILE).ifEmpty { "none recorded" })
                appendLine()
                appendLine("== Crashes (Kestrel's record) ==")
                appendLine(log.read(DiagnosticsLog.CRASHES_FILE).ifEmpty { "none recorded" })
                appendLine()
                appendLine("== Hidden-page self-test (a local page, no network) ==")
                appendLine(probe)
                appendLine()
                appendLine("== Recent snipes (timeline, with each step's details) ==")
                appendLine(snipes)
            }
        val directory = File(context.cacheDir, REPORTS_DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val stamp =
            Instant
                .now()
                .toString()
                .replace(":", "")
                .substringBefore('.')
        val file = File(directory, "kestrel-report-$stamp.txt").apply { writeText(text) }
        return FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }

    private const val SNIPES_KEPT = 6
    private const val EVENTS_KEPT = 12
    private const val MAXIMUM_DETAIL_CHARS = 80_000

    private suspend fun recentSnipes(context: Context): String {
        val repository = runCatching { context.kestrelContainer.repository }.getOrNull() ?: return "unavailable"
        val snipes =
            runCatching { repository.observeSnipes().first() }
                .getOrNull()
                ?.sortedByDescending { it.updatedAtUtcMillis }
                ?.take(SNIPES_KEPT)
                .orEmpty()
        if (snipes.isEmpty()) return "none"
        val text = StringBuilder()
        for (snipe in snipes) {
            val events = runCatching { repository.observeTimeline(snipe.id).first() }.getOrNull().orEmpty()
            text.appendLine(
                "# ${snipe.title.take(60)} (item ${snipe.ref.itemId} on ${snipe.ref.site.host}), ${snipe.status}, " +
                    "ends ${Instant.ofEpochMilli(snipe.endTimeUtcMillis)}",
            )
            for (event in events.takeLast(EVENTS_KEPT)) {
                text.appendLine("${Instant.ofEpochMilli(event.atUtcMillis)} ${event.kind}: ${event.message}")
                event.detail?.let { text.appendLine("    detail: ${it.take(MAXIMUM_DETAIL_CHARS)}") }
            }
            text.appendLine()
        }
        return text.toString()
    }

    private fun exits(context: Context): String {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return "unavailable"
        val exits =
            runCatching {
                manager.getHistoricalProcessExitReasons(
                    context.packageName,
                    0,
                    EXITS_KEPT,
                )
            }.getOrNull()
        if (exits.isNullOrEmpty()) return "none recorded"
        return exits.joinToString("\n") { info ->
            buildString {
                appendLine(
                    "${Instant.ofEpochMilli(info.timestamp)} ${reasonName(info.reason)} " +
                        "(importance ${info.importance}, pss ${info.pss} kB, rss ${info.rss} kB)",
                )
                info.description?.let { appendLine("  description: $it") }
                if (info.reason == ApplicationExitInfo.REASON_ANR) {
                    val trace =
                        runCatching { info.traceInputStream?.bufferedReader()?.use { it.readText() } }
                            .getOrNull()
                    if (trace != null) {
                        appendLine("  --- thread dump ---")
                        appendLine(trace.substring(0, min(trace.length, MAXIMUM_TRACE_CHARS)))
                    }
                }
            }
        }
    }

    private fun reasonName(reason: Int): String =
        when (reason) {
            ApplicationExitInfo.REASON_ANR -> "ANR (isn't responding)"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            else -> "reason $reason"
        }
}
