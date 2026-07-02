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
    val password: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
