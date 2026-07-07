package com.aibook.android.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        OpdsConnectionEntity::class,
        ScanDirectoryEntity::class,
        OpdsCatalogEntryEntity::class,
        ShelfFolderEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AiBookDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun opdsConnectionDao(): OpdsConnectionDao
    abstract fun scanDirectoryDao(): ScanDirectoryDao
    abstract fun opdsCatalogEntryDao(): OpdsCatalogEntryDao
    abstract fun shelfFolderDao(): ShelfFolderDao

    companion object {
        @Volatile
        private var INSTANCE: AiBookDatabase? = null

        fun get(context: Context): AiBookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AiBookDatabase::class.java,
                    "aibook.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
