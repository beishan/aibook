package com.aibook.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BookFormatTest {

    @Test
    fun kindleExtensionsAreRecognizedCaseInsensitively() {
        assertEquals(BookFormat.MOBI, BookFormat.fromFileName("book.mobi"))
        assertEquals(BookFormat.AZW3, BookFormat.fromFileName("BOOK.AZW3"))
    }
}
