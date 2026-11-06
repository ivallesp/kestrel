package app.kestrel.ui.screens.home

import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeStatus
import app.kestrel.model.StatusFamily
import app.kestrel.model.TimelineEvent
import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.GroupContext
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.LotStatusMapper
import app.kestrel.ui.model.LotTimes
import app.kestrel.ui.model.action
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The latest readiness checks: [items] is null until the first check answers; [checking] while one runs. */
data class ReadinessSnapshot(
    val items: List<ReadinessItem>?,
    val checkedAt: Instant?,
    val checking: Boolean,
) {
    companion object {
        val NotChecked = ReadinessSnapshot(items = null, checkedAt = null, checking = false)
    }
}

/**
 * What S1 is built from. [snipes] are every snipe the user has not deleted, soonest end first, as the repository
 * returns them; [timelines] hold each shown snipe's events. [photoOf] picks the plate treatment (the app has only
 * cutouts; the fixture knows its scene photos).
 */
data class HomeInput(
    val now: Instant,
    val snipes: List<Snipe>,
    val timelines: Map<Long, List<TimelineEvent>>,
    val groups: List<SnipeGroup>,
    val connectivity: Connectivity,
    val readiness: ReadinessSnapshot,
    val accountName: String?,
    val defaultLeadSeconds: Int,
    val refreshing: Boolean = false,
    val photoOf: (Snipe) -> LotPhoto = LotPhoto::of,
)

/**
 * Builds S1 from the domain (DESIGN S1, 3.4 to 3.8). Pure, so every bucket, hero and pill rule is unit tested and
 * every screenshot runs the same code as the phone.
 *
 * - Buckets, in Europe/London: Ending soon (ends within 24 h), This week (within the next 7 calendar days), Later,
 *   and Done (finished in the last 30 days, newest first). Rows keep end-time order inside a bucket.
 * - The hero is the earliest-ending lot that will bid (it has a max) and is Armed, Bidding or Attention. It opens
 *   its bucket.
 * - A group gets its own block, opened by its header, only when all its open lots sit next to each other in one
 *   bucket and none is the hero; otherwise each of its rows carries the group flag.
 * - When the platform finds no eBay session, armed lots read "Sign-in needed · paused": no bid can go out.
 */
internal object HomeStateBuilder {
    /** Finished lots stay on S1 this long; older results live only in History. */
    val DONE_FOR: Duration = Duration.ofDays(30)

    /** Seconds tick (and the zone shows) for a lot this close to its end. */
    private val ONE_HOUR: Duration = Duration.ofHours(1)

    private val ENDING_SOON: Duration = Duration.ofHours(24)

    private const val THIS_WEEK_DAYS = 7L

    private val heroFamilies = setOf(StatusFamily.ARMED, StatusFamily.BIDDING, StatusFamily.ATTENTION)

    /** Row actions S1 shows; "Edit max" lives on S3 and finished rows have none (DESIGN 3.5). */
    private val rowActions =
        setOf(
            LotAction.RAISE_MAX,
            LotAction.SET_MAX,
            LotAction.SIGN_IN,
            LotAction.OPEN_EBAY_PAGE,
            LotAction.FIX,
            LotAction.OPEN_ON_EBAY,
        )

    fun build(input: HomeInput): HomeUiState {
        val now = input.now
        val offline = input.connectivity as? Connectivity.Offline
        val sessionLost = input.readiness.items?.any { it.key == ReadinessKey.EBAY_SESSION && !it.ok } == true
        val lots = input.snipes.map { lotOf(it, input, sessionLost, offline) }
        val (finished, open) = lots.partition { it.status.kind.isFinal }
        val pill =
            ReadinessPills.of(
                items = input.readiness.items,
                checkedAt = input.readiness.checkedAt,
                checking = input.readiness.checking,
                offline = offline != null,
                offlineBidAt = if (offline != null) earliestBidWithinTheHour(open, now) else null,
                lotsNeedSignIn = open.any { it.status is LotStatus.SignInNeeded },
                now = now,
            )
        val pillOffersSignIn = pill.action == PillAction.SignIn
        val openLots = open.map { it.copy(action = rowAction(it.status, now, pillOffersSignIn)) }
        val sections = openSections(openLots, input) + listOfNotNull(doneSection(finished, now))
        return HomeUiState(
            now = now,
            loaded = true,
            accountName = input.accountName,
            sections = sections,
            pill = pill,
            offline = offline?.let { OfflineNotice(it.sinceUtcMillis?.let(Instant::ofEpochMilli)) },
            showFinishedLink = finished.isNotEmpty(),
            refreshing = input.refreshing,
            defaultLeadSeconds = input.defaultLeadSeconds,
        )
    }

