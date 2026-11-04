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

    @Query(
        "SELECT * FROM snipes WHERE dryRunAtUtcMillis IS NOT NULL AND deletedAtUtcMillis IS NULL " +
            "ORDER BY dryRunAtUtcMillis DESC, id DESC LIMIT 1",
    )
    suspend fun latestDryRun(): SnipeEntity?

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
