package com.aibook.android.core.data.repository

import com.aibook.android.core.data.db.OpdsConnectionDao
import com.aibook.android.core.data.mapper.toDomain
import com.aibook.android.core.data.mapper.toEntity
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsCatalogService
import com.aibook.android.core.network.opds.OpdsFeed
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
        password: String?
    ): OpdsConnection {
        val connection = OpdsConnection(
            id = UUID.nameUUIDFromBytes(baseUrl.toByteArray()).toString(),
            name = name.ifBlank { "OPDS 书库" },
            baseUrl = baseUrl,
            username = username?.ifBlank { null },
            password = password?.ifBlank { null }
        )
        dao.insert(connection.toEntity())
        return connection
    }

    suspend fun deleteConnection(id: String) {
        dao.deleteById(id)
    }

    fun browse(
        catalogService: OpdsCatalogService,
        connection: OpdsConnection,
        href: String? = null
    ): OpdsFeed {
        return catalogService.load(connection, href)
    }
}
