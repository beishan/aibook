package com.aibook.android.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenRouteTest {
    @Test
    fun uiBundleRoutesRemainStable() {
        assertEquals("bookshelf/grid", Screen.Shelf.route)
        assertEquals("bookshelf/list", Screen.ShelfList.route)
        assertEquals("bookshelf/batch", Screen.ShelfBatch.route)
        assertEquals("bookstore/local/scan", Screen.LocalScan.route)
        assertEquals("bookstore/opds", Screen.StoreOpds.route)
        assertEquals("bookstore/backend/recent", Screen.BackendRecent.route)
        assertEquals("bookstore/backend/favorites", Screen.BackendFavorites.route)
        assertEquals("bookstore/backend/booklists", Screen.BackendBooklists.route)
        assertEquals("reading/recent", Screen.RecentReading.route)
        assertEquals("settings/reading", Screen.ReadingSettings.route)
        assertEquals("settings/theme", Screen.ThemeSettings.route)
        assertEquals("settings/backup", Screen.BackupRestore.route)
        assertEquals("settings/about", Screen.About.route)
    }

    @Test
    fun parameterizedRoutesEncodeExternalIdentifiers() {
        assertEquals(
            "bookstore/opds/family/category/http%3A%2F%2Fnas%2Fopds%2Fcategory%3Fname%3D%E7%A7%91%E5%B9%BB",
            Screen.OpdsCategoryBooks.createRoute("family", "http://nas/opds/category?name=科幻")
        )
        assertEquals("bookstore/backend/booklist/42", Screen.BackendBookListDetail.createRoute(42))
    }
}
