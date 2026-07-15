package com.aibook.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OpdsCatalogEntryDao {

    @Query("SELECT * FROM opds_catalog_entries ORDER BY syncedAt DESC, title ASC")
    abstract fun observeAll(): Flow<List<OpdsCatalogEntryEntity>>

    @Query("SELECT * FROM opds_catalog_entries WHERE connectionId = :connectionId")
    abstract suspend fun getByConnection(connectionId: String): List<OpdsCatalogEntryEntity>

    @Query("SELECT COUNT(*) FROM opds_catalog_entries WHERE connectionId = :connectionId")
    abstract suspend fun countByConnection(connectionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entries: List<OpdsCatalogEntryEntity>)

    @Query("DELETE FROM opds_catalog_entries WHERE connectionId = :connectionId")
    abstract suspend fun deleteByConnection(connectionId: String)

    @Transaction
    open suspend fun replaceForConnection(
        connectionId: String,
        entries: List<OpdsCatalogEntryEntity>
    ) {
        deleteByConnection(connectionId)
        if (entries.isNotEmpty()) {
            insertAll(entries)
        }
    }
}
