package com.aibook.android.feature.server

import com.aibook.android.background.DownloadQueueManager
import com.aibook.android.core.data.repository.DownloadStatus
import com.aibook.android.core.data.repository.DownloadTask
import com.aibook.android.core.model.LocalBook
import com.aibook.android.feature.reader.RemoteReaderSource
import com.aibook.android.feature.reader.resolveRemoteReaderSource

internal enum class BackendBookDownloadState(val label: String, val canDownload: Boolean = false) {
    CHECKING("检查下载状态…"),
    AVAILABLE("下载到本地", true),
    DOWNLOADED("已下载"),
    QUEUED("等待下载"),
    RUNNING("下载中"),
    PAUSED("下载已暂停"),
    FAILED("重新下载", true)
}

internal fun resolveBackendBookDownloadState(
    bookId: Long,
    books: List<LocalBook>,
    tasks: List<DownloadTask>
): BackendBookDownloadState {
    if (resolveRemoteReaderSource(bookId, books) is RemoteReaderSource.Downloaded) {
        return BackendBookDownloadState.DOWNLOADED
    }
    val task = tasks.filter {
        it.connectionId == DownloadQueueManager.SERVER_CONNECTION_ID &&
            it.remoteEntryId == "server:$bookId"
    }.maxByOrNull { it.createdAt }
    return when (task?.status) {
        DownloadStatus.QUEUED -> BackendBookDownloadState.QUEUED
        DownloadStatus.RUNNING -> BackendBookDownloadState.RUNNING
        DownloadStatus.PAUSED -> BackendBookDownloadState.PAUSED
        DownloadStatus.FAILED -> BackendBookDownloadState.FAILED
        else -> BackendBookDownloadState.AVAILABLE
    }
}
