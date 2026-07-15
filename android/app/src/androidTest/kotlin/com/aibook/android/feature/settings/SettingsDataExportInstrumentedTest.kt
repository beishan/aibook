package com.aibook.android.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ReadingProgress
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDataExportInstrumentedTest {
    @Test
    fun exportIncludesEditableMetadataAndProgressWithoutAbsolutePath() {
        val json = exportLibraryJson(
            books = listOf(
                LocalBook(
                    id = "book-1",
                    title = "三体",
                    author = "刘慈欣",
                    rating = 9.5f,
                    tags = listOf("科幻", "经典"),
                    format = BookFormat.EPUB,
                    uri = "/private/books/three-body.epub",
                    progress = ReadingProgress(percent = 0.42f, chapterTitle = "红岸基地")
                )
            ),
            folders = listOf("folder-1" to "科幻")
        )

        val book = JSONObject(json).getJSONArray("books").getJSONObject(0)
        assertEquals("three-body.epub", book.getString("fileName"))
        assertEquals(9.5, book.getDouble("rating"), 0.001)
        assertEquals(0.42, book.getDouble("progress"), 0.001)
        assertEquals(2, book.getJSONArray("tags").length())
        assertFalse(json.contains("/private/books"))
    }
}
