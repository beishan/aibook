package com.aibook.android.core.network.opds

object OpdsDownloadNamer {
    fun fileName(entry: OpdsEntry): String {
        val hrefName = entry.acquisitionLink?.href
            ?.substringBefore('?')
            ?.substringAfterLast('/')
            ?.takeIf { "." in it && it.substringAfterLast('.').length in 2..5 }

        if (!hrefName.isNullOrBlank()) {
            return hrefName
        }

        return "${entry.title.sanitizeFileName()}.${extensionFor(entry.acquisitionLink?.type)}"
    }

    private fun extensionFor(type: String?): String {
        return when (type?.lowercase()) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "text/markdown" -> "md"
            "text/html", "application/xhtml+xml" -> "html"
            else -> "bin"
        }
    }

    private fun String.sanitizeFileName(): String {
        return trim()
            .replace(Regex("[\\\\/:：*?\"<>|\\s]+"), "_")
            .trim('_')
            .ifBlank { "downloaded-book" }
    }
}
