package com.aibook.android.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.aibook.android.core.data.db.ScanDirectoryDao
import com.aibook.android.core.data.db.ScanDirectoryEntity
import com.aibook.android.core.model.BookFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class ScanDirectory(
    val id: String,
    val uri: String,
    val name: String,
    val enabled: Boolean,
    val lastScanAt: Long?,
    val discoveredCount: Int,
    val addedCount: Int,
    val duplicateCount: Int,
    val unsupportedCount: Int,
    val failedCount: Int,
    val lastErrorMessage: String?
)

data class ScanImportStats(
    val scanned: Int = 0,
    val added: Int = 0,
    val restored: Int = 0,
    val duplicate: Int = 0,
    val unsupported: Int = 0,
    val failed: Int = 0
) {
    fun plus(other: ScanImportStats): ScanImportStats {
        return ScanImportStats(
            scanned = scanned + other.scanned,
            added = added + other.added,
            restored = restored + other.restored,
            duplicate = duplicate + other.duplicate,
            unsupported = unsupported + other.unsupported,
            failed = failed + other.failed
        )
    }
}

class ScanDirectoryRepository(
    private val context: Context,
    private val dao: ScanDirectoryDao,
    private val bookRepository: BookRepository
) {
    fun observeDirectories(): Flow<List<ScanDirectory>> {
        return dao.observeAll().map { directories -> directories.map { it.toDomain() } }
    }

    suspend fun addDirectory(uri: Uri): ScanDirectory {
        val uriString = uri.toString()
        val id = UUID.nameUUIDFromBytes(uriString.toByteArray()).toString()
        val existing = dao.getById(id)
        val directory = ScanDirectoryEntity(
            id = id,
            uri = uriString,
            name = resolveDirectoryName(uri),
            enabled = existing?.enabled ?: true,
            lastScanAt = existing?.lastScanAt,
            discoveredCount = existing?.discoveredCount ?: 0,
            addedCount = existing?.addedCount ?: 0,
            duplicateCount = existing?.duplicateCount ?: 0,
            unsupportedCount = existing?.unsupportedCount ?: 0,
            failedCount = existing?.failedCount ?: 0,
            lastErrorMessage = existing?.lastErrorMessage,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        dao.insert(directory)
        return directory.toDomain()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    suspend fun deleteDirectory(id: String) {
        dao.deleteById(id)
    }

    suspend fun scanAllEnabled(): ScanImportStats {
        return dao.getAll()
            .filter { it.enabled }
            .fold(ScanImportStats()) { stats, directory -> stats.plus(scan(directory)) }
    }

    suspend fun scanDirectory(id: String): ScanImportStats {
        val directory = dao.getById(id) ?: return ScanImportStats()
        return scan(directory)
    }

    private suspend fun scan(directory: ScanDirectoryEntity): ScanImportStats {
        return try {
            val stats = scanTree(Uri.parse(directory.uri))
            dao.insert(
                directory.copy(
                    lastScanAt = System.currentTimeMillis(),
                    discoveredCount = stats.scanned,
                    addedCount = stats.added + stats.restored,
                    duplicateCount = stats.duplicate,
                    unsupportedCount = stats.unsupported,
                    failedCount = stats.failed,
                    lastErrorMessage = null
                )
            )
            stats
        } catch (e: Exception) {
            dao.insert(
                directory.copy(
                    lastScanAt = System.currentTimeMillis(),
                    lastErrorMessage = e.message ?: "扫描失败"
                )
            )
            ScanImportStats(failed = 1)
        }
    }

    private suspend fun scanTree(treeUri: Uri): ScanImportStats {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        return scanDocumentChildren(treeUri, rootDocumentId)
    }

    private suspend fun scanDocumentChildren(treeUri: Uri, documentId: String): ScanImportStats {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        var total = ScanImportStats()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex).orEmpty()
                val mimeType = cursor.getString(mimeIndex).orEmpty()
                total = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    total.plus(scanDocumentChildren(treeUri, childId))
                } else {
                    total.plus(importDocument(treeUri, childId, name))
                }
            }
        }
        return total
    }

    private suspend fun importDocument(treeUri: Uri, documentId: String, name: String): ScanImportStats {
        if (BookFormat.fromFileName(name) == null) {
            return ScanImportStats(scanned = 1, unsupported = 1)
        }
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return runCatching { bookRepository.importBook(documentUri, name) }
            .fold(
                onSuccess = { result ->
                    when (result) {
                        is ImportResult.Added -> ScanImportStats(scanned = 1, added = 1)
                        is ImportResult.Restored -> ScanImportStats(scanned = 1, restored = 1)
                        is ImportResult.Duplicate -> ScanImportStats(scanned = 1, duplicate = 1)
                        is ImportResult.UnsupportedFormat -> ScanImportStats(scanned = 1, unsupported = 1)
                        is ImportResult.Failed -> ScanImportStats(scanned = 1, failed = 1)
                    }
                },
                onFailure = { ScanImportStats(scanned = 1, failed = 1) }
            )
    }

    private fun resolveDirectoryName(uri: Uri): String {
        val documentUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        }.getOrNull()
        if (documentUri != null) {
            context.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "扫描目录"
    }

    private fun ScanDirectoryEntity.toDomain(): ScanDirectory {
        return ScanDirectory(
            id = id,
            uri = uri,
            name = name,
            enabled = enabled,
            lastScanAt = lastScanAt,
            discoveredCount = discoveredCount,
            addedCount = addedCount,
            duplicateCount = duplicateCount,
            unsupportedCount = unsupportedCount,
            failedCount = failedCount,
            lastErrorMessage = lastErrorMessage
        )
    }
}
