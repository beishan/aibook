package com.aibook.android.navigation

sealed class Screen(val route: String) {
    data object Shelf : Screen("shelf")
    data object Opds : Screen("opds")
    data object Settings : Screen("settings")
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
