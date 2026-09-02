package com.aibook.android.navigation

sealed class Screen(val route: String) {
    data object Shelf : Screen("shelf")
    data object ShelfFolders : Screen("shelf-folders")
    data object ShelfFolderDetail : Screen("shelf-folder/{folderId}") {
        fun createRoute(folderId: String) = "shelf-folder/$folderId"
    }
    data object NewShelfFolder : Screen("shelf-folder-new")
    data object RecentReading : Screen("recent-reading")
    data object Store : Screen("store")
    data object StoreOpds : Screen("store-opds")
    data object ServerLibrary : Screen("server-library")
    data object ServerLibraryDetail : Screen("server-library/{section}") {
        fun createRoute(section: String) = "server-library/$section"
    }
    data object NewBookList : Screen("booklist/new")
    data object EditBookList : Screen("booklist/{listId}/edit") {
        fun createRoute(listId: Long) = "booklist/$listId/edit"
    }
    data object ShelfSortFilter : Screen("shelf-sort-filter")
    data object StoreCategory : Screen("store-category")
    data object StoreSearch : Screen("store-search")
    data object StoreRemoteBookDetail : Screen("store-remote-book/{bookId}") {
        fun createRoute(bookId: String) = "store-remote-book/$bookId"
    }
    data object Opds : Screen("opds")
    data object ImportBooks : Screen("import-books")
    data object OpdsAddSource : Screen("opds-add-source") {
        fun createRoute(connectionId: String? = null): String =
            if (connectionId != null) "opds-add-source?connectionId=$connectionId"
            else "opds-add-source"
    }
    data object Settings : Screen("settings")
    data object ThemeSettings : Screen("theme-settings")
    data object ShelfSettings : Screen("shelf-settings")
    data object ScanDirectories : Screen("scan-directories")
    data object SyncConnectionSettings : Screen("sync-connection-settings")
    data object StorageCache : Screen("storage-cache")
    data object Downloads : Screen("downloads")
    data object DownloadDetail : Screen("downloads/{taskId}") {
        fun createRoute(taskId: String) = "downloads/$taskId"
    }
    data object PrivacyPermissions : Screen("privacy-permissions")
    data object About : Screen("about")
    data object BackupRestore : Screen("backup-restore")
    data object BookDetail : Screen("book/{bookId}") {
        fun createRoute(bookId: String) = "book/$bookId"
    }
    data object BookSources : Screen("book/{bookId}/sources") {
        fun createRoute(bookId: String) = "book/$bookId/sources"
    }
    data object RemoteBookDetail : Screen("remote-book/{bookId}") {
        fun createRoute(bookId: Long) = "remote-book/$bookId"
    }
    data object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }
    data object RemoteReader : Screen("remote-reader/{bookId}") {
        fun createRoute(bookId: Long) = "remote-reader/$bookId"
    }
}
