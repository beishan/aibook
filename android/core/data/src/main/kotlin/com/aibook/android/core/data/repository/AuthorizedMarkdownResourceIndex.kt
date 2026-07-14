package com.aibook.android.core.data.repository

data class SelectedDocument<T>(
    val value: T,
    val documentId: String?,
    val displayName: String,
    val providerId: String? = null
)

/** Resolves Markdown resources strictly to documents the user explicitly selected. */
class AuthorizedMarkdownResourceIndex<T>(documents: Collection<SelectedDocument<T>>) {
    private val byDocumentId = documents
        .mapNotNull { document -> document.documentId?.let { (document.providerId to it) to document.value } }
        .groupBy({ it.first }, { it.second })
    fun resolve(source: SelectedDocument<T>, relativePath: String): T? {
        val safePath = safeRelativePath(relativePath) ?: return null
        val sourceId = source.documentId
        if (sourceId != null) {
            val expectedId = expectedSelectedSiblingId(sourceId, safePath)
            val exactMatches = expectedId?.let { byDocumentId[source.providerId to it] }
            if (exactMatches?.size == 1) return exactMatches.single()
        }
        return null
    }

    fun <R> withResolved(source: SelectedDocument<T>, relativePath: String, action: (T) -> R): R? {
        return resolve(source, relativePath)?.let(action)
    }
}

data class TreeDocument<T>(val value: T, val relativePath: String)

/** Uses relative paths discovered by querying an authorized document tree; no document IDs are synthesized. */
class AuthorizedTreeResourceIndex<T>(documents: Collection<TreeDocument<T>>) {
    private val byRelativePath = documents.groupBy(TreeDocument<T>::relativePath)

    fun resolve(sourceRelativePath: String, resourcePath: String): T? {
        val safeResource = safeRelativePath(resourcePath) ?: return null
        val parent = sourceRelativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val target = if (parent.isEmpty()) safeResource else "$parent/$safeResource"
        return byRelativePath[target]?.singleOrNull()?.value
    }

    fun <R> withResolved(sourceRelativePath: String, resourcePath: String, action: (T) -> R): R? {
        return resolve(sourceRelativePath, resourcePath)?.let(action)
    }
}

private fun safeRelativePath(path: String): String? {
    if (path.isBlank() || path.startsWith('/') || path.contains('\\')) return null
    if (path.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
    return path
}

/** Computes a lookup key only; callers can receive values solely from the selected-document whitelist. */
private fun expectedSelectedSiblingId(sourceDocumentId: String, relativePath: String): String? {
    val separator = sourceDocumentId.lastIndexOf('/')
    if (separator >= 0) return sourceDocumentId.substring(0, separator + 1) + relativePath
    val rootSeparator = sourceDocumentId.indexOf(':')
    if (rootSeparator >= 0) return sourceDocumentId.substring(0, rootSeparator + 1) + relativePath
    return null
}
