package com.aibook.android.feature.store

object StoreDownloadNamer {
    fun fileName(book: StoreBook): String {
        val hrefName = book.acquisitionHref
            ?.substringBefore('?')
            ?.substringAfterLast('/')
            ?.takeIf { "." in it && it.substringAfterLast('.').length in 2..5 }

        if (!hrefName.isNullOrBlank()) {
            return hrefName
        }

        return "${book.title.sanitizeFileName()}.${extensionFor(book.acquisitionType, book.format)}"
    }

    private fun extensionFor(type: String?, format: String): String {
        return when (type?.lowercase()) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "text/markdown" -> "md"
            "text/html", "application/xhtml+xml" -> "html"
            else -> when (format.lowercase()) {
                "pdf" -> "pdf"
                "txt" -> "txt"
                "markdown" -> "md"
                "html" -> "html"
                else -> "epub"
            }
        }
    }

    private fun String.sanitizeFileName(): String {
        return trim()
            .replace(Regex("[\\\\/:：*?\"<>|\\s]+"), "_")
            .trim('_')
            .ifBlank { "downloaded-book" }
    }
}
