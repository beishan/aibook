package com.aibook.android.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.work.workDataOf
import com.aibook.android.R
import com.aibook.android.core.data.db.DownloadTaskEntity
import com.aibook.android.core.data.repository.DownloadStatus
import com.aibook.android.core.data.repository.ImportResult
import com.aibook.android.core.network.opds.OpdsFeed
import com.aibook.android.core.network.opds.OpdsSyncMode
import com.aibook.android.di.ServiceLocator
import com.aibook.android.feature.opds.OpdsSyncCollector
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.io.File
import java.util.concurrent.TimeUnit

class DirectoryScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val stats = ServiceLocator.get(applicationContext).scanDirectoryRepository.scanAllEnabled()
        TaskNotifications.show(applicationContext, SCAN_NOTIFICATION, "目录扫描完成", "扫描 ${stats.scanned} 个文件，新增 ${stats.added + stats.restored} 本，失败 ${stats.failed} 个")
        Result.success()
    }.getOrElse { Result.retry() }

    companion object { const val SCAN_NOTIFICATION = 4101 }
}

class OpdsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val locator = ServiceLocator.get(applicationContext)
        var success = 0
        var failed = 0
        locator.opdsConnectionRepository.getAll().filter { it.enabled }.forEach { connection ->
            locator.opdsConnectionRepository.markSyncing(connection.id)
            runCatching {
                val collection = OpdsSyncCollector { href -> locator.opdsCatalogService.load(connection, href) }.collect()
                val feed = OpdsFeed(connection.name, collection.acquisitionEntries)
                if (connection.syncMode == OpdsSyncMode.INCREMENTAL) {
                    locator.opdsCatalogCacheRepository.mergeConnectionEntries(connection, feed)
                } else {
                    locator.opdsCatalogCacheRepository.replaceConnectionEntries(connection, feed)
                }
                val total = locator.opdsCatalogCacheRepository.countByConnection(connection.id)
                locator.opdsConnectionRepository.markSyncSuccess(connection.id, System.currentTimeMillis(), total)
                success += 1
            }.onFailure {
                locator.opdsConnectionRepository.markSyncFailed(connection.id, it.message ?: "后台同步失败")
                failed += 1
            }
        }
        TaskNotifications.show(applicationContext, OPDS_NOTIFICATION, "OPDS 同步完成", "成功 $success 个，失败 $failed 个数据源")
        return if (failed > 0 && success == 0) Result.retry() else Result.success()
    }

    companion object { const val OPDS_NOTIFICATION = 4102 }
}

class BookDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val locator = ServiceLocator.get(applicationContext)
        val task = locator.downloadTaskRepository.getById(taskId) ?: return Result.failure()
        if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.CANCELLED) return Result.success()
        val connection = locator.opdsConnectionRepository.getById(task.connectionId)
            ?: return fail(taskId, "数据源不存在")
        locator.downloadTaskRepository.update(taskId, DownloadStatus.RUNNING, task.progress, task.downloadedBytes, task.totalBytes)
        setForeground(TaskNotifications.foreground(applicationContext, taskId.hashCode(), task.title, task.progress))
        return try {
            var lastPersistedProgress = -1
            val partial = partialFile(applicationContext, taskId)
            val downloadedFile = locator.opdsCatalogService.downloadTo(
                connection,
                task.href,
                partial,
                onProgress = { downloaded, total ->
                    val progress = total?.takeIf { it > 0 }?.let { (downloaded * 100 / it).toInt() } ?: 0
                    setProgressAsync(workDataOf("progress" to progress, "downloaded" to downloaded, "total" to (total ?: -1L)))
                    if (progress != lastPersistedProgress) {
                        lastPersistedProgress = progress
                        runBlocking { locator.downloadTaskRepository.update(taskId, DownloadStatus.RUNNING, progress, downloaded, total) }
                        TaskNotifications.showProgress(applicationContext, taskId.hashCode(), task.title, progress)
                    }
                },
                isCancelled = { isStopped }
            )
            val bytes = downloadedFile.readBytes()
            locator.downloadTaskRepository.update(taskId, DownloadStatus.RUNNING, 95, bytes.size.toLong(), bytes.size.toLong())
            val result = locator.bookRepository.importDownloadedBook(task.fileName, bytes, task.title)
            if (result is ImportResult.Failed || result is ImportResult.UnsupportedFormat) {
                fail(taskId, if (result is ImportResult.Failed) result.message else "不支持该文件格式")
            } else {
                locator.downloadTaskRepository.update(taskId, DownloadStatus.COMPLETED, 100, bytes.size.toLong(), bytes.size.toLong())
                partial.delete()
                TaskNotifications.show(applicationContext, taskId.hashCode(), "下载完成", task.title)
                Result.success()
            }
        } catch (error: Exception) {
            val latest = locator.downloadTaskRepository.getById(taskId)
            if (latest?.status == DownloadStatus.PAUSED || latest?.status == DownloadStatus.CANCELLED || isStopped) {
                Result.success()
            } else {
                locator.downloadTaskRepository.update(taskId, DownloadStatus.FAILED, task.progress, task.downloadedBytes, task.totalBytes, error.message)
                TaskNotifications.show(applicationContext, taskId.hashCode(), "下载失败", "${task.title}：${error.message ?: "未知错误"}")
                Result.retry()
            }
        }
    }

    private suspend fun fail(id: String, message: String): Result {
        ServiceLocator.get(applicationContext).downloadTaskRepository.update(id, DownloadStatus.FAILED, 0, error = message)
        return Result.failure(workDataOf("error" to message))
    }

    companion object { const val KEY_TASK_ID = "task_id" }
}

