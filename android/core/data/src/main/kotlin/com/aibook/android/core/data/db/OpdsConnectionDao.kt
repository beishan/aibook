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

    @Query("UPDATE opds_connections SET name = :name, baseUrl = :baseUrl, username = :username, passwordCiphertext = :passwordCiphertext, syncMode = :syncMode WHERE id = :id")
    suspend fun updateConnectionFields(
        id: String,
        name: String,
        baseUrl: String,
        username: String?,
        passwordCiphertext: String?,
        syncMode: String
    )

    @Query("UPDATE opds_connections SET passwordCiphertext = :passwordCiphertext WHERE id = :id")
    suspend fun updatePasswordCiphertext(id: String, passwordCiphertext: String?)

    @Query("UPDATE opds_connections SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean)

    @Query("UPDATE opds_connections SET syncState = :syncState, lastSyncedAt = COALESCE(:lastSyncedAt, lastSyncedAt), bookCount = COALESCE(:bookCount, bookCount), lastErrorMessage = :lastErrorMessage WHERE id = :id")
    suspend fun updateSyncState(
        id: String,
        syncState: String,
        lastSyncedAt: Long?,
        bookCount: Int?,
        lastErrorMessage: String?
    )

    @Query("DELETE FROM opds_connections WHERE id = :id")
    suspend fun deleteById(id: String)
}
