package app.kestrel.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import app.kestrel.AppContainer
import app.kestrel.KestrelApp
import app.kestrel.data.HOUR
import app.kestrel.data.MINUTE
import app.kestrel.data.TEST_NOW
import app.kestrel.data.testFormatMoney
import app.kestrel.engine.EngineClock
import app.kestrel.engine.NeedsUserReason
import app.kestrel.engine.SnipeNotifier
import app.kestrel.engine.SnipeStore
import app.kestrel.engine.WindowPhase
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeResult
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.CompletableDeferred
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlarmManager
import java.time.ZoneOffset

/** True time and elapsedRealtime that tests move by hand. */
class FakeClock(
    var now: Long = TEST_NOW,
    var elapsed: Long = 9_000_000L,
) : EngineClock {
    override fun nowUtcMillis(): Long = now

    override fun elapsedRealtimeMillis(): Long = elapsed
}

/** Window at T-120 s, backup at T-75 s, dry run at T-10 min, checks on the cadence in docs/ARCHITECTURE.md. */
internal class FakePolicy(
    var nextCheck: (now: Long, end: Long, lastCheck: Long?) -> Long? = ::architectureCadence,
) : SchedulePolicy {
    override fun nextCheckAt(
        nowUtcMillis: Long,
        endUtcMillis: Long,
        lastCheckUtcMillis: Long?,
    ): Long? = nextCheck(nowUtcMillis, endUtcMillis, lastCheckUtcMillis)

    override fun dryRunAt(endUtcMillis: Long): Long = endUtcMillis - 10 * MINUTE

    override fun windowOpensAt(endUtcMillis: Long): Long = endUtcMillis - 120_000

    override fun backupWindowAt(endUtcMillis: Long): Long = endUtcMillis - 75_000

    companion object {
        fun architectureCadence(
            now: Long,
            end: Long,
            lastCheck: Long?,
        ): Long? {
            val left = end - now
            val interval =
                when {
                    left > 24 * HOUR -> 6 * HOUR
                    left > 6 * HOUR -> 2 * HOUR
                    left > HOUR -> HOUR
                    left > 10 * MINUTE -> 15 * MINUTE
                    else -> return null
                }
            return (lastCheck ?: now) + interval
        }
    }
}

class RecordingNotifier : SnipeNotifier {
    val calls = mutableListOf<String>()
    val needsUser = mutableListOf<Pair<NeedsUserReason, String?>>()

    override fun windowOpened(
        snipe: Snipe,
        bidAtUtcMillis: Long,
    ) {
        calls += "windowOpened ${snipe.id}"
    }

    override fun windowProgress(
        snipe: Snipe,
        phase: WindowPhase,
        detail: String?,
    ) {
        calls += "windowProgress ${snipe.id} $phase"
    }

    override fun windowClosed(snipeId: Long) {
        calls += "windowClosed $snipeId"
    }

    override fun priceOverMax(
        snipe: Snipe,
        current: Money,
        minimumNext: Money?,
    ) {
        calls += "priceOverMax ${snipe.id}"
    }

    override fun needsUser(
        snipe: Snipe,
        reason: NeedsUserReason,
        detail: String?,
    ) {
        calls += "needsUser ${snipe.id} $reason"
        needsUser += reason to detail
    }

    override fun result(
        snipe: Snipe,
        result: SnipeResult,
    ) {
        calls += "result ${snipe.id} ${result.outcome}"
    }
}

/** A SnipeStore over a map, for jobs that only need the engine's view of the data. */
class FakeStore(
    vararg snipes: Snipe,
) : SnipeStore {
    val snipes = snipes.associateBy { it.id }.toMutableMap()
    val events = mutableListOf<Triple<Long, TimelineKind, String>>()

    override suspend fun snipe(id: Long): Snipe? = snipes[id]

    override suspend fun update(snipe: Snipe) {
        snipes[snipe.id] = snipe
    }

    override suspend fun addEvent(
        snipeId: Long,
        kind: TimelineKind,
        message: String,
        detail: String?,
    ) {
        events += Triple(snipeId, kind, message)
    }

    override suspend fun recordPrice(
        snipeId: Long,
        atUtcMillis: Long,
        price: Money,
        bidCount: Int,
    ) = Unit

    override suspend fun group(id: Long): SnipeGroup? = null

    override suspend fun groupMembers(groupId: Long): List<Snipe> = emptyList()
}

