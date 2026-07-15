package com.aibook.android.core.data.repository

import com.aibook.android.core.data.db.OpdsCatalogEntryDao
import com.aibook.android.core.data.db.OpdsCatalogEntryEntity
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsFeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class OpdsCatalogCacheRepository(
    private val dao: OpdsCatalogEntryDao
) {
    fun observeEntries(): Flow<List<OpdsCatalogEntry>> {
        return dao.observeAll().map { rows ->
            rows.map { it.toDomain() }
        }
    }

    suspend fun replaceConnectionEntries(
        connection: OpdsConnection,
        feed: OpdsFeed,
        syncedAt: Long = System.currentTimeMillis()
    ) {
        val entries = feed.entries
            .filter { it.acquisitionLink != null }
            .map { it.toEntity(connection, syncedAt) }
        dao.replaceForConnection(connection.id, entries)
    }

    suspend fun mergeConnectionEntries(
        connection: OpdsConnection,
        feed: OpdsFeed,
        syncedAt: Long = System.currentTimeMillis()
    ): OpdsMergeResult {
        val existing = dao.getByConnection(connection.id).associateBy { it.id }
        val incoming = feed.entries.filter { it.acquisitionLink != null }.map { it.toEntity(connection, syncedAt) }
        var added = 0
        var updated = 0
        val changed = incoming.filter { row ->
            val old = existing[row.id]
            when {
                old == null -> { added += 1; true }
                old.copy(syncedAt = row.syncedAt) != row -> { updated += 1; true }
                else -> false
            }
        }
        if (changed.isNotEmpty()) dao.insertAll(changed)
        return OpdsMergeResult(added, updated, incoming.size - added - updated, dao.countByConnection(connection.id))
    }

    suspend fun countByConnection(connectionId: String): Int = dao.countByConnection(connectionId)

    suspend fun deleteByConnection(connectionId: String) {
        dao.deleteByConnection(connectionId)
    }

    private fun OpdsEntry.toEntity(connection: OpdsConnection, syncedAt: Long): OpdsCatalogEntryEntity {
        val acquisition = requireNotNull(acquisitionLink)
        val format = formatFrom(acquisition.type, acquisition.href)
        val categories = categories.joinToString("|")
        return OpdsCatalogEntryEntity(
            id = stableId(connection.id, identifier ?: acquisition.href),
            connectionId = connection.id,
            sourceName = connection.name,
            title = title,
            author = author,
            summary = summary,
            coverHref = coverLink?.href,
            acquisitionHref = acquisition.href,
            acquisitionType = acquisition.type,
            format = format,
            categories = categories,
            syncedAt = syncedAt
        )
    }

    private fun OpdsCatalogEntryEntity.toDomain(): OpdsCatalogEntry {
        return OpdsCatalogEntry(
            id = id,
            connectionId = connectionId,
            sourceName = sourceName,
            title = title,
            author = author,
            summary = summary,
            coverHref = coverHref,
            acquisitionHref = acquisitionHref,
            acquisitionType = acquisitionType,
            format = format,
            categories = categories.split("|").filter { it.isNotBlank() },
            syncedAt = syncedAt
        )
    }

    private fun stableId(connectionId: String, remoteIdentifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$connectionId|$remoteIdentifier".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun formatFrom(type: String?, href: String): String {
        val lowerType = type.orEmpty().lowercase()
        val extension = href.substringBefore('?').substringAfterLast('.', missingDelimiterValue = "")
            .uppercase()
        return when {
            "epub" in lowerType -> "EPUB"
            "pdf" in lowerType -> "PDF"
            "html" in lowerType -> "HTML"
            "markdown" in lowerType -> "Markdown"
            "text" in lowerType || "txt" in lowerType -> "TXT"
            extension == "MD" -> "Markdown"
            extension.isNotBlank() -> extension
            else -> "未知"
        }
    }
}

data class OpdsMergeResult(val added: Int, val updated: Int, val unchanged: Int, val total: Int)

data class OpdsCatalogEntry(
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
    val categories: List<String> = emptyList(),
    val syncedAt: Long
)
