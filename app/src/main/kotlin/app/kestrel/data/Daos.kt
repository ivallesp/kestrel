package app.kestrel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.kestrel.model.EbaySite
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.flow.Flow

// Every snipe query except findIncludingDeleted hides rows the user deleted (deletedAtUtcMillis set).

@Dao
interface SnipeDao {
    @Query("SELECT * FROM snipes WHERE deletedAtUtcMillis IS NULL ORDER BY endTimeUtcMillis ASC, id ASC")
    fun observeAll(): Flow<List<SnipeEntity>>

    @Query("SELECT * FROM snipes WHERE id = :id AND deletedAtUtcMillis IS NULL")
    fun observe(id: Long): Flow<SnipeEntity?>

    @Query(
        "SELECT * FROM snipes WHERE status IN (:statuses) AND deletedAtUtcMillis IS NULL " +
            "ORDER BY endTimeUtcMillis DESC, id DESC",
    )
    fun observeWithStatuses(statuses: List<String>): Flow<List<SnipeEntity>>

    @Query("SELECT * FROM snipes WHERE id = :id AND deletedAtUtcMillis IS NULL")
    suspend fun find(id: Long): SnipeEntity?

    @Query("SELECT * FROM snipes WHERE id = :id")
    suspend fun findIncludingDeleted(id: Long): SnipeEntity?

    @Query(
        "SELECT * FROM snipes WHERE status NOT IN (:statuses) AND deletedAtUtcMillis IS NULL " +
            "ORDER BY endTimeUtcMillis ASC, id ASC",
    )
    suspend fun withoutStatuses(statuses: List<String>): List<SnipeEntity>

    @Query(
        "SELECT * FROM snipes WHERE site = :site AND itemId = :itemId AND status NOT IN (:finalStatuses) " +
            "AND deletedAtUtcMillis IS NULL ORDER BY id ASC LIMIT 1",
    )
    suspend fun findOpen(
        site: EbaySite,
        itemId: String,
        finalStatuses: List<String>,
    ): SnipeEntity?

    // A won member stays visible to group rules even after the user deleted it from the history: hiding it
    // would let a "win 1 of N" group win a second time.
    @Query(
        "SELECT * FROM snipes WHERE groupId = :groupId AND (deletedAtUtcMillis IS NULL OR status = 'WON') " +
            "ORDER BY endTimeUtcMillis ASC, id ASC",
    )
    suspend fun inGroup(groupId: Long): List<SnipeEntity>

    // The wins inGroup sees, so the screens and the data layer judge a group complete as the engine does.
    @Query("SELECT COUNT(*) FROM snipes WHERE groupId = :groupId AND status = 'WON'")
    suspend fun winsInGroup(groupId: Long): Int

    @Query(
        "SELECT groupId, COUNT(*) AS wins FROM snipes WHERE groupId IS NOT NULL AND status = 'WON' GROUP BY groupId",
    )
    fun observeGroupWins(): Flow<List<GroupWinsRow>>

    // Only snipes that will still bid count: a finished or watched snipe's dry run says nothing about the next bid.
    // A failed dry run comes first (the one that bids soonest), so a later pass on another snipe never hides it.
    @Query(
        "SELECT * FROM snipes WHERE dryRunAtUtcMillis IS NOT NULL AND deletedAtUtcMillis IS NULL " +
            "AND maxBid IS NOT NULL AND status IN (:biddingStatuses) " +
            "ORDER BY CASE WHEN dryRunPassed = 0 THEN 0 ELSE 1 END ASC, " +
            "CASE WHEN dryRunPassed = 0 THEN endTimeUtcMillis ELSE 0 END ASC, " +
            "dryRunAtUtcMillis DESC, id DESC LIMIT 1",
    )
    suspend fun latestDryRun(biddingStatuses: List<String>): SnipeEntity?

    @Insert
    suspend fun insert(snipe: SnipeEntity): Long

    @Update
    suspend fun update(snipe: SnipeEntity): Int

    @Query("UPDATE snipes SET deletedAtUtcMillis = :atUtcMillis WHERE id = :id AND deletedAtUtcMillis IS NULL")
    suspend fun markDeleted(
        id: Long,
        atUtcMillis: Long,
    ): Int

    @Query("UPDATE snipes SET deletedAtUtcMillis = NULL WHERE id = :id AND deletedAtUtcMillis IS NOT NULL")
    suspend fun restore(id: Long): Int

    // Won group members are kept (hidden) for the same reason as in inGroup.
    @Query(
        "DELETE FROM snipes WHERE deletedAtUtcMillis IS NOT NULL AND deletedAtUtcMillis < :beforeUtcMillis " +
            "AND NOT (status = 'WON' AND groupId IS NOT NULL)",
    )
    suspend fun purgeDeleted(beforeUtcMillis: Long): Int
}

/** How many members of group [groupId] are won, hidden ones included. */
data class GroupWinsRow(
    val groupId: Long,
    val wins: Int,
)

@Dao
interface GroupDao {
    @Query("SELECT * FROM `groups` ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    suspend fun find(id: Long): GroupEntity?

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Query("UPDATE `groups` SET name = :name WHERE id = :id")
    suspend fun rename(
        id: Long,
        name: String,
    ): Int
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_events WHERE snipeId = :snipeId ORDER BY atUtcMillis ASC, id ASC")
    fun observeFor(snipeId: Long): Flow<List<TimelineEventEntity>>

    @Query(
        "SELECT * FROM timeline_events WHERE snipeId = :snipeId AND kind = :kind " +
            "ORDER BY atUtcMillis DESC, id DESC LIMIT 1",
    )
    suspend fun latest(
        snipeId: Long,
        kind: TimelineKind,
    ): TimelineEventEntity?

    @Insert
    suspend fun insert(event: TimelineEventEntity): Long
}

@Dao
interface PricePointDao {
    @Query("SELECT * FROM price_points WHERE snipeId = :snipeId ORDER BY atUtcMillis ASC, id ASC")
    fun observeFor(snipeId: Long): Flow<List<PricePointEntity>>

    @Query("SELECT * FROM price_points WHERE snipeId = :snipeId ORDER BY atUtcMillis DESC, id DESC LIMIT 1")
    suspend fun latest(snipeId: Long): PricePointEntity?

    @Insert
    suspend fun insert(point: PricePointEntity): Long
}
