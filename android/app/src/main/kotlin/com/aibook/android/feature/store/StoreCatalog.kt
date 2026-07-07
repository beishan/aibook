package com.aibook.android.feature.store

object StoreCatalog {
    const val LOCAL_SOURCE_ID = "local"

    fun aggregate(
        localBooks: List<LocalInput>,
        opdsEntries: List<OpdsInput>,
        enabledConnectionIds: Set<String>
    ): List<StoreBook> {
        val localByTitleAndFormat = localBooks.associateBy { downloadMatchKey(it.title, it.format) }
        val localItems = localBooks
            .filter { it.visibleInStore }
            .map {
                StoreBook(
                    id = it.id,
                    title = it.title,
                    author = it.author ?: "未知作者",
                    summary = null,
                    sourceId = LOCAL_SOURCE_ID,
                    sourceName = "本地",
                    kind = StoreItemKind.LOCAL,
                    format = it.format,
                    categories = listOf(it.format),
                    updatedRank = it.importedAtEpochSeconds,
                    acquisitionHref = null,
                    acquisitionType = null,
                    downloadedLocalId = it.id,
                    coverUri = it.coverUri,
                    shelved = it.shelved
                )
            }
        val opdsItems = opdsEntries
            .filter { it.connectionId in enabledConnectionIds }
            .map {
                val downloadedLocal = localByTitleAndFormat[downloadMatchKey(it.title, it.format)]
                StoreBook(
                    id = it.id,
                    title = it.title,
                    author = it.author ?: "未知作者",
                    summary = it.summary,
                    sourceId = it.connectionId,
                    sourceName = it.sourceName,
                    kind = StoreItemKind.OPDS,
                    format = it.format,
                    categories = (it.categories + it.format).distinct(),
                    updatedRank = it.syncedAt,
                    acquisitionHref = it.acquisitionHref,
                    acquisitionType = it.acquisitionType,
                    downloadedLocalId = downloadedLocal?.id,
                    coverUri = downloadedLocal?.coverUri,
                    shelved = downloadedLocal?.shelved == true
                )
            }
        return (opdsItems + localItems).sortedWith(StoreSortOption.RECENT.comparator)
    }

    fun filterAndSort(
        books: List<StoreBook>,
        filter: StoreCatalogFilter
    ): List<StoreBook> {
        val normalizedQuery = filter.query.trim()
        return books
            .asSequence()
            .filter { filter.sourceId == null || it.sourceId == filter.sourceId }
            .filter { filter.format == null || it.format.equals(filter.format, ignoreCase = true) }
            .filter { filter.category == null || it.categories.any { category -> category == filter.category } }
            .filter {
                normalizedQuery.isBlank() ||
                    it.title.contains(normalizedQuery, ignoreCase = true) ||
                    it.author.contains(normalizedQuery, ignoreCase = true) ||
                    it.sourceName.contains(normalizedQuery, ignoreCase = true) ||
                    it.categories.any { category -> category.contains(normalizedQuery, ignoreCase = true) }
            }
            .sortedWith(filter.sort.comparator)
            .toList()
    }

    fun optionsFor(books: List<StoreBook>): StoreCatalogOptions {
        return StoreCatalogOptions(
            sourceOptions = books
                .map { it.sourceId to it.sourceName }
                .distinctBy { it.first },
            formatOptions = books
                .map { it.format }
                .distinct()
                .sorted(),
            categoryOptions = books
                .flatMap { it.categories }
                .distinct()
                .sorted()
        )
    }

    data class LocalInput(
        val id: String,
        val title: String,
        val author: String?,
        val format: String,
        val importedAtEpochSeconds: Long,
        val visibleInStore: Boolean = true,
        val coverUri: String? = null,
        val shelved: Boolean = false
    )

    data class OpdsInput(
        val id: String,
        val connectionId: String,
        val sourceName: String,
        val title: String,
        val author: String?,
        val summary: String? = null,
        val format: String,
        val categories: List<String>,
        val syncedAt: Long,
        val acquisitionHref: String? = null,
        val acquisitionType: String? = null
    )

    private fun downloadMatchKey(title: String, format: String): String {
        return "${title.trim().lowercase()}|${format.trim().lowercase()}"
    }
}

data class StoreCatalogOptions(
    val sourceOptions: List<Pair<String, String>> = emptyList(),
    val formatOptions: List<String> = emptyList(),
    val categoryOptions: List<String> = emptyList()
)

data class StoreBook(
    val id: String,
    val title: String,
    val author: String,
    val summary: String?,
    val sourceId: String,
    val sourceName: String,
    val kind: StoreItemKind,
    val format: String,
    val categories: List<String>,
    val updatedRank: Long,
    val acquisitionHref: String?,
    val acquisitionType: String?,
    val downloadedLocalId: String? = null,
    val coverUri: String? = null,
    val shelved: Boolean = false
) {
    val isDownloaded: Boolean get() = downloadedLocalId != null
}

data class StoreCatalogFilter(
    val sourceId: String? = null,
    val format: String? = null,
    val category: String? = null,
    val query: String = "",
    val sort: StoreSortOption = StoreSortOption.RECENT
)

enum class StoreItemKind {
    LOCAL,
    OPDS
}

enum class StoreSortOption(
    val label: String,
    val comparator: Comparator<StoreBook>
) {
    RECENT("最近更新", compareByDescending<StoreBook> { it.updatedRank }.thenBy { it.title }),
    TITLE("书名", compareBy<StoreBook> { it.title }),
    AUTHOR("作者", compareBy<StoreBook> { it.author }.thenBy { it.title }),
    SOURCE("来源", compareBy<StoreBook> { it.sourceName }.thenBy { it.title })
}
