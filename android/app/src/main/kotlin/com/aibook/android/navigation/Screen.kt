package com.aibook.android.navigation

sealed class Screen(val route: String) {
    data object Shelf : Screen("bookshelf/grid")
    data object ShelfList : Screen("bookshelf/list")
    data object ShelfFolders : Screen("bookshelf/folders")
    data object ShelfFolderDetail : Screen("bookshelf/folder/{folderId}") {
        fun createRoute(folderId: String) = "bookshelf/folder/$folderId"
    }
    data object NewShelfFolder : Screen("bookshelf/folder/new")
    data object ShelfBatch : Screen("bookshelf/batch")
    data object RecentReading : Screen("reading/recent")
    data object Store : Screen("bookstore/local")
    data object StoreOpds : Screen("bookstore/opds")
    data object OpdsServiceDetail : Screen("bookstore/opds/{serviceId}") {
        fun createRoute(serviceId: String) = "bookstore/opds/$serviceId"
    }
    data object OpdsCategories : Screen("bookstore/opds/{serviceId}/categories") {
        fun createRoute(serviceId: String) = "bookstore/opds/$serviceId/categories"
    }
    data object OpdsCategoryBooks : Screen("bookstore/opds/{serviceId}/category/{categoryId}") {
        fun createRoute(serviceId: String, categoryId: String) =
            "bookstore/opds/${routeEncode(serviceId)}/category/${routeEncode(categoryId)}"
    }
    data object ServerLibrary : Screen("bookstore/backend")
    data object BackendRecent : Screen("bookstore/backend/recent")
    data object BackendFavorites : Screen("bookstore/backend/favorites")
    data object BackendBooklists : Screen("bookstore/backend/booklists")
    data object BackendBookListDetail : Screen("bookstore/backend/booklist/{listId}") {
        fun createRoute(listId: Long) = "bookstore/backend/booklist/$listId"
    }
    data object NewBookList : Screen("bookstore/backend/booklist/new")
    data object EditBookList : Screen("bookstore/backend/booklist/{listId}/edit") {
        fun createRoute(listId: Long) = "bookstore/backend/booklist/$listId/edit"
    }
    data object ShelfSortFilter : Screen("bookshelf/sort-filter")
    data object StoreCategory : Screen("bookstore/local/category")
    data object StoreSearch : Screen("search")
    data object StoreSearchResults : Screen("search?query={query}") {
        fun createRoute(query: String) = "search?query=${routeEncode(query)}"
    }
    data object StoreRemoteBookDetail : Screen("bookstore/book/{bookId}") {
        fun createRoute(bookId: String) = "bookstore/book/$bookId"
    }
    data object Opds : Screen("discovery")
    data object ImportBooks : Screen("bookstore/local/import")
    data object LocalScan : Screen("bookstore/local/scan")
    data object ScanResult : Screen("bookstore/local/scan-result")
    data object OpdsAddSource : Screen("bookstore/opds/add") {
        fun createRoute(connectionId: String? = null): String =
            if (connectionId != null) "bookstore/opds/add?connectionId=$connectionId"
            else "bookstore/opds/add"
    }
    data object Settings : Screen("settings")
    data object ReadingSettings : Screen("settings/reading")
    data object ThemeSettings : Screen("settings/theme")
    data object ShelfSettings : Screen("settings/bookshelf")
    data object ScanDirectories : Screen("bookstore/local/scan-directories")
    data object SyncConnectionSettings : Screen("sync-connection-settings")
    data object StorageCache : Screen("storage-cache")
    data object Downloads : Screen("downloads")
    data object DownloadDetail : Screen("downloads/{taskId}") {
        fun createRoute(taskId: String) = "downloads/$taskId"
    }
    data object PrivacyPermissions : Screen("privacy-permissions")
    data object About : Screen("settings/about")
    data object BackupRestore : Screen("settings/backup")
    data object BookDetail : Screen("book/{bookId}") {
        fun createRoute(bookId: String) = "book/$bookId"
    }
    data object BookSources : Screen("book/{bookId}/sources") {
        fun createRoute(bookId: String) = "book/$bookId/sources"
    }
    data object RemoteBookDetail : Screen("bookstore/backend/book/{bookId}") {
        fun createRoute(bookId: Long) = "bookstore/backend/book/$bookId"
    }
    data object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }
    data object RemoteReader : Screen("remote-reader/{bookId}") {
        fun createRoute(bookId: Long) = "remote-reader/$bookId"
    }
}

private fun routeEncode(value: String): String =
    java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.toString()).replace("+", "%20")
