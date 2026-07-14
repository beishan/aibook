package com.aibook.android.core.data.repository

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies only explicitly referenced, local raster resources into a Markdown book's private directory. */
class MarkdownCompanionStorage(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxResources: Int = DEFAULT_MAX_RESOURCES,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    private val maxResourceBytes: Long = DEFAULT_MAX_RESOURCE_BYTES
) {
    suspend fun copy(
        markdownFile: File,
        references: Collection<String>,
        openSource: (String) -> InputStream?
    ) = withContext(ioDispatcher) copy@{
        val root = requireNotNull(markdownFile.parentFile).canonicalFile
        val safeResources = references.asSequence()
            .distinct()
            .mapNotNull { path -> safeDestination(root, path)?.let { path to it } }
            .take(maxResources.coerceAtLeast(0))
            .toList()
        var copiedBytes = 0L
        safeResources.forEach { (relativePath, destination) ->
            val input = runCatching { openSource(relativePath) }.getOrNull() ?: return@forEach
            input.use {
                destination.parentFile?.mkdirs()
                val temporary = File(destination.parentFile, ".${destination.name}.part")
                try {
                    temporary.outputStream().use { output ->
                        copiedBytes += copyWithLimits(it, output, copiedBytes)
                    }
                    if (!temporary.renameTo(destination)) {
                        temporary.copyTo(destination, overwrite = true)
                        temporary.delete()
                    }
                } catch (_: TotalLimitExceededException) {
                    temporary.delete()
                    return@copy
                } catch (_: Exception) {
                    temporary.delete()
                }
            }
        }
    }

    private fun safeDestination(root: File, relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.contains('\\')) return null
        val path = File(relativePath)
        if (path.isAbsolute || path.extension.lowercase() !in RASTER_EXTENSIONS) return null
        if (relativePath.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        val destination = runCatching { File(root, relativePath).canonicalFile }.getOrNull() ?: return null
        return destination.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    private fun copyWithLimits(input: InputStream, output: java.io.OutputStream, alreadyCopied: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var resourceBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return resourceBytes
            resourceBytes += read
            if (resourceBytes > maxResourceBytes) throw ResourceTooLargeException()
            if (alreadyCopied + resourceBytes > maxTotalBytes) throw TotalLimitExceededException()
            output.write(buffer, 0, read)
        }
    }

    private class ResourceTooLargeException : Exception()
    private class TotalLimitExceededException : Exception()

    private companion object {
        const val DEFAULT_MAX_RESOURCES = 128
        const val DEFAULT_MAX_TOTAL_BYTES = 64L * 1024 * 1024
        const val DEFAULT_MAX_RESOURCE_BYTES = 20L * 1024 * 1024
        val RASTER_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    }
}
