package com.aibook.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OpdsConnectionDao {

    @Query("SELECT * FROM opds_connections ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OpdsConnectionEntity>>

    @Query("SELECT * FROM opds_connections ORDER BY createdAt DESC")
    suspend fun getAll(): List<OpdsConnectionEntity>

    @Query("SELECT * FROM opds_connections WHERE id = :id")
    suspend fun getById(id: String): OpdsConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(connection: OpdsConnectionEntity)

    @Query("DELETE FROM opds_connections WHERE id = :id")
    suspend fun deleteById(id: String)
}
