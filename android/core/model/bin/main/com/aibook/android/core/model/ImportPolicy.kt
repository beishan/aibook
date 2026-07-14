package com.aibook.android.core.model

import java.nio.charset.Charset

object ImportPolicy {
    private val gbk: Charset = Charset.forName("GBK")
    private val mojibakeMarkers = Regex("[锟涓锛銆鐨妫鍜闃绔笂]")

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

    fun preferredTitle(metadataTitle: String?, fileName: String): String {
        val fallbackTitle = normalizedTitle(fileName)
        val title = metadataTitle?.trim()?.takeIf { it.isNotBlank() } ?: return fallbackTitle
        return if (title.isLikelyUtf8DecodedAsGbk()) fallbackTitle else title
    }

    private fun String.isLikelyUtf8DecodedAsGbk(): Boolean {
        if (!mojibakeMarkers.containsMatchIn(this)) return false
        val recovered = String(toByteArray(gbk), Charsets.UTF_8)
        return recovered != this && recovered.any { it in '\u4E00'..'\u9FFF' }
    }
}
