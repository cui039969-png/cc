// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.sync.Mutex

@Database(entities = [DailyRecord::class, SyncAccount::class, SyncLink::class, SyncOperation::class, io.github.daily3minutes.audio.VoiceNote::class], version = 3, exportSchema = true)
abstract class DailyDatabase : RoomDatabase() {
    abstract fun records(): DailyDao
    abstract fun sync(): SyncDao
    abstract fun voices(): io.github.daily3minutes.audio.VoiceDao

    // Shared by every repository using this database, including widget and Activity instances.
    internal val writeMutex = Mutex()

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `voice_notes` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `question` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `size` INTEGER NOT NULL, `sha256` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_notes_date_question` ON `voice_notes` (`date`, `question`)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_accounts` (`id` TEXT NOT NULL, `email` TEXT NOT NULL, `listId` TEXT NOT NULL, `sinceDate` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `lastSuccess` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_links` (`accountId` TEXT NOT NULL, `date` TEXT NOT NULL, `taskId` TEXT NOT NULL, `baseTitle` TEXT NOT NULL, `baseCompleted` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `due` TEXT NOT NULL, `etag` TEXT NOT NULL, `state` TEXT NOT NULL, `remoteTitle` TEXT NOT NULL, `remoteCompleted` INTEGER NOT NULL, PRIMARY KEY(`accountId`, `date`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_operations` (`accountId` TEXT NOT NULL, `date` TEXT NOT NULL, `revision` INTEGER NOT NULL, `kind` TEXT NOT NULL, PRIMARY KEY(`accountId`, `date`))")
            }
        }
        @Volatile private var instance: DailyDatabase? = null

        fun get(context: Context): DailyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DailyDatabase::class.java,
                "daily-3-minutes.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
