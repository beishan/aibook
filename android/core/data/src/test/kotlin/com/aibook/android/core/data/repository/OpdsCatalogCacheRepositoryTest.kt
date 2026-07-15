package com.aibook.android.core.data.repository

import com.aibook.android.core.data.db.OpdsCatalogEntryDao
import com.aibook.android.core.data.db.OpdsCatalogEntryEntity
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsFeed
import com.aibook.android.core.network.opds.OpdsLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OpdsCatalogCacheRepositoryTest {

    @Test
    fun replaceConnectionEntriesKeepsOnlyAcquisitionEntriesAndReplacesOldRows() = runTest {
        val dao = FakeOpdsCatalogEntryDao()
        val repository = OpdsCatalogCacheRepository(dao)
        val connection = OpdsConnection(
            id = "source-1",
            name = "家庭书库",
            baseUrl = "https://books.example/opds"
        )

        repository.replaceConnectionEntries(
            connection = connection,
            feed = OpdsFeed(
                title = "root",
                entries = listOf(
                    OpdsEntry(
                        title = "银河帝国",
                        author = "Isaac Asimov",
                        summary = "Foundation",
                        acquisitionLink = OpdsLink("/books/foundation.epub", "application/epub+zip"),
                        alternateLink = OpdsLink("/category/sci-fi"),
                        coverLink = OpdsLink("/covers/foundation.jpg")
                    ),
                    OpdsEntry(
                        title = "科幻分类",
                        alternateLink = OpdsLink("/category/sci-fi")
                    )
                )
            ),
            syncedAt = 10L
        )

        assertEquals(1, dao.current().size)
        assertEquals("银河帝国", dao.current().single().title)
        assertEquals("EPUB", dao.current().single().format)
        assertEquals("家庭书库", dao.current().single().sourceName)

        repository.replaceConnectionEntries(
            connection = connection.copy(name = "新书库"),
            feed = OpdsFeed(
                title = "root",
                entries = listOf(
                    OpdsEntry(
                        title = "沙丘",
                        author = "Frank Herbert",
                        acquisitionLink = OpdsLink("https://cdn.example/dune.pdf", "application/pdf")
                    )
                )
            ),
            syncedAt = 20L
        )

        assertEquals(listOf("沙丘"), dao.current().map { it.title })
        assertEquals("PDF", dao.current().single().format)
        assertEquals("新书库", dao.current().single().sourceName)
        assertEquals(20L, dao.current().single().syncedAt)
    }

    @Test
    fun deleteByConnectionRemovesOnlyThatSourcesCache() = runTest {
        val dao = FakeOpdsCatalogEntryDao()
        val repository = OpdsCatalogCacheRepository(dao)

        dao.replaceForConnection(
            connectionId = "source-1",
            entries = listOf(sampleEntity(id = "one", connectionId = "source-1", title = "一号"))
        )
        dao.replaceForConnection(
            connectionId = "source-2",
            entries = listOf(sampleEntity(id = "two", connectionId = "source-2", title = "二号"))
        )

        repository.deleteByConnection("source-1")

        assertEquals(listOf("二号"), dao.current().map { it.title })
    }

    private fun sampleEntity(id: String, connectionId: String, title: String) =
        OpdsCatalogEntryEntity(
            id = id,
            connectionId = connectionId,
            sourceName = "源",
            title = title,
            acquisitionHref = "/$id.epub",
            format = "EPUB",
            syncedAt = 1L
        )

    private class FakeOpdsCatalogEntryDao : OpdsCatalogEntryDao() {
        private val rows = linkedMapOf<String, OpdsCatalogEntryEntity>()
        private val flow = MutableStateFlow<List<OpdsCatalogEntryEntity>>(emptyList())

        fun current(): List<OpdsCatalogEntryEntity> = rows.values.toList()

        override fun observeAll(): Flow<List<OpdsCatalogEntryEntity>> = flow

        override suspend fun getByConnection(connectionId: String): List<OpdsCatalogEntryEntity> =
            rows.values.filter { it.connectionId == connectionId }

        override suspend fun countByConnection(connectionId: String): Int =
            rows.values.count { it.connectionId == connectionId }

        override suspend fun insertAll(entries: List<OpdsCatalogEntryEntity>) {
            entries.forEach { rows[it.id] = it }
            flow.value = rows.values.toList()
        }

        override suspend fun replaceForConnection(
            connectionId: String,
            entries: List<OpdsCatalogEntryEntity>
        ) {
            rows.entries.removeIf { it.value.connectionId == connectionId }
            entries.forEach { rows[it.id] = it }
            flow.value = rows.values.toList()
        }

        override suspend fun deleteByConnection(connectionId: String) {
            rows.entries.removeIf { it.value.connectionId == connectionId }
            flow.value = rows.values.toList()
        }
    }
}
