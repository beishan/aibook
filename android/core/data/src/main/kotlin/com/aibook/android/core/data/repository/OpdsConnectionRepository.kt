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
        if (existing != null && id != null) {
            return updateConnectionFields(
                id = id,
                name = name,
                baseUrl = baseUrl,
                username = username,
                password = password
            ) ?: existing.toDomain()
        }
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
        dao.updateEnabled(id, enabled)
    }

    suspend fun updateConnectionFields(
        id: String,
        name: String,
        baseUrl: String,
        username: String?,
        password: String?
    ): OpdsConnection? {
        val existing = dao.getById(id) ?: return null
        dao.updateConnectionFields(
            id = id,
            name = name.ifBlank { "OPDS 书库" },
            baseUrl = baseUrl,
            username = username?.ifBlank { null },
            password = password?.ifBlank { null }
        )
        return existing.copy(
            name = name.ifBlank { "OPDS 书库" },
            baseUrl = baseUrl,
            username = username?.ifBlank { null },
            password = password?.ifBlank { null }
        ).toDomain()
    }

    suspend fun markSyncing(id: String) {
        dao.updateSyncState(
            id = id,
            syncState = OpdsSyncState.SYNCING.name,
            lastSyncedAt = null,
            bookCount = null,
            lastErrorMessage = null
        )
    }

    suspend fun markSyncSuccess(id: String, lastSyncedAt: Long, bookCount: Int) {
        dao.updateSyncState(
            id = id,
            syncState = OpdsSyncState.SUCCESS.name,
            lastSyncedAt = lastSyncedAt,
            bookCount = bookCount,
            lastErrorMessage = null
        )
    }

    suspend fun markSyncFailed(id: String, errorMessage: String) {
        dao.updateSyncState(
            id = id,
            syncState = OpdsSyncState.FAILED.name,
            lastSyncedAt = null,
            bookCount = null,
            lastErrorMessage = errorMessage
        )
    }

    suspend fun updateSyncState(
        id: String,
        syncState: OpdsSyncState,
        lastSyncedAt: Long? = null,
        bookCount: Int? = null,
        errorMessage: String? = null
    ) {
        dao.updateSyncState(
            id = id,
            syncState = syncState.name,
            lastSyncedAt = lastSyncedAt,
            bookCount = bookCount,
            lastErrorMessage = errorMessage
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
