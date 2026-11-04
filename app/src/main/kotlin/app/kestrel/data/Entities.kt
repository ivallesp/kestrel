package app.kestrel.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.kestrel.model.EbaySite
import app.kestrel.model.Money
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind

/** One row per snipe. The result columns are null until the snipe ran. A non-null [deletedAtUtcMillis] hides the row. */
@Entity(
    tableName = "snipes",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("endTimeUtcMillis"),
        Index("status"),
        Index("groupId"),
        Index("site", "itemId"),
    ],
)
data class SnipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val site: EbaySite,
    val itemId: String,
    val title: String,
    val imageUrl: String?,
    val sellerName: String?,
    val maxBid: Money?,
    val leadSeconds: Int,
    val groupId: Long?,
    val status: SnipeStatus,
    val endTimeUtcMillis: Long,
    val lastPrice: Money?,
    val lastBidCount: Int?,
    val lastCheckedAtUtcMillis: Long?,
    val postage: Money?,
    val buyerProtectionFee: Money?,
    val buyItNow: Money?,
    val hasReserve: Boolean,
    val reserveMet: Boolean?,
    val extendedBidding: Boolean,
    val dryRunAtUtcMillis: Long?,
    val dryRunPassed: Boolean?,
    val resultOutcome: SnipeOutcome?,
    val resultFinalPrice: Money?,
    val resultBidSentAtUtcMillis: Long?,
    val resultBidLeadMillis: Long?,
    val resultReplyLatencyMillis: Long?,
    val resultEbayReplyText: String?,
    val resultReason: String?,
    val createdAtUtcMillis: Long,
    val updatedAtUtcMillis: Long,
    val deletedAtUtcMillis: Long?,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetWins: Int,
    val createdAtUtcMillis: Long,
)

@Entity(
    tableName = "price_points",
    foreignKeys = [
        ForeignKey(
            entity = SnipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["snipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("snipeId", "atUtcMillis")],
)
data class PricePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snipeId: Long,
    val atUtcMillis: Long,
    val price: Money,
    val bidCount: Int,
)

@Entity(
    tableName = "timeline_events",
    foreignKeys = [
        ForeignKey(
            entity = SnipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["snipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("snipeId", "atUtcMillis")],
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snipeId: Long,
    val atUtcMillis: Long,
    val kind: TimelineKind,
    val message: String,
    val detail: String?,
)