    /** True while any open lot is within the hour of its end, so the screen ticks every second. */
    fun ticksEverySecond(
        snipes: List<Snipe>,
        now: Instant,
    ): Boolean = snipes.any { !it.status.isFinal && it.endTimeUtcMillis - now.toEpochMilli() < ONE_HOUR.toMillis() }

    /** The bucket of an open lot ending at [end]. An end that has just passed stays in Ending soon. */
    fun bucketOf(
        end: Instant,
        now: Instant,
        zone: ZoneId = AuctionZone,
    ): HomeBucket {
        if (Duration.between(now, end) < ENDING_SOON) return HomeBucket.EndingSoon
        val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), end.atZone(zone).toLocalDate())
        return if (days <= THIS_WEEK_DAYS) HomeBucket.ThisWeek else HomeBucket.Later
    }

    /** The lot the hero shows, or null when no lot will bid. */
    fun heroOf(open: List<HomeLot>): HomeLot? =
        open
            .filter { it.snipe.maxBid != null && it.status.family in heroFamilies }
            .minWithOrNull(compareBy({ it.snipe.endTimeUtcMillis }, { it.id }))

    /** When a finished lot finished: its end, or the cancel when that came first. */
    fun finishedAt(lot: HomeLot): Instant {
        val end = Instant.ofEpochMilli(lot.snipe.endTimeUtcMillis)
        val cancelled = (lot.status as? LotStatus.CancelledByYou)?.cancelledAt
        return if (cancelled != null && cancelled < end) cancelled else end
    }

    private fun lotOf(
        snipe: Snipe,
        input: HomeInput,
        sessionLost: Boolean,
        offline: Connectivity.Offline?,
    ): HomeLot {
        val events = input.timelines[snipe.id].orEmpty()
        val mapped = LotStatusMapper.statusOf(snipe, events, input.now, input.connectivity, groupContext(snipe, input))
        // Without a session no bid can go out, whatever the last check said.
        val status = if (sessionLost && mapped is LotStatus.Armed) LotStatus.SignInNeeded(mapped.bidAt) else mapped
        val open = !status.kind.isFinal
        return HomeLot(
            snipe = snipe,
            status = status,
            photo = input.photoOf(snipe),
            failedCheckAt = if (open) LotStatusMapper.failedCheckAt(snipe, events) else null,
            pricesAsOf = if (open) offline?.sinceUtcMillis?.let(Instant::ofEpochMilli) else null,
        )
    }

    private fun groupContext(
        snipe: Snipe,
        input: HomeInput,
    ): GroupContext? {
        val groupId = snipe.groupId ?: return null
        val group = input.groups.firstOrNull { it.id == groupId } ?: return null
        val won =
            input.snipes.firstOrNull {
                it.groupId == groupId && it.id != snipe.id && it.status == SnipeStatus.WON
            }
        return GroupContext(group.name, won?.title)
    }

    private fun rowAction(
        status: LotStatus,
        now: Instant,
        pillOffersSignIn: Boolean,
    ): LotAction? {
        val action = status.action(now)?.takeIf { it in rowActions } ?: return null
        // When the whole account is signed out the dock says so once, with its own "Sign in".
        return if (action == LotAction.SIGN_IN && pillOffersSignIn) null else action
    }

    private fun earliestBidWithinTheHour(
        open: List<HomeLot>,
        now: Instant,
    ): Instant? =
        open
            .filter { it.snipe.maxBid != null && it.status.family in heroFamilies }
            .map { LotTimes.bidAt(it.snipe) }
            .filter { it > now && Duration.between(now, it) <= ONE_HOUR }
            .minOrNull()

    private fun openSections(
        open: List<HomeLot>,
        input: HomeInput,
    ): List<HomeSection> {
        val hero = heroOf(open)
        val byBucket = open.groupBy { bucketOf(Instant.ofEpochMilli(it.snipe.endTimeUtcMillis), input.now) }
        return listOf(HomeBucket.EndingSoon, HomeBucket.ThisWeek, HomeBucket.Later).mapNotNull { bucket ->
            val inBucket = byBucket[bucket].orEmpty()
            if (inBucket.isEmpty()) return@mapNotNull null
            HomeSection(bucket, blocksOf(inBucket, hero?.takeIf { it in inBucket }, open, input))
        }
    }

    private fun blocksOf(
        inBucket: List<HomeLot>,
        hero: HomeLot?,
        open: List<HomeLot>,
        input: HomeInput,
    ): List<HomeBlock> {
        val rest = inBucket.filter { it != hero }
        val joinedGroups =
            rest
                .mapNotNull { it.snipe.groupId }
                .distinct()
                .filter { joins(it, rest, open) }
                .toSet()
        val blocks = mutableListOf<HomeBlock>()
        val singles = mutableListOf<HomeLot>()
        var heroPending = hero != null
        // The hero is never inside its group's block, so it carries the group flag like any lone row.
        if (hero != null) singles += hero.copy(groupFlag = groupFlagOf(hero, input))

        fun flush() {
            if (singles.isEmpty()) return
            blocks += HomeBlock(lots = singles.toList(), opensWithHero = heroPending)
            heroPending = false
            singles.clear()
        }

        var index = 0
        while (index < rest.size) {
            val lot = rest[index]
            val groupId = lot.snipe.groupId
            val group = groupId?.let { id -> input.groups.firstOrNull { it.id == id } }
            if (group != null && group.id in joinedGroups) {
                flush()
                val members = rest.filter { it.snipe.groupId == group.id }
                blocks += HomeBlock(lots = members, group = headerOf(group, input))
                index += members.size
            } else {
                singles += lot.copy(groupFlag = group?.let { HomeText.groupTitle(headerOf(it, input)) })
                index++
            }
        }
        flush()
        return blocks
    }

    /** "Keyboard: win 1 of 2" for a lot in a group, or null. */
    private fun groupFlagOf(
        lot: HomeLot,
        input: HomeInput,
    ): String? {
        val groupId = lot.snipe.groupId ?: return null
        val group = input.groups.firstOrNull { it.id == groupId } ?: return null
        return HomeText.groupTitle(headerOf(group, input))
    }

    /** A group joins into a block when it has two or more open lots, all here, next to each other. */
    private fun joins(
        groupId: Long,
        rest: List<HomeLot>,
        open: List<HomeLot>,
    ): Boolean {
        val members = open.filter { it.snipe.groupId == groupId }
        if (members.size < 2) return false
        val positions = members.map { member -> rest.indexOfFirst { it.id == member.id } }
        if (positions.any { it < 0 }) return false
        return positions.sorted().zipWithNext().all { (first, second) -> second == first + 1 }
    }

    private fun headerOf(
        group: SnipeGroup,
        input: HomeInput,
    ): GroupHeader =
        GroupHeader(
            id = group.id,
            name = group.name,
            targetWins = group.targetWins,
            memberCount = input.snipes.count { it.groupId == group.id },
        )

    private fun doneSection(
        finished: List<HomeLot>,
        now: Instant,
    ): HomeSection? {
        val since = now - DONE_FOR
        val done = finished.filter { finishedAt(it) >= since }.sortedByDescending { finishedAt(it) }
        return if (done.isEmpty()) null else HomeSection(HomeBucket.Done, listOf(HomeBlock(lots = done)))
    }
}
