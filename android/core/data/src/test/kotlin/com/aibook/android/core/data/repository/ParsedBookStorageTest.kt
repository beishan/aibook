package com.aibook.android.core.data.repository

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParsedBookStorageTest {

    @Test
    fun deleteRemovesOnlyRequestedBookDirectory() {
        val storage = ParsedBookStorage(createTempDirectory("parsed-storage").toFile())
        storage.directoryFor("book-a").resolve("x").apply { parentFile?.mkdirs(); writeText("x") }
        storage.directoryFor("book-b").mkdirs()

        storage.deleteForBook("book-a")

        assertFalse(storage.directoryFor("book-a").exists())
        assertTrue(storage.directoryFor("book-b").exists())
    }

    @Test
    fun invalidBookIdCannotEscapeRoot() {
        val storage = ParsedBookStorage(createTempDirectory("parsed-safe").toFile())

        assertFailsWith<IllegalArgumentException> { storage.directoryFor("../outside") }
        assertFailsWith<IllegalArgumentException> { storage.directoryFor("book/child") }
    }
}
