package com.aibook.android.core.data.db

import android.content.Context
import android.content.ContentValues
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aibook.android.core.data.security.AndroidKeystoreSecretCipher

@Database(
    entities = [
        BookEntity::class,
        OpdsConnectionEntity::class,
        ScanDirectoryEntity::class,
        OpdsCatalogEntryEntity::class,
        ShelfFolderEntity::class,
        ReaderBookmarkEntity::class,
        ReaderHighlightEntity::class,
        DownloadTaskEntity::class
    ],
    version = 17,
    exportSchema = true
)
abstract class AiBookDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun opdsConnectionDao(): OpdsConnectionDao
    abstract fun scanDirectoryDao(): ScanDirectoryDao
    abstract fun opdsCatalogEntryDao(): OpdsCatalogEntryDao
    abstract fun shelfFolderDao(): ShelfFolderDao
    abstract fun readerBookmarkDao(): ReaderBookmarkDao
    abstract fun readerHighlightDao(): ReaderHighlightDao
    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AiBookDatabase? = null

        fun get(context: Context): AiBookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AiBookDatabase::class.java,
                    "aibook.db"
                )
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reader_highlights (id TEXT NOT NULL, bookId TEXT NOT NULL, chapterHref TEXT, chapterIndex INTEGER, lineIndex INTEGER NOT NULL, startOffset INTEGER NOT NULL, endOffset INTEGER NOT NULL, excerpt TEXT NOT NULL, note TEXT, color INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reader_highlights_bookId ON reader_highlights(bookId)")
            }
        }
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN readingDurationSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN progressPdfZoom REAL")
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_shelved_lastReadAt_title ON books(shelved, lastReadAt, title)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_shelved_folderId_lastReadAt ON books(shelved, folderId, lastReadAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_sha256 ON books(sha256)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_opds_catalog_entries_syncedAt_title ON opds_catalog_entries(syncedAt, title)")
            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN rating REAL")
                db.execSQL("ALTER TABLE books ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE opds_connections RENAME TO opds_connections_legacy")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `opds_connections` (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL,
                        `username` TEXT, `passwordCiphertext` TEXT, `enabled` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER, `bookCount` INTEGER NOT NULL, `syncState` TEXT NOT NULL,
                        `lastErrorMessage` TEXT, `createdAt` INTEGER NOT NULL, `syncMode` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val cipher = AndroidKeystoreSecretCipher()
                db.query("SELECT * FROM opds_connections_legacy").use { cursor ->
                    while (cursor.moveToNext()) {
                        val password = cursor.stringOrNull("password")
                        val values = ContentValues().apply {
                            put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                            put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                            put("baseUrl", cursor.getString(cursor.getColumnIndexOrThrow("baseUrl")))
                            put("username", cursor.stringOrNull("username"))
                            put("passwordCiphertext", password?.let(cipher::encrypt))
                            put("enabled", cursor.getInt(cursor.getColumnIndexOrThrow("enabled")))
                            put("lastSyncedAt", cursor.longOrNull("lastSyncedAt"))
                            put("bookCount", cursor.getInt(cursor.getColumnIndexOrThrow("bookCount")))
                            put("syncState", cursor.getString(cursor.getColumnIndexOrThrow("syncState")))
                            put("lastErrorMessage", cursor.stringOrNull("lastErrorMessage"))
                            put("createdAt", cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
                            put("syncMode", "FULL")
                        }
                        db.insert("opds_connections", 0, values)
                    }
                }
                db.execSQL("DROP TABLE opds_connections_legacy")
            }
        }

        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `download_tasks` (`id` TEXT NOT NULL, `remoteEntryId` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `title` TEXT NOT NULL, `href` TEXT NOT NULL, `fileName` TEXT NOT NULL, `status` TEXT NOT NULL, `progress` INTEGER NOT NULL, `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_tasks_remoteEntryId` ON `download_tasks` (`remoteEntryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_tasks_status` ON `download_tasks` (`status`)")
            }
        }

        private fun android.database.Cursor.stringOrNull(column: String): String? {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) null else getString(index)
        }

        private fun android.database.Cursor.longOrNull(column: String): Long? {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) null else getLong(index)
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reader_bookmarks` (
                        `id` TEXT NOT NULL,
                        `bookId` TEXT NOT NULL,
                        `chapterHref` TEXT,
                        `chapterTitle` TEXT,
                        `progress` REAL NOT NULL,
                        `chapterIndex` INTEGER,
                        `lineIndex` INTEGER NOT NULL,
                        `scrollOffset` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reader_bookmarks_bookId` ON `reader_bookmarks` (`bookId`)"
                )
            }
        }
    }
}
