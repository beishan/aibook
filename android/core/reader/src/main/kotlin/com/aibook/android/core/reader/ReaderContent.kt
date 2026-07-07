package com.aibook.android.core.reader

data class ReaderChapter(
    val index: Int,
    val title: String,
    val href: String,
    val content: String,
    val imageUri: String? = null
)

data class ReaderPage(
    val index: Int,
    val text: String,
    val progress: Float
)
