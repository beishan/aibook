package com.aibook.android.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "opds_connections")
data class OpdsConnectionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String? = null,
    val passwordCiphertext: String? = null,
    val enabled: Boolean = true,
    val lastSyncedAt: Long? = null,
    val bookCount: Int = 0,
    val syncState: String = "IDLE",
    val lastErrorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncMode: String = "FULL"
)
