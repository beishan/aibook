package com.aibook.android.core.model

object ImportPolicy {
    fun isSupported(fileName: String): Boolean {
        return BookFormat.fromFileName(fileName) != null
    }

    fun normalizedTitle(fileName: String): String {
        val name = fileName.substringAfterLast('/').substringAfterLast('\\')
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")

        return if (extension.isBlank()) {
            name.ifBlank { "未命名书籍" }
        } else {
            name.removeSuffix(".$extension").ifBlank { "未命名书籍" }
        }
    }
}
