package com.aibook.android.core.reader

object TextPaginator {
    fun paginate(text: String, maxCharsPerPage: Int): List<ReaderPage> {
        require(maxCharsPerPage > 0) { "maxCharsPerPage must be positive" }

        if (text.isEmpty()) {
            return listOf(ReaderPage(index = 0, text = "", progress = 0f))
        }

        val chunks = text.chunked(maxCharsPerPage)
        return chunks.mapIndexed { index, pageText ->
            ReaderPage(
                index = index,
                text = pageText,
                progress = (index.toFloat() / chunks.size).coerceIn(0f, 1f)
            )
        }
    }
}