object BackgroundWorkScheduler {
    private const val SCAN_WORK = "periodic-directory-scan"
    private const val OPDS_WORK = "periodic-opds-sync"

    fun configureScan(context: Context, intervalHours: Int) {
        val manager = WorkManager.getInstance(context)
        if (intervalHours <= 0) {
            manager.cancelUniqueWork(SCAN_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<DirectoryScanWorker>(intervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
            .build()
        manager.enqueueUniquePeriodicWork(SCAN_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scanNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("startup-directory-scan", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<DirectoryScanWorker>().build())
    }

    fun configureOpds(context: Context, intervalHours: Int, wifiOnly: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (intervalHours <= 0) {
            manager.cancelUniqueWork(OPDS_WORK)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<OpdsSyncWorker>(intervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        manager.enqueueUniquePeriodicWork(OPDS_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class DownloadQueueManager(private val context: Context) {
    private val locator get() = ServiceLocator.get(context)

    suspend fun enqueue(remoteId: String, connectionId: String, title: String, href: String, fileName: String): String {
        val existing = locator.downloadTaskRepository.getLatestByRemoteEntry(remoteId)
        if (existing != null && existing.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED)) return existing.id
        val id = UUID.randomUUID().toString()
        locator.downloadTaskRepository.save(DownloadTaskEntity(id, remoteId, connectionId, title, href, fileName))
        enqueueWork(id)
        return id
    }

    suspend fun pause(id: String) {
        val task = locator.downloadTaskRepository.getById(id) ?: return
        locator.downloadTaskRepository.update(id, DownloadStatus.PAUSED, task.progress, task.downloadedBytes, task.totalBytes)
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    suspend fun resume(id: String) {
        val task = locator.downloadTaskRepository.getById(id) ?: return
        locator.downloadTaskRepository.update(id, DownloadStatus.QUEUED, task.progress, task.downloadedBytes, task.totalBytes)
        enqueueWork(id)
    }

    suspend fun cancel(id: String) {
        val task = locator.downloadTaskRepository.getById(id) ?: return
        locator.downloadTaskRepository.update(id, DownloadStatus.CANCELLED, task.progress, task.downloadedBytes, task.totalBytes)
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        partialFile(context, id).delete()
    }

    suspend fun retry(id: String) = resume(id)

    suspend fun remove(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        partialFile(context, id).delete()
        locator.downloadTaskRepository.delete(id)
    }

    private fun enqueueWork(id: String) {
        val request = OneTimeWorkRequestBuilder<BookDownloadWorker>()
            .setInputData(workDataOf(BookDownloadWorker.KEY_TASK_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String) = "book-download-$id"
}

private fun partialFile(context: Context, id: String): File = File(context.cacheDir, "downloads/$id.part")

private object TaskNotifications {
    private const val CHANNEL = "background_tasks"
    fun show(context: Context, id: Int, title: String, text: String) {
        createChannel(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(id, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).build())
    }

    fun foreground(context: Context, id: Int, title: String, progress: Int): ForegroundInfo {
        createChannel(context)
        return ForegroundInfo(id, progressNotification(context, title, progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    fun showProgress(context: Context, id: Int, title: String, progress: Int) {
        createChannel(context)
        NotificationManagerCompat.from(context).notify(id, progressNotification(context, title, progress))
    }

    private fun progressNotification(context: Context, title: String, progress: Int) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("正在下载：$title")
        .setContentText("$progress%")
        .setProgress(100, progress, false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "后台任务", NotificationManager.IMPORTANCE_DEFAULT))
    }
}
