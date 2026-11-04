package app.kestrel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The snipe database. It lives in credential-encrypted storage, so it can only be opened after the first unlock.
 * Schema changes need a migration: there is deliberately no destructive fallback, because it would wipe armed snipes.
 */
@Database(
    entities = [SnipeEntity::class, GroupEntity::class, PricePointEntity::class, TimelineEventEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KestrelDatabase : RoomDatabase() {
    abstract fun snipeDao(): SnipeDao

    abstract fun groupDao(): GroupDao

    abstract fun timelineDao(): TimelineDao

    abstract fun pricePointDao(): PricePointDao

    companion object {
        const val FILE_NAME = "kestrel.db"

        fun open(context: Context): KestrelDatabase =
            Room.databaseBuilder(context.applicationContext, KestrelDatabase::class.java, FILE_NAME).build()

        fun inMemory(context: Context): KestrelDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, KestrelDatabase::class.java).build()
    }
}
