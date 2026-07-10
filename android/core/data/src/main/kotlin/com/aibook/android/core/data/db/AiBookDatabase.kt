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
        ReaderBookmarkEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AiBookDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun opdsConnectionDao(): OpdsConnectionDao
    abstract fun scanDirectoryDao(): ScanDirectoryDao
    abstract fun opdsCatalogEntryDao(): OpdsCatalogEntryDao
    abstract fun shelfFolderDao(): ShelfFolderDao
    abstract fun readerBookmarkDao(): ReaderBookmarkDao

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
                    .addMigrations(MIGRATION_8_9)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
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