/** Records every job and, when [holdJobs] is set, keeps each one running until [release] is called. */
internal class FakeJobRunner(
    var holdJobs: Boolean = false,
) : SnipeJobRunner {
    val runs = mutableListOf<Pair<JobKind, Long>>()
    private val gates = mutableMapOf<Pair<JobKind, Long>, CompletableDeferred<Unit>>()
    var failure: Exception? = null

    override suspend fun run(
        kind: JobKind,
        snipeId: Long,
    ) {
        runs += kind to snipeId
        failure?.let { throw it }
        if (holdJobs) gates.getOrPut(kind to snipeId) { CompletableDeferred() }.await()
    }

    fun release(
        kind: JobKind,
        snipeId: Long,
    ) {
        gates.getOrPut(kind to snipeId) { CompletableDeferred() }.complete(Unit)
    }
}

internal class FakeWakeLocks : WakeLocks {
    val acquired = mutableListOf<Pair<String, Long>>()
    var held = 0

    override fun acquire(
        tag: String,
        timeoutMillis: Long,
    ): HeldWakeLock {
        acquired += tag to timeoutMillis
        held++
        var released = false
        return HeldWakeLock {
            if (!released) {
                released = true
                held--
            }
        }
    }
}

internal class FakePhotos(
    var photo: Bitmap? = null,
    var cachedPhoto: Bitmap? = null,
) : ItemPhotos {
    override fun cached(url: String?): Bitmap? = if (url == null) null else cachedPhoto

    // Like the real cache, a loaded photo is kept in memory afterwards.
    override suspend fun load(url: String): Bitmap? = photo?.also { cachedPhoto = it }
}

internal fun testTexts(): NotificationTexts =
    NotificationTexts(
        formatMoney = ::testFormatMoney,
        zone = { ZoneOffset.UTC },
        use24HourClock = { true },
    )

/** The app with fakes for the engine, the timing policy and the clock, for tests that start real components. */
class TestKestrelApp : KestrelApp() {
    val testContainer: TestContainer get() = container as TestContainer

    override fun createContainer(): AppContainer = TestContainer(this)
}

class TestContainer(
    context: Context,
) : AppContainer(context) {
    val fakeClock = FakeClock()
    internal val fakeRunner = FakeJobRunner()
    internal val fakeWakeLocks = FakeWakeLocks()

    override val clock: EngineClock get() = fakeClock
    override val formatMoney: (Money) -> String = ::testFormatMoney
    override val parseMoney: (
        String,
        String,
    ) -> Money? = { text, currency -> text.toBigDecimalOrNull()?.let { Money.of(it, currency) } }
    override val minimumNextBid: (Snipe) -> Money? = { null }
    override val notificationTexts: NotificationTexts = testTexts()
    override val schedulePolicy: SchedulePolicy = FakePolicy()
    override val jobRunner: SnipeJobRunner get() = fakeRunner
    override val wakeLocks: WakeLocks get() = fakeWakeLocks
    var cookieFlushes = 0
    override val flushCookies: suspend () -> Unit = { cookieFlushes++ }
    val windowsKeptAlarmed = mutableListOf<Long>()
    override val keepWindowAlarmed: suspend (Long) -> Unit = { windowsKeptAlarmed += it }
    override val lastExitNote = LastExitNote({ null }, { emptyList() }, { _, _ -> }, { 0L }, {})
}

/** The intent an alarm will deliver. Robolectric 4.17 exposes the alarm's PendingIntent only as a deprecated field. */
@Suppress("DEPRECATION")
internal val ShadowAlarmManager.ScheduledAlarm.savedIntent: Intent
    get() = Shadows.shadowOf(operation).savedIntent
