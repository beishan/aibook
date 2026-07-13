package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderContentsCatalogTest {
    private fun chapter(index: Int, title: String) =
        ReaderChapter(index, title, "chapter-$index", "content")

    @Test
    fun `groups common volume headings and keeps heading chapters`() {
        val groups = ReaderContentsCatalog.group(
            listOf(
                chapter(0, "序章"),
                chapter(1, "第一卷 星火初燃"),
                chapter(2, "第一章 少年出山"),
                chapter(3, "卷二 暗潮涌动"),
                chapter(4, "第二章 夜探玄铁坊")
            )
        )

        assertEquals(listOf("正文", "第一卷 星火初燃", "卷二 暗潮涌动"), groups.map { it.title })
        assertEquals(listOf(0), groups[0].chapters.map { it.index })
        assertEquals(listOf(1, 2), groups[1].chapters.map { it.index })
        assertEquals(listOf(3, 4), groups[2].chapters.map { it.index })
    }

    @Test
    fun `uses body group when no volume heading exists`() {
        val groups = ReaderContentsCatalog.group(listOf(chapter(0, "第一章"), chapter(1, "第二章")))

        assertEquals(listOf("正文"), groups.map { it.title })
        assertEquals(listOf(0, 1), groups.single().chapters.map { it.index })
    }

    @Test
    fun `finds current group and derives chapter read states`() {
        val groups = ReaderContentsCatalog.group(
            listOf(chapter(0, "上卷"), chapter(1, "第一章"), chapter(2, "下卷"), chapter(3, "第二章"))
        )

        assertEquals(1, ReaderContentsCatalog.currentGroupIndex(groups, 3))
        assertEquals(ReaderChapterReadState.READ, ReaderContentsCatalog.readState(1, 2))
        assertEquals(ReaderChapterReadState.CURRENT, ReaderContentsCatalog.readState(2, 2))
        assertEquals(ReaderChapterReadState.UNREAD, ReaderContentsCatalog.readState(3, 2))
    }

    @Test
    fun `expanded group exposes every chapter as an independent lazy list item`() {
        val groups = ReaderContentsCatalog.group(
            List(5_000) { index -> chapter(index, "第${index + 1}章") }
        )

        val collapsed = ReaderContentsCatalog.visibleItems(groups, emptySet())
        val expanded = ReaderContentsCatalog.visibleItems(groups, setOf(0))

        assertEquals(1, collapsed.size)
        assertIs<ReaderContentsListItem.GroupHeader>(collapsed.single())
        assertEquals(5_001, expanded.size)
        assertIs<ReaderContentsListItem.GroupHeader>(expanded.first())
        assertIs<ReaderContentsListItem.Chapter>(expanded[1])
        assertIs<ReaderContentsListItem.Chapter>(expanded.last())
    }

    @Test
    fun `finds current chapter position in classic list by real chapter index`() {
        val filtered = listOf(chapter(7, "第八章"), chapter(12, "第十三章"))

        assertEquals(1, ReaderContentsCatalog.chapterListPosition(filtered, 12))
        assertEquals(0, ReaderContentsCatalog.chapterListPosition(filtered, 99))
        assertEquals(0, ReaderContentsCatalog.chapterListPosition(emptyList(), 12))
    }

    @Test
    fun `finds current chapter position in expanded grouped items`() {
        val groups = ReaderContentsCatalog.group(
            listOf(chapter(7, "上卷"), chapter(8, "第一章"), chapter(12, "下卷"), chapter(13, "第二章"))
        )
        val visibleItems = ReaderContentsCatalog.visibleItems(groups, setOf(1))

        assertEquals(2, ReaderContentsCatalog.visibleItemPosition(visibleItems, 12))
        assertEquals(0, ReaderContentsCatalog.visibleItemPosition(visibleItems, 99))
    }
}
