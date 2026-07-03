package com.aibook.android.navigation

sealed class Screen(val route: String) {
    data object Shelf : Screen("shelf")
    data object Store : Screen("store")
    data object StoreCategory : Screen("store-category")
    data object Opds : Screen("opds")
    data object OpdsAddSource : Screen("opds-add-source")
    data object Settings : Screen("settings")
    data object ThemeSettings : Screen("theme-settings")
    data object ScanDirectories : Screen("scan-directories")
    data object StorageCache : Screen("storage-cache")
    data object PrivacyPermissions : Screen("privacy-permissions")
    data object About : Screen("about")
    data object BookDetail : Screen("book/{bookId}") {
        fun createRoute(bookId: String) = "book/$bookId"
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
