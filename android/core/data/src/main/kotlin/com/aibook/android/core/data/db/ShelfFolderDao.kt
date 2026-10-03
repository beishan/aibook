package com.aibook.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfFolderDao {

    @Query("SELECT * FROM shelf_folders ORDER BY createdAt ASC, name ASC")
    fun observeAll(): Flow<List<ShelfFolderEntity>>

    @Query("SELECT * FROM shelf_folders ORDER BY createdAt ASC, name ASC")
    suspend fun getAll(): List<ShelfFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: ShelfFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<ShelfFolderEntity>)

    @Query("SELECT * FROM shelf_folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShelfFolderEntity?

    @Query("DELETE FROM shelf_folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM shelf_folders")
    suspend fun deleteAll()
}
