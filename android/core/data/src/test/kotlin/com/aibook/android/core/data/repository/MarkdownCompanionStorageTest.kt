package com.aibook.android.core.data.repository

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest

class MarkdownCompanionStorageTest {

    @Test
    fun copiesReferencedCompanionsBesidePrivateMarkdownOnIoDispatcher() = runTest {
        val root = Files.createTempDirectory("markdown-private-book").toFile()
        val markdown = root.resolve("book.md").apply { writeText("![cover](images/a.png)") }
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "import-test-io") }
        val dispatcher = executor.asCoroutineDispatcher()
        var openThread: String? = null
        try {
            val storage = MarkdownCompanionStorage(dispatcher)

            storage.copy(markdown, listOf("images/a.png")) { path ->
                openThread = Thread.currentThread().name
                if (path == "images/a.png") ByteArrayInputStream(byteArrayOf(1, 2, 3)) else null
            }

            assertContentEquals(byteArrayOf(1, 2, 3), root.resolve("images/a.png").readBytes())
            assertEquals("import-test-io", openThread)
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun rejectsEscapeAbsoluteSchemeAndNonRasterPathsBeforeOpeningSource() = runTest {
        val root = Files.createTempDirectory("markdown-private-book-unsafe").toFile()
        val markdown = root.resolve("book.md").apply { writeText("body") }
        val opened = mutableListOf<String>()
        val storage = MarkdownCompanionStorage()

        storage.copy(
            markdown,
            listOf("../secret.png", "/absolute.png", "https://example.com/a.png", "assets/data.json")
        ) { path ->
            opened += path
            ByteArrayInputStream(byteArrayOf(9))
        }

        assertEquals(emptyList(), opened)
        assertFalse(root.resolve("secret.png").exists())
    }

    @Test
    fun resourceCountLimitDoesNotOpenOrLeaveFilesBeyondBudget() = runTest {
        val root = Files.createTempDirectory("markdown-resource-count").toFile()
        val markdown = root.resolve("book.md").apply { writeText("body") }
        val opened = mutableListOf<String>()
        val storage = MarkdownCompanionStorage(maxResources = 2, maxTotalBytes = 100, maxResourceBytes = 100)

        storage.copy(markdown, listOf("1.png", "2.png", "3.png")) { path ->
            opened += path
            ByteArrayInputStream(byteArrayOf(1))
        }

        assertEquals(listOf("1.png", "2.png"), opened)
        assertFalse(root.resolve("3.png").exists())
        assertFalse(root.walkTopDown().any { it.name.endsWith(".part") })
    }

    @Test
    fun totalByteLimitRemovesPartialFileAndStopsOpeningMoreResources() = runTest {
        val root = Files.createTempDirectory("markdown-resource-total").toFile()
        val markdown = root.resolve("book.md").apply { writeText("body") }
        val opened = mutableListOf<String>()
        val storage = MarkdownCompanionStorage(maxResources = 10, maxTotalBytes = 5, maxResourceBytes = 4)

        storage.copy(markdown, listOf("1.png", "2.png", "3.png")) { path ->
            opened += path
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        }

        assertContentEquals(byteArrayOf(1, 2, 3, 4), root.resolve("1.png").readBytes())
        assertFalse(root.resolve("2.png").exists())
        assertFalse(root.resolve("3.png").exists())
        assertEquals(listOf("1.png", "2.png"), opened)
        assertFalse(root.walkTopDown().any { it.name.endsWith(".part") })
    }
}
