package com.aibook.android.feature.store

internal data class CompactShelfLabel(
    val text: String,
    val contentDescription: String
)

internal object CompactStoreRowLabels {

    fun format(format: String): String =
        format.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()

    fun source(kind: StoreItemKind): String = when (kind) {
        StoreItemKind.LOCAL -> "本"
        StoreItemKind.OPDS -> "O"
    }

    fun localShelf(shelved: Boolean): CompactShelfLabel = if (shelved) {
        CompactShelfLabel(text = "✓", contentDescription = "已在书架")
    } else {
        CompactShelfLabel(text = "+", contentDescription = "加入书架")
    }
}
