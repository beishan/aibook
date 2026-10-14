package com.aibook.android.feature.server

import com.aibook.android.core.data.repository.DownloadStatus
import com.aibook.android.core.data.repository.DownloadTask
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendBookDownloadStateTest {
    @Test
    fun validLocalCopyIsDownloadedEvenWithoutTaskHistoryOrShelfMembership() {
        val file = File.createTempFile("download-status", ".txt")
        try {
            file.writeText("第一章\n离线正文")
            val book = LocalBook(
                id = "local", title = "书籍", format = BookFormat.TXT,
                uri = file.absolutePath, remoteBookId = 42L,
                shelved = false, visibleInStore = false
            )
            assertEquals(BackendBookDownloadState.DOWNLOADED, resolveBackendBookDownloadState(42, listOf(book), emptyList()))
            assertEquals(BackendBookDownloadState.DOWNLOADED, resolveBackendBookDownloadState(42, listOf(book), listOf(task(DownloadStatus.RUNNING))))
            assertEquals(BackendBookDownloadState.AVAILABLE, resolveBackendBookDownloadState(43, listOf(book), emptyList()))
        } finally {
            file.delete()
        }
    }

    @Test
    fun completedTaskWithMissingOrEmptyFileAllowsDownloadingAgain() {
        val file = File.createTempFile("empty-download", ".txt")
        try {
            val book = LocalBook(
                id = "local", title = "书籍", format = BookFormat.TXT,
                uri = file.absolutePath, remoteBookId = 42L
            )
            val tasks = listOf(task(DownloadStatus.COMPLETED))
            assertEquals(BackendBookDownloadState.AVAILABLE, resolveBackendBookDownloadState(42, listOf(book), tasks))
            file.delete()
            assertEquals(BackendBookDownloadState.AVAILABLE, resolveBackendBookDownloadState(42, listOf(book), tasks))
        } finally {
            file.delete()
        }
    }

    @Test
    fun mapsActiveAndFailedTasksAndPreventsDuplicateDownloads() {
        val expected = mapOf(
            DownloadStatus.QUEUED to BackendBookDownloadState.QUEUED,
            DownloadStatus.RUNNING to BackendBookDownloadState.RUNNING,
            DownloadStatus.PAUSED to BackendBookDownloadState.PAUSED,
            DownloadStatus.FAILED to BackendBookDownloadState.FAILED,
            DownloadStatus.CANCELLED to BackendBookDownloadState.AVAILABLE
        )
        expected.forEach { (status, state) ->
            assertEquals(state, resolveBackendBookDownloadState(42, emptyList(), listOf(task(status))))
        }
        listOf(BackendBookDownloadState.QUEUED, BackendBookDownloadState.RUNNING,
            BackendBookDownloadState.PAUSED, BackendBookDownloadState.DOWNLOADED,
            BackendBookDownloadState.CHECKING).forEach { assertFalse(it.canDownload) }
        assertTrue(BackendBookDownloadState.AVAILABLE.canDownload)
        assertTrue(BackendBookDownloadState.FAILED.canDownload)
    }

    @Test
    fun ignoresOtherSourcesAndBooksAndUsesMostRecentlyCreatedTask() {
        val older = task(DownloadStatus.FAILED)
        val newer = task(DownloadStatus.QUEUED).copy(id = "new", createdAt = 2)
        assertEquals(BackendBookDownloadState.QUEUED, resolveBackendBookDownloadState(42, emptyList(), listOf(newer, older)))
        val unrelated = listOf(
            newer.copy(connectionId = "OPDS"),
            newer.copy(remoteEntryId = "server:43")
        )
        assertEquals(BackendBookDownloadState.AVAILABLE, resolveBackendBookDownloadState(42, emptyList(), unrelated))
    }

    private fun task(status: DownloadStatus) = DownloadTask(
        id = "task", remoteEntryId = "server:42", connectionId = "SERVER",
        title = "书籍", href = "42", fileName = "书籍.txt", status = status,
        progress = 0, downloadedBytes = 0, totalBytes = null, errorMessage = null,
        createdAt = 1, updatedAt = 1
    )
}
