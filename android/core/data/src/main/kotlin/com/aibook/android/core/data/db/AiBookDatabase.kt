package com.aibook.android.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        OpdsConnectionEntity::class,
        ScanDirectoryEntity::class,
        OpdsCatalogEntryEntity::class,
        ShelfFolderEntity::class,
        ReaderBookmarkEntity::class,
        ReaderHighlightEntity::class
    ],
    version = 13,
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
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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
