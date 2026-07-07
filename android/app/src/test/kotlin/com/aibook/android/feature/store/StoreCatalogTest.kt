package com.aibook.android.feature.store

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreCatalogTest {

    @Test
    fun filterAppliesSourceFormatCategoryAndQueryTogether() {
        val books = listOf(
            storeBook("local-1", "本地 EPUB", "王小明", "本地", "EPUB", listOf("EPUB", "文学"), StoreItemKind.LOCAL),
            storeBook("opds-1", "远程科幻", "刘小明", "家庭书库", "PDF", listOf("科幻"), StoreItemKind.OPDS),
            storeBook("opds-2", "远程历史", "陈老师", "家庭书库", "EPUB", listOf("历史"), StoreItemKind.OPDS)
        )

        val filtered = StoreCatalog.filterAndSort(
            books = books,
            filter = StoreCatalogFilter(
                sourceId = "source-family",
                format = "PDF",
                category = "科幻",
                query = "小明",
                sort = StoreSortOption.TITLE
            )
        )

        assertEquals(listOf("远程科幻"), filtered.map { it.title })
    }

    @Test
    fun disabledOpdsSourcesAreExcludedFromAggregationInputs() {
        val books = StoreCatalog.aggregate(
            localBooks = listOf(
                StoreCatalog.LocalInput(
                    id = "local-1",
                    title = "本地书",
                    author = null,
                    format = "EPUB",
                    importedAtEpochSeconds = 20L
                )
            ),
            opdsEntries = listOf(
                StoreCatalog.OpdsInput(
                    id = "enabled-book",
                    connectionId = "enabled",
                    sourceName = "启用源",
                    title = "启用书",
                    author = "作者",
                    format = "TXT",
                    categories = listOf("小说"),
                    syncedAt = 30L
                ),
                StoreCatalog.OpdsInput(
                    id = "disabled-book",
                    connectionId = "disabled",
                    sourceName = "停用源",
                    title = "停用书",
                    author = "作者",
                    format = "TXT",
                    categories = listOf("小说"),
                    syncedAt = 40L
                )
            ),
            enabledConnectionIds = setOf("enabled")
        )

        assertEquals(listOf("启用书", "本地书"), books.map { it.title })
    }

    @Test
    fun opdsEntryMatchingLocalTitleAndFormatLinksToDownloadedLocalBook() {
        val books = StoreCatalog.aggregate(
            localBooks = listOf(
                StoreCatalog.LocalInput(
                    id = "local-foundation",
                    title = "银河帝国",
                    author = "Isaac Asimov",
                    format = "EPUB",
                    importedAtEpochSeconds = 50L
                )
            ),
            opdsEntries = listOf(
                StoreCatalog.OpdsInput(
                    id = "remote-foundation",
                    connectionId = "source-1",
                    sourceName = "家庭书库",
                    title = "银河帝国",
                    author = "Isaac Asimov",
                    format = "EPUB",
                    categories = listOf("科幻"),
                    syncedAt = 80L,
                    acquisitionHref = "/foundation.epub"
                )
            ),
            enabledConnectionIds = setOf("source-1")
        )

        val remote = books.single { it.kind == StoreItemKind.OPDS }
        assertEquals(true, remote.isDownloaded)
        assertEquals("local-foundation", remote.downloadedLocalId)
    }

    @Test
    fun hiddenLocalBooksAreExcludedFromStoreCatalog() {
        val books = StoreCatalog.aggregate(
            localBooks = listOf(
                StoreCatalog.LocalInput(
                    id = "visible-local",
                    title = "可见本地书",
                    author = "作者",
                    format = "EPUB",
                    importedAtEpochSeconds = 50L,
                    visibleInStore = true
                ),
                StoreCatalog.LocalInput(
                    id = "hidden-local",
                    title = "已移出本地书",
                    author = "作者",
                    format = "TXT",
                    importedAtEpochSeconds = 60L,
                    visibleInStore = false
                )
            ),
            opdsEntries = emptyList(),
            enabledConnectionIds = emptySet()
        )

        assertEquals(listOf("可见本地书"), books.map { it.title })
    }

    @Test
    fun hiddenLocalBooksStillMatchDownloadedRemoteEntries() {
        val books = StoreCatalog.aggregate(
            localBooks = listOf(
                StoreCatalog.LocalInput(
                    id = "hidden-local-foundation",
                    title = "银河帝国",
                    author = "Isaac Asimov",
                    format = "EPUB",
                    importedAtEpochSeconds = 50L,
                    visibleInStore = false
                )
            ),
            opdsEntries = listOf(
                StoreCatalog.OpdsInput(
                    id = "remote-foundation",
                    connectionId = "source-1",
                    sourceName = "家庭书库",
                    title = "银河帝国",
                    author = "Isaac Asimov",
                    format = "EPUB",
                    categories = listOf("科幻"),
                    syncedAt = 80L,
                    acquisitionHref = "/foundation.epub"
                )
            ),
            enabledConnectionIds = setOf("source-1")
        )

        assertEquals(listOf("银河帝国"), books.map { it.title })
        assertEquals("hidden-local-foundation", books.single().downloadedLocalId)
    }

    @Test
    fun localCoverUriIsPreservedForStoreCards() {
        val books = StoreCatalog.aggregate(
            localBooks = listOf(
                StoreCatalog.LocalInput(
                    id = "local-cover",
                    title = "有封面的本地书",
                    author = "作者",
                    format = "EPUB",
                    importedAtEpochSeconds = 50L,
                    coverUri = "/covers/local-cover.jpg"
                )
            ),
            opdsEntries = emptyList(),
            enabledConnectionIds = emptySet()
        )

        assertEquals("/covers/local-cover.jpg", books.single().coverUri)
    }

    @Test
    fun localShelvedStateIsPreservedForStoreActions() {
        val books = StoreCatalog.aggregate(
            localBooks = listOf(
                StoreCatalog.LocalInput(
                    id = "local-shelved",
                    title = "已上架本地书",
                    author = "作者",
                    format = "EPUB",
                    importedAtEpochSeconds = 50L,
                    shelved = true
                )
            ),
            opdsEntries = emptyList(),
            enabledConnectionIds = emptySet()
        )

        assertEquals(true, books.single().shelved)
    }

    @Test
    fun opdsEntrySummaryIsPreservedForRemoteDetail() {
        val books = StoreCatalog.aggregate(
            localBooks = emptyList(),
            opdsEntries = listOf(
                StoreCatalog.OpdsInput(
                    id = "remote-foundation",
                    connectionId = "source-1",
                    sourceName = "家庭书库",
                    title = "银河帝国",
                    author = "Isaac Asimov",
                    summary = "基地系列第一部",
                    format = "EPUB",
                    categories = listOf("科幻"),
                    syncedAt = 80L,
                    acquisitionHref = "/foundation.epub"
                )
            ),
            enabledConnectionIds = setOf("source-1")
        )

        assertEquals("基地系列第一部", books.single().summary)
    }

    @Test
    fun optionsExposeDistinctSourcesFormatsAndCategories() {
        val options = StoreCatalog.optionsFor(
            listOf(
                storeBook("local-1", "本地 EPUB", "王小明", "本地", "EPUB", listOf("EPUB", "文学"), StoreItemKind.LOCAL),
                storeBook("remote-1", "远程 PDF", "刘小明", "家庭书库", "PDF", listOf("PDF", "科幻"), StoreItemKind.OPDS),
                storeBook("remote-2", "远程 EPUB", "陈老师", "家庭书库", "EPUB", listOf("EPUB", "科幻"), StoreItemKind.OPDS)
            )
        )

        assertEquals(listOf(StoreCatalog.LOCAL_SOURCE_ID to "本地", "source-family" to "家庭书库"), options.sourceOptions)
        assertEquals(listOf("EPUB", "PDF"), options.formatOptions)
        assertEquals(listOf("EPUB", "PDF", "文学", "科幻"), options.categoryOptions)
    }

    private fun storeBook(
        id: String,
        title: String,
        author: String,
        sourceName: String,
        format: String,
        categories: List<String>,
        kind: StoreItemKind
    ) = StoreBook(
        id = id,
        title = title,
        author = author,
        sourceId = if (kind == StoreItemKind.LOCAL) StoreCatalog.LOCAL_SOURCE_ID else "source-family",
        sourceName = sourceName,
        kind = kind,
        format = format,
        categories = categories,
        summary = null,
        updatedRank = 0L,
        acquisitionHref = null,
        acquisitionType = null
    )
}
