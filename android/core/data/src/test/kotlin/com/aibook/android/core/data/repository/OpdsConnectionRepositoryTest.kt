package com.aibook.android.core.data.repository

import com.aibook.android.core.data.db.OpdsConnectionDao
import com.aibook.android.core.data.db.OpdsConnectionEntity
import com.aibook.android.core.network.opds.OpdsSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OpdsConnectionRepositoryTest {

    @Test
    fun updateConnectionFieldsPreservesOperationalState() = runTest {
        val dao = FakeOpdsConnectionDao()
        dao.insert(
            OpdsConnectionEntity(
                id = "source-1",
                name = "旧源",
                baseUrl = "https://old.example/opds",
                username = "old-user",
                passwordCiphertext = "old-pass",
                enabled = false,
                lastSyncedAt = 123L,
                bookCount = 8,
                syncState = OpdsSyncState.FAILED.name,
                lastErrorMessage = "timeout",
                createdAt = 99L
            )
        )
        val repository = OpdsConnectionRepository(dao)

        val updated = repository.updateConnectionFields(
            id = "source-1",
            name = "新源",
            baseUrl = "https://new.example/opds",
            username = "",
            password = "secret"
        )

        assertEquals("新源", updated?.name)
        assertEquals("https://new.example/opds", updated?.baseUrl)
        assertEquals(null, updated?.username)
        assertEquals("secret", updated?.password)
        assertFalse(updated?.enabled ?: true)
        assertEquals(123L, updated?.lastSyncedAt)
        assertEquals(8, updated?.bookCount)
        assertEquals(OpdsSyncState.FAILED, updated?.syncState)
        assertEquals("timeout", updated?.lastErrorMessage)
        assertEquals(99L, dao.current("source-1")?.createdAt)
    }

    @Test
    fun syncStateHelpersClearAndKeepErrorFieldsCorrectly() = runTest {
        val dao = FakeOpdsConnectionDao()
        dao.insert(
            OpdsConnectionEntity(
                id = "source-1",
                name = "源",
                baseUrl = "https://example.com/opds",
                lastSyncedAt = 100L,
                bookCount = 3,
                syncState = OpdsSyncState.FAILED.name,
                lastErrorMessage = "old error"
            )
        )
        val repository = OpdsConnectionRepository(dao)

        repository.markSyncing("source-1")
        assertEquals(OpdsSyncState.SYNCING.name, dao.current("source-1")?.syncState)
        assertEquals(null, dao.current("source-1")?.lastErrorMessage)

        repository.markSyncSuccess("source-1", lastSyncedAt = 200L, bookCount = 12)
        assertEquals(OpdsSyncState.SUCCESS.name, dao.current("source-1")?.syncState)
        assertEquals(200L, dao.current("source-1")?.lastSyncedAt)
        assertEquals(12, dao.current("source-1")?.bookCount)
        assertEquals(null, dao.current("source-1")?.lastErrorMessage)

        repository.markSyncFailed("source-1", "HTTP 500")
        assertEquals(OpdsSyncState.FAILED.name, dao.current("source-1")?.syncState)
        assertEquals(200L, dao.current("source-1")?.lastSyncedAt)
        assertEquals(12, dao.current("source-1")?.bookCount)
        assertEquals("HTTP 500", dao.current("source-1")?.lastErrorMessage)
    }

    private class FakeOpdsConnectionDao : OpdsConnectionDao {
        private val rows = linkedMapOf<String, OpdsConnectionEntity>()
        private val flow = MutableStateFlow<List<OpdsConnectionEntity>>(emptyList())

        fun current(id: String): OpdsConnectionEntity? = rows[id]

        override fun observeAll(): Flow<List<OpdsConnectionEntity>> = flow

        override suspend fun getAll(): List<OpdsConnectionEntity> = rows.values.toList()

        override suspend fun getById(id: String): OpdsConnectionEntity? = rows[id]

        override suspend fun insert(connection: OpdsConnectionEntity) {
            rows[connection.id] = connection
            flow.value = rows.values.toList()
        }

        override suspend fun updateConnectionFields(
            id: String,
            name: String,
            baseUrl: String,
            username: String?,
            passwordCiphertext: String?,
            syncMode: String
        ) {
            rows[id]?.let {
                insert(it.copy(name = name, baseUrl = baseUrl, username = username, passwordCiphertext = passwordCiphertext, syncMode = syncMode))
            }
        }

        override suspend fun updatePasswordCiphertext(id: String, passwordCiphertext: String?) {
            rows[id]?.let { insert(it.copy(passwordCiphertext = passwordCiphertext)) }
        }

        override suspend fun updateEnabled(id: String, enabled: Boolean) {
            rows[id]?.let { insert(it.copy(enabled = enabled)) }
        }

        override suspend fun updateSyncState(
            id: String,
            syncState: String,
            lastSyncedAt: Long?,
            bookCount: Int?,
            lastErrorMessage: String?
        ) {
            rows[id]?.let {
                insert(
                    it.copy(
                        syncState = syncState,
                        lastSyncedAt = lastSyncedAt ?: it.lastSyncedAt,
                        bookCount = bookCount ?: it.bookCount,
                        lastErrorMessage = lastErrorMessage
                    )
                )
            }
        }

        override suspend fun deleteById(id: String) {
            rows.remove(id)
            flow.value = rows.values.toList()
        }
    }
}
