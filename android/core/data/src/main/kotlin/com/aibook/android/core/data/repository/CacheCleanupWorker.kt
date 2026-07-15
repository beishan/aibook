package com.aibook.android.core.data.repository

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.TimeUnit

class CacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val days = inputData.getInt(KEY_DAYS, 0)
        if (days <= 0) return Result.success()
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        clean(File(applicationContext.cacheDir.path), cutoff)
        clean(File(applicationContext.filesDir, "parsed-books"), cutoff)
        return Result.success()
    }

    private fun clean(root: File, cutoff: Long) {
        root.listFiles()?.forEach { child ->
            if (child.isDirectory) clean(child, cutoff)
            if (child.lastModified() in 1 until cutoff) child.deleteRecursively()
        }
    }

    companion object {
        private const val WORK_NAME = "aibook-cache-cleanup"
        private const val KEY_DAYS = "retention_days"

        fun configure(context: Context, retentionDays: Int) {
            val manager = WorkManager.getInstance(context)
            if (retentionDays <= 0) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<CacheCleanupWorker>(24, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_DAYS to retentionDays))
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
