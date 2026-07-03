package com.aibook.android.core.data.repository

import com.aibook.android.core.data.db.OpdsConnectionDao
import com.aibook.android.core.data.mapper.toDomain
import com.aibook.android.core.data.mapper.toEntity
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsCatalogService
import com.aibook.android.core.network.opds.OpdsFeed
import com.aibook.android.core.network.opds.OpdsSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class OpdsConnectionRepository(
    private val dao: OpdsConnectionDao
) {
    fun observeConnections(): Flow<List<OpdsConnection>> {
        return dao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getAll(): List<OpdsConnection> {
        return dao.getAll().map { it.toDomain() }
    }

    suspend fun getById(id: String): OpdsConnection? {
        return dao.getById(id)?.toDomain()
    }

    suspend fun saveConnection(
        name: String,
        baseUrl: String,
        username: String?,
        password: String?,
        id: String? = null
    ): OpdsConnection {
        val connectionId = id ?: UUID.nameUUIDFromBytes(baseUrl.toByteArray()).toString()
        val existing = dao.getById(connectionId)
        val connection = OpdsConnection(
            id = connectionId,
            name = name.ifBlank { "OPDS 书库" },
            baseUrl = baseUrl,
            username = username?.ifBlank { null },
            password = password?.ifBlank { null },
            enabled = existing?.enabled ?: true,
            lastSyncedAt = existing?.lastSyncedAt,
            bookCount = existing?.bookCount ?: 0,
            syncState = existing?.syncState
                ?.let { runCatching { OpdsSyncState.valueOf(it) }.getOrDefault(OpdsSyncState.IDLE) }
                ?: OpdsSyncState.IDLE,
            lastErrorMessage = existing?.lastErrorMessage
        )
        dao.insert(connection.toEntity().copy(createdAt = existing?.createdAt ?: System.currentTimeMillis()))
        return connection
    }

    suspend fun deleteConnection(id: String) {
        dao.deleteById(id)
    }

    suspend fun updateEnabled(id: String, enabled: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.insert(existing.copy(enabled = enabled))
    }

    suspend fun updateSyncState(
        id: String,
        syncState: OpdsSyncState,
        lastSyncedAt: Long? = null,
        bookCount: Int? = null,
        errorMessage: String? = null
    ) {
        val existing = dao.getById(id) ?: return
        dao.insert(
            existing.copy(
                syncState = syncState.name,
                lastSyncedAt = lastSyncedAt ?: existing.lastSyncedAt,
                bookCount = bookCount ?: existing.bookCount,
                lastErrorMessage = errorMessage
            )
        )
    }

    fun browse(
        catalogService: OpdsCatalogService,
        connection: OpdsConnection,
        href: String? = null
    ): OpdsFeed {
        return catalogService.load(connection, href)
    }
}
