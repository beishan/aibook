package com.aibook.android.feature.server

import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.core.network.api.dto.BookListDTO
import com.aibook.android.core.network.api.dto.ReadingProgressDTO

/** 云端接口联调完成前供所有云端页面共用的演示数据。 */
object CloudMockData {
    const val enabled = true

    val books = listOf(
        mockBook(9001, "三体", "刘慈欣", "科幻", listOf("科幻", "经典"), 5, true, "EPUB", "地球文明向宇宙发出第一声啼鸣，也为未知世界拉开序幕。"),
        mockBook(9002, "活着", "余华", "文学", listOf("文学", "人生"), 5, true, "EPUB", "一个人和他的命运之间的友情，是最为感人的友情。"),
        mockBook(9003, "百年孤独", "加西亚·马尔克斯", "文学", listOf("魔幻现实主义"), 5, true, "PDF", "布恩迪亚家族七代人的传奇故事。"),
        mockBook(9004, "小王子", "安托万·德·圣埃克苏佩里", "童话", listOf("童话", "治愈"), 5, false, "EPUB", "献给每一位曾经是孩子的大人。"),
        mockBook(9005, "围城", "钱钟书", "文学", listOf("文学", "讽刺"), 4, false, "MOBI", "人生的愿望大都如此，身在其中的人想出去。"),
        mockBook(9006, "人类简史", "尤瓦尔·赫拉利", "历史", listOf("历史", "社会"), 4, false, "EPUB", "从认知革命到科技革命的人类发展脉络。"),
        mockBook(9007, "球状闪电", "刘慈欣", "科幻", listOf("科幻", "物理"), 5, false, "EPUB", "一次离奇的自然现象改变了主人公的一生。"),
        mockBook(9008, "月亮与六便士", "威廉·萨默塞特·毛姆", "文学", listOf("文学", "艺术"), 4, false, "PDF", "在安稳生活与内心召唤之间寻找真正的自己。")
    )

    val shelfBookIds = setOf(9001L, 9002L, 9004L, 9007L)

    val bookLists = listOf(
        BookListDTO(9101, "科幻必读", "从宇宙尺度重新理解人类", books.filter { it.id in setOf(9001L, 9007L) }),
        BookListDTO(9102, "文学经典", "值得反复阅读的文学作品", books.filter { it.id in setOf(9002L, 9003L, 9005L, 9008L) }),
        BookListDTO(9103, "认识世界", "历史、社会与人类文明", books.filter { it.id in setOf(9004L, 9006L) })
    )

    fun favorites(): List<BookDTO> = books.filter { it.isFavorite == true }

    fun book(id: Long): BookDTO? = books.firstOrNull { it.id == id }

    fun bookList(id: Long): BookListDTO? = bookLists.firstOrNull { it.id == id }

    fun search(query: String): List<BookDTO> {
        val keyword = query.trim()
        if (keyword.isBlank()) return emptyList()
        return books.filter { book ->
            book.title.contains(keyword, ignoreCase = true) ||
                book.author.orEmpty().contains(keyword, ignoreCase = true) ||
                book.categoryName.orEmpty().contains(keyword, ignoreCase = true) ||
                book.tagNames.orEmpty().any { it.contains(keyword, ignoreCase = true) }
        }
    }

    fun progress(bookId: Long) = ReadingProgressDTO(
        id = bookId,
        bookId = bookId,
        currentChapter = "chapter-18",
        currentChapterTitle = "第 18 章",
        chapterProgress = 42,
        totalProgress = if (bookId == 9001L) 37 else 24,
        readingTimeSeconds = 7_680,
        lastReadAt = "今天 19:30"
    )

    private fun mockBook(
        id: Long,
        title: String,
        author: String,
        category: String,
        tags: List<String>,
        rating: Int,
        favorite: Boolean,
        format: String,
        description: String
    ) = BookDTO(
        id = id,
        title = title,
        author = author,
        publisher = "汗牛充栋云端书库",
        publishDate = "2026",
        description = description,
        format = format,
        fileSize = 3_670_016,
        language = "中文",
        rating = rating,
        readingStatus = "READING",
        categoryName = category,
        tagNames = tags,
        isFavorite = favorite,
        chapterInfo = "共 32 章",
        createdAt = "2026-09-01T10:00:00"
    )
}
