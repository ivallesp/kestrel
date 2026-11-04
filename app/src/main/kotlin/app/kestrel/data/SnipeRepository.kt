package app.kestrel.data

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.room.withTransaction
import app.kestrel.engine.SnipeStore
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The single owner of the snipe tables. The UI edits snipes through the operations here; the engine reads and
 * writes through [SnipeStore]. Callers reschedule alarms after a change (see SnipeScheduler); this class only stores.
 *
 * Engine writes never change the user's own fields (max, lead, group): only the user can raise a max.
 */
class SnipeRepository(
    private val database: KestrelDatabase,
    private val armedCopy: ArmedSnipesCopy,
    private val nowUtcMillis: () -> Long,
    private val formatMoney: (Money) -> String,
    /**
     * Runs the armed-copy refresh after an engine write, off the caller's path: the BID_SENT write right before the
     * tap must not wait for a file write and its fsync. Null refreshes inline.
     */
    private val refreshArmedCopyLater: ((suspend () -> Unit) -> Unit)? = null,
) : SnipeStore {
    private val snipes = database.snipeDao()
    private val groups = database.groupDao()
    private val timeline = database.timelineDao()
    private val prices = database.pricePointDao()
    private val armedCopyLock = Mutex()

    // Reads for the UI.

    /** Every snipe the user has not deleted, soonest end first. */
    fun observeSnipes(): Flow<List<Snipe>> = snipes.observeAll().map { rows -> rows.map { it.toSnipe() } }

    fun observeSnipe(id: Long): Flow<Snipe?> = snipes.observe(id).map { it?.toSnipe() }

    fun observeTimeline(snipeId: Long): Flow<List<TimelineEvent>> =
        timeline.observeFor(snipeId).map { rows -> rows.map { it.toEvent() } }

    fun observePricePoints(snipeId: Long): Flow<List<PricePoint>> =
        prices.observeFor(snipeId).map { rows -> rows.map { it.toPricePoint() } }

    fun observeGroups(): Flow<List<SnipeGroup>> = groups.observeAll().map { rows -> rows.map { it.toGroup() } }

    /** Finished snipes, most recent end first. */
    fun observeHistory(): Flow<List<Snipe>> =
        snipes.observeWithStatuses(SnipeRules.FINAL_STATUS_NAMES).map { rows -> rows.map { it.toSnipe() } }

    fun observeStats(): Flow<SnipeStats> = observeHistory().map { SnipeStats.of(it) }

    // Reads for the platform.

    /** Snipes that may still need alarms: not finished and not deleted. */
    suspend fun pendingSnipes(): List<Snipe> =
        snipes.withoutStatuses(SnipeRules.FINAL_STATUS_NAMES).map { it.toSnipe() }

    /** The snipe whose dry run ran most recently, if any. */
    suspend fun latestDryRun(): Snipe? = snipes.latestDryRun()?.toSnipe()

    suspend fun latestEvent(
        snipeId: Long,
        kind: TimelineKind,
    ): TimelineEvent? = timeline.latest(snipeId, kind)?.toEvent()

    // Operations for the UI.

    /** Adds a snipe for [listing]. A null [maxBid] watches the listing without ever bidding. */
    suspend fun addFromListing(
        listing: Listing,
        maxBid: Money?,
        leadSeconds: Int,
        groupId: Long? = null,
    ): AddResult {
        val now = nowUtcMillis()
        val refusal = addRefusal(listing, maxBid, leadSeconds, now)
        if (refusal != null) return AddResult.Rejected(refusal)
        val ref = listing.relistedItemId?.let { ItemRef(listing.ref.site, it) } ?: listing.ref
        val result =
            database.withTransaction {
                if (groupId != null && groups.find(groupId) == null) {
                    return@withTransaction AddResult.Rejected("That group no longer exists.")
                }
                val existing = snipes.findOpen(ref.site, ref.itemId, SnipeRules.FINAL_STATUS_NAMES)
                if (existing != null) return@withTransaction AddResult.AlreadyAdded(existing.toSnipe())
                val watching = newSnipe(listing, ref, maxBid, leadSeconds, groupId, now)
                val entity = watching.copy(status = SnipeRules.statusForMax(watching.toSnipe(), maxBid))
                val id = snipes.insert(entity)
                timeline.insert(event(id, now, TimelineKind.ADDED, addedMessage(maxBid, leadSeconds)))
                prices.insert(
                    PricePointEntity(
                        snipeId = id,
                        atUtcMillis = listing.fetchedAtUtcMillis,
                        price = listing.currentPrice,
                        bidCount = listing.bidCount,
                    ),
                )
                AddResult.Added(entity.copy(id = id).toSnipe())
            }
        refreshArmedCopy()
        return result
    }

    /**
     * Sets, changes or removes (null) the max. Also while the bid window is open, until the edits lock 3 s before the
     * bid (see [SnipeRules.maxOrLeadRefusal]).
     */
    suspend fun updateMax(
        snipeId: Long,
        maxBid: Money?,
    ): EditResult =
        edit(snipeId) { snipe, now ->
            // True time read inside the transaction: the lock is a matter of seconds.
            SnipeRules.maxOrLeadRefusal(snipe, nowUtcMillis())?.let { return@edit EditResult.NotAllowed(it) }
            if (maxBid != null) {
                maxRefusal(snipe, maxBid)?.let { return@edit EditResult.NotAllowed(it) }
            }
            if (maxBid == snipe.maxBid) return@edit EditResult.Done(snipe)
            val status = SnipeRules.statusForMax(snipe, maxBid)
            val updated = snipe.copy(maxBid = maxBid, status = status, updatedAtUtcMillis = now)
            val kind = if (snipe.maxBid == null) TimelineKind.ARMED else TimelineKind.EDITED
            write(updated, event(snipe.id, now, kind, maxChangeMessage(snipe.maxBid, maxBid)))
        }

    suspend fun setLead(
        snipeId: Long,
        leadSeconds: Int,
    ): EditResult =
        edit(snipeId) { snipe, now ->
            SnipeRules
                .maxOrLeadRefusal(
                    snipe,
                    nowUtcMillis(),
                    leadSeconds,
                )?.let { return@edit EditResult.NotAllowed(it) }
            if (leadSeconds !in SnipeRules.LEAD_SECONDS_RANGE) return@edit EditResult.NotAllowed(leadRangeMessage())
            if (leadSeconds == snipe.leadSeconds) return@edit EditResult.Done(snipe)
            val updated = snipe.copy(leadSeconds = leadSeconds, updatedAtUtcMillis = now)
            write(updated, event(snipe.id, now, TimelineKind.EDITED, "Kestrel now bids $leadSeconds s before the end."))
        }

    /** Moves the snipe into a group, or out of any group with null. */
    suspend fun setGroup(
        snipeId: Long,
        groupId: Long?,
    ): EditResult =
        edit(snipeId) { snipe, now ->
            SnipeRules.editRefusal(snipe.status)?.let { return@edit EditResult.NotAllowed(it) }
            if (groupId == snipe.groupId) return@edit EditResult.Done(snipe)
            val group = groupId?.let { groups.find(it) }
            if (groupId != null && group == null) return@edit EditResult.NotAllowed("That group no longer exists.")
            val message = if (group == null) "Removed from its group." else "Added to the group \"${group.name}\"."
            val updated = snipe.copy(groupId = groupId, updatedAtUtcMillis = now)
            write(updated, event(snipe.id, now, TimelineKind.EDITED, message))
        }

    /** Stops the snipe for good. Refused once eBay has the bid, because a placed bid cannot be taken back here. */
    suspend fun cancel(snipeId: Long): EditResult =
        edit(snipeId) { snipe, now ->
            SnipeRules.cancelRefusal(snipe.status)?.let { return@edit EditResult.NotAllowed(it) }
            val result =
                SnipeResult(
                    outcome = SnipeOutcome.CANCELLED,
                    finalPrice = null,
                    bidSentAtUtcMillis = null,
                    bidLeadMillis = null,
                    replyLatencyMillis = null,
                    ebayReplyText = null,
                    reason = CANCELLED_BY_USER,
                )
            val updated = snipe.copy(status = SnipeStatus.CANCELLED, result = result, updatedAtUtcMillis = now)
            write(updated, event(snipe.id, now, TimelineKind.CANCELLED, CANCELLED_BY_USER))
        }

    /**
     * The user says how a bid ended that Kestrel could not read (see [SnipeRules.settleRefusal]). A win then counts
     * for the snipe's group; a loss lets the group's other snipes bid.
     */
    suspend fun settle(
        snipeId: Long,
        won: Boolean,
    ): EditResult =
        edit(snipeId) { snipe, now ->
            SnipeRules.settleRefusal(snipe, now)?.let { return@edit EditResult.NotAllowed(it) }
            val reason = "You marked it ${if (won) "won" else "lost"} after checking eBay."
            val result =
                SnipeResult(
                    outcome = if (won) SnipeOutcome.WON else SnipeOutcome.LOST,
                    finalPrice = snipe.result?.finalPrice ?: snipe.lastPrice,
                    bidSentAtUtcMillis = snipe.result?.bidSentAtUtcMillis,
                    bidLeadMillis = snipe.result?.bidLeadMillis,
                    replyLatencyMillis = snipe.result?.replyLatencyMillis,
                    ebayReplyText = snipe.result?.ebayReplyText,
                    reason = reason,
                )
            val status = if (won) SnipeStatus.WON else SnipeStatus.LOST
            write(
                snipe.copy(status = status, result = result, updatedAtUtcMillis = now),
                event(snipe.id, now, TimelineKind.RESULT, reason),
            )
        }

    /**
     * Hides the snipe; [undoDelete] brings it back unchanged. Rows stay in the database for a day so an undo always
     * works, then they are purged with their timeline and prices.
     */
    suspend fun delete(snipeId: Long): EditResult {
        val now = nowUtcMillis()
        val outcome =
            database.withTransaction {
                val snipe = snipes.find(snipeId)?.toSnipe() ?: return@withTransaction EditResult.NotFound
                SnipeRules.deleteRefusal(snipe, now)?.let { return@withTransaction EditResult.NotAllowed(it) }
                snipes.markDeleted(snipeId, now)
                EditResult.Done(snipe)
            }
        if (outcome is EditResult.Done) {
            purgeDeleted(now - DELETED_ROWS_KEPT_MILLIS)
            refreshArmedCopy()
        }
        return outcome
    }

    suspend fun undoDelete(snipeId: Long): EditResult {
        val outcome =
            database.withTransaction {
                if (snipes.restore(snipeId) == 0) return@withTransaction EditResult.NotFound
                snipes.find(snipeId)?.let { EditResult.Done(it.toSnipe()) } ?: EditResult.NotFound
            }
        refreshArmedCopy()
        return outcome
    }

    suspend fun purgeDeleted(deletedBeforeUtcMillis: Long): Int = snipes.purgeDeleted(deletedBeforeUtcMillis)

    suspend fun createGroup(
        name: String,
        targetWins: Int = 1,
    ): SnipeGroup {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A group needs a name" }
        require(targetWins >= 1) { "A group must aim to win at least one item" }
        val entity = GroupEntity(name = trimmed, targetWins = targetWins, createdAtUtcMillis = nowUtcMillis())
        val id = groups.insert(entity)
        return entity.copy(id = id).toGroup()
    }

    // SnipeStore, used by the engine.

    override suspend fun snipe(id: Long): Snipe? = snipes.find(id)?.toSnipe()

    override suspend fun update(snipe: Snipe) {
        database.withTransaction {
            val storedRow = snipes.findIncludingDeleted(snipe.id) ?: return@withTransaction
            val stored = storedRow.toSnipe()
            val statusAccepted = SnipeRules.acceptsEngineStatus(stored, snipe, nowUtcMillis())
            if (!statusAccepted) {
                Log.w(TAG, "Kept status ${stored.status} of snipe ${snipe.id}; an engine write wanted ${snipe.status}")
            }
            val merged =
                snipe.copy(
                    ref = stored.ref,
                    maxBid = stored.maxBid,
                    leadSeconds = stored.leadSeconds,
                    groupId = stored.groupId,
                    createdAtUtcMillis = stored.createdAtUtcMillis,
                    status = if (statusAccepted) snipe.status else stored.status,
                    result = if (statusAccepted) snipe.result else stored.result,
                )
            snipes.update(merged.toEntity(deletedAtUtcMillis = storedRow.deletedAtUtcMillis))
        }
        val later = refreshArmedCopyLater
        if (later != null) later { refreshArmedCopy() } else refreshArmedCopy()
    }

    override suspend fun addEvent(
        snipeId: Long,
        kind: TimelineKind,
        message: String,
        detail: String?,
    ) {
        if (snipes.findIncludingDeleted(snipeId) == null) return
        insertIgnoringPurged { timeline.insert(event(snipeId, nowUtcMillis(), kind, message, detail)) }
    }

    /** Skips a point equal to the previous one, so polling the live price does not fill the table. */
    override suspend fun recordPrice(
        snipeId: Long,
        atUtcMillis: Long,
        price: Money,
        bidCount: Int,
    ) {
        if (snipes.findIncludingDeleted(snipeId) == null) return
        val last = prices.latest(snipeId)
        if (last != null && last.price == price && last.bidCount == bidCount) return
        insertIgnoringPurged {
            val point =
                PricePointEntity(snipeId = snipeId, atUtcMillis = atUtcMillis, price = price, bidCount = bidCount)
            prices.insert(point)
        }
    }

    override suspend fun group(id: Long): SnipeGroup? = groups.find(id)?.toGroup()

    override suspend fun groupMembers(groupId: Long): List<Snipe> = snipes.inGroup(groupId).map { it.toSnipe() }

    // Helpers.

    private suspend fun edit(
        snipeId: Long,
        change: suspend (Snipe, Long) -> EditResult,
    ): EditResult {
        val now = nowUtcMillis()
        val outcome =
            database.withTransaction {
                val snipe = snipes.find(snipeId)?.toSnipe() ?: return@withTransaction EditResult.NotFound
                change(snipe, now)
            }
        refreshArmedCopy()
        return outcome
    }

    private suspend fun write(
        snipe: Snipe,
        event: TimelineEventEntity,
    ): EditResult {
        snipes.update(snipe.toEntity(deletedAtUtcMillis = null))
        timeline.insert(event)
        return EditResult.Done(snipe)
    }

    private fun addRefusal(
        listing: Listing,
        maxBid: Money?,
        leadSeconds: Int,
        now: Long,
    ): String? =
        when {
            !listing.isAuction -> {
                "This listing is not an auction."
            }

            listing.status == ListingStatus.ENDED -> {
                "This auction has already ended."
            }

            listing.endTimeUtcMillis <= now -> {
                "This auction has already ended."
            }

            leadSeconds !in SnipeRules.LEAD_SECONDS_RANGE -> {
                leadRangeMessage()
            }

            maxBid != null && maxBid.currency != listing.currentPrice.currency -> {
                "The max must be in ${listing.currentPrice.currency}, the currency of the listing."
            }

            maxBid != null && maxBid.minor <= 0 -> {
                "The max must be more than zero."
            }

            else -> {
                null
            }
        }

    private fun maxRefusal(
        snipe: Snipe,
        maxBid: Money,
    ): String? {
        val currency = snipe.lastPrice?.currency ?: snipe.maxBid?.currency
        return when {
            currency != null && maxBid.currency != currency -> {
                "The max must be in $currency, the currency of the listing."
            }

            maxBid.minor <= 0 -> {
                "The max must be more than zero."
            }

            else -> {
                null
            }
        }
    }

    private fun newSnipe(
        listing: Listing,
        ref: ItemRef,
        maxBid: Money?,
        leadSeconds: Int,
        groupId: Long?,
        now: Long,
    ) = SnipeEntity(
        site = ref.site,
        itemId = ref.itemId,
        title = listing.title,
        imageUrl = listing.imageUrl,
        sellerName = listing.sellerName,
        maxBid = maxBid,
        leadSeconds = leadSeconds,
        groupId = groupId,
        status = SnipeStatus.WATCHING,
        endTimeUtcMillis = listing.endTimeUtcMillis,
        lastPrice = listing.currentPrice,
        lastBidCount = listing.bidCount,
        lastCheckedAtUtcMillis = listing.fetchedAtUtcMillis,
        postage = listing.postage,
        buyerProtectionFee = listing.buyerProtectionFee,
        buyItNow = listing.buyItNow,
        hasReserve = listing.hasReserve,
        reserveMet = listing.reserveMet,
        extendedBidding = listing.extendedBidding,
        dryRunAtUtcMillis = null,
        dryRunPassed = null,
        resultOutcome = null,
        resultFinalPrice = null,
        resultBidSentAtUtcMillis = null,
        resultBidLeadMillis = null,
        resultReplyLatencyMillis = null,
        resultEbayReplyText = null,
        resultReason = null,
        createdAtUtcMillis = now,
        updatedAtUtcMillis = now,
        deletedAtUtcMillis = null,
    )

    private fun addedMessage(
        maxBid: Money?,
        leadSeconds: Int,
    ): String =
        if (maxBid == null) {
            "Added to watch. Kestrel will not bid."
        } else {
            "Added with a max of ${formatMoney(maxBid)}. Kestrel bids $leadSeconds s before the end."
        }

    private fun maxChangeMessage(
        before: Money?,
        after: Money?,
    ): String =
        when {
            after == null -> "Max removed. Kestrel will not bid."
            before == null -> "Armed with a max of ${formatMoney(after)}."
            else -> "Max changed from ${formatMoney(before)} to ${formatMoney(after)}."
        }

    private fun leadRangeMessage(): String {
        val range = SnipeRules.LEAD_SECONDS_RANGE
        return "Kestrel can bid between ${range.first} and ${range.last} s before the end."
    }

    private fun event(
        snipeId: Long,
        atUtcMillis: Long,
        kind: TimelineKind,
        message: String,
        detail: String? = null,
    ) = TimelineEventEntity(
        snipeId = snipeId,
        atUtcMillis = atUtcMillis,
        kind = kind,
        message = message,
        detail = detail,
    )

    /** The snipe can be purged between the existence check and the insert; losing that one line is fine. */
    private suspend fun insertIgnoringPurged(insert: suspend () -> Unit) {
        try {
            insert()
        } catch (error: SQLiteConstraintException) {
            Log.w(TAG, "Dropped a row for a snipe that no longer exists", error)
        }
    }

    /** Keeps the device-protected copy in step with the database. A failure here must never fail the edit itself. */
    private suspend fun refreshArmedCopy() {
        try {
            armedCopyLock.withLock {
                val now = nowUtcMillis()
                val armed =
                    snipes
                        .withoutStatuses(SnipeRules.FINAL_STATUS_NAMES)
                        .filter { it.maxBid != null && it.status.isActiveBid && it.endTimeUtcMillis > now }
                        .map { ArmedSnipe(id = it.id, title = it.title, endTimeUtcMillis = it.endTimeUtcMillis) }
                withContext(Dispatchers.IO) { armedCopy.write(armed) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not refresh the armed snipes copy", error)
        }
    }

    companion object {
        private const val TAG = "SnipeRepository"
        const val CANCELLED_BY_USER = "Cancelled by you."
        private const val DELETED_ROWS_KEPT_MILLIS = 24 * 60 * 60 * 1000L
    }
}
