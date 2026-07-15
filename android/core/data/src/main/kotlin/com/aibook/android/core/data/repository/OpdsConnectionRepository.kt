package com.aibook.android.core.data.repository

import com.aibook.android.core.data.db.OpdsConnectionDao
import com.aibook.android.core.data.db.OpdsConnectionEntity
import com.aibook.android.core.data.security.PassthroughSecretCipher
import com.aibook.android.core.data.security.SecretCipher
import com.aibook.android.core.network.opds.OpdsCatalogService
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsFeed
import com.aibook.android.core.network.opds.OpdsSyncMode
import com.aibook.android.core.network.opds.OpdsSyncState
import com.aibook.android.core.network.opds.OpdsUrlValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class OpdsConnectionRepository(
    private val dao: OpdsConnectionDao,
    private val secretCipher: SecretCipher = PassthroughSecretCipher
) {
    fun observeConnections(): Flow<List<OpdsConnection>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<OpdsConnection> = dao.getAll().map { it.toDomain() }

    suspend fun getById(id: String): OpdsConnection? = dao.getById(id)?.toDomain()

    suspend fun saveConnection(
        name: String,
        baseUrl: String,
        username: String?,
        password: String?,
        id: String? = null,
        syncMode: OpdsSyncMode = OpdsSyncMode.FULL
    ): OpdsConnection {
        val normalizedUrl = OpdsUrlValidator.normalize(baseUrl).getOrElse { throw IllegalArgumentException(it.message) }
        val connectionId = id ?: UUID.nameUUIDFromBytes(normalizedUrl.toByteArray()).toString()
        val existing = dao.getById(connectionId)
        if (existing != null && id != null) {
            return updateConnectionFields(id, name, normalizedUrl, username, password, syncMode)
                ?: existing.toDomain()
        }
        val connection = OpdsConnection(
            id = connectionId,
            name = name.ifBlank { "OPDS 书库" },
            baseUrl = normalizedUrl,
            username = username?.ifBlank { null },
            password = password?.ifBlank { null },
            enabled = existing?.enabled ?: true,
            lastSyncedAt = existing?.lastSyncedAt,
            bookCount = existing?.bookCount ?: 0,
            syncState = existing?.syncState?.toSyncState() ?: OpdsSyncState.IDLE,
            lastErrorMessage = existing?.lastErrorMessage,
            syncMode = syncMode
        )
        dao.insert(connection.toEntity(existing?.createdAt ?: System.currentTimeMillis()))
        return connection
    }

    suspend fun deleteConnection(id: String) = dao.deleteById(id)

    suspend fun updateEnabled(id: String, enabled: Boolean) = dao.updateEnabled(id, enabled)

    suspend fun updateConnectionFields(
        id: String,
        name: String,
        baseUrl: String,
        username: String?,
        password: String?,
        syncMode: OpdsSyncMode = OpdsSyncMode.FULL
    ): OpdsConnection? {
        val existing = dao.getById(id) ?: return null
        val normalizedUrl = OpdsUrlValidator.normalize(baseUrl).getOrElse { throw IllegalArgumentException(it.message) }
        val normalizedName = name.ifBlank { "OPDS 书库" }
        val normalizedUsername = username?.ifBlank { null }
        val normalizedPassword = password?.ifBlank { null }
        dao.updateConnectionFields(
            id = id,
            name = normalizedName,
            baseUrl = normalizedUrl,
            username = normalizedUsername,
            passwordCiphertext = normalizedPassword?.let(secretCipher::encrypt),
            syncMode = syncMode.name
        )
        return existing.toDomain().copy(
            name = normalizedName,
            baseUrl = normalizedUrl,
            username = normalizedUsername,
            password = normalizedPassword,
            syncMode = syncMode
        )
    }

    suspend fun migratePlaintextSecrets() {
        dao.getAll().forEach { row ->
            val stored = row.passwordCiphertext ?: return@forEach
            if (!secretCipher.isEncrypted(stored)) {
                dao.updatePasswordCiphertext(row.id, secretCipher.encrypt(stored))
            }
        }
    }

    suspend fun markSyncing(id: String) = updateSyncState(id, OpdsSyncState.SYNCING)

    suspend fun markSyncSuccess(id: String, lastSyncedAt: Long, bookCount: Int) =
        updateSyncState(id, OpdsSyncState.SUCCESS, lastSyncedAt, bookCount)

    suspend fun markSyncFailed(id: String, errorMessage: String) =
        updateSyncState(id, OpdsSyncState.FAILED, errorMessage = errorMessage)

    suspend fun updateSyncState(
        id: String,
        syncState: OpdsSyncState,
        lastSyncedAt: Long? = null,
        bookCount: Int? = null,
        errorMessage: String? = null
    ) {
        dao.updateSyncState(id, syncState.name, lastSyncedAt, bookCount, errorMessage)
    }

    fun browse(catalogService: OpdsCatalogService, connection: OpdsConnection, href: String? = null): OpdsFeed =
        catalogService.load(connection, href)

    private fun OpdsConnectionEntity.toDomain(): OpdsConnection = OpdsConnection(
        id = id,
        name = name,
        baseUrl = baseUrl,
        username = username,
        password = passwordCiphertext?.let { runCatching { secretCipher.decrypt(it) }.getOrNull() },
        enabled = enabled,
        lastSyncedAt = lastSyncedAt,
        bookCount = bookCount,
        syncState = syncState.toSyncState(),
        lastErrorMessage = lastErrorMessage,
        syncMode = runCatching { OpdsSyncMode.valueOf(syncMode) }.getOrDefault(OpdsSyncMode.FULL)
    )

    private fun OpdsConnection.toEntity(createdAt: Long) = OpdsConnectionEntity(
        id = id,
        name = name,
        baseUrl = baseUrl,
        username = username,
        passwordCiphertext = password?.let(secretCipher::encrypt),
        enabled = enabled,
        lastSyncedAt = lastSyncedAt,
        bookCount = bookCount,
        syncState = syncState.name,
        lastErrorMessage = lastErrorMessage,
        createdAt = createdAt,
        syncMode = syncMode.name
    )

    private fun String.toSyncState() = runCatching { OpdsSyncState.valueOf(this) }.getOrDefault(OpdsSyncState.IDLE)
}
