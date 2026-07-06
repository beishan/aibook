package com.aibook.android.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "opds_catalog_entries",
    indices = [
        Index(value = ["connectionId"]),
        Index(value = ["connectionId", "acquisitionHref"], unique = true)
    ]
)
data class OpdsCatalogEntryEntity(
    @PrimaryKey
    val id: String,
    val connectionId: String,
    val sourceName: String,
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val coverHref: String? = null,
    val acquisitionHref: String,
    val acquisitionType: String? = null,
    val format: String,
    val categories: String = "",
    val syncedAt: Long
)
