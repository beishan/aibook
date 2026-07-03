package com.aibook.android.feature.reader

import android.content.Context
import com.aibook.android.core.reader.EpubBookContent
import com.aibook.android.core.reader.EpubMetadata
import com.aibook.android.core.reader.ReaderChapter
import org.jsoup.Jsoup
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.logging.ListWarningLogger
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.File

class ReadiumEpubReader(
    context: Context
) {
    private val appContext = context.applicationContext

    suspend fun parse(file: File, initialHref: String? = null): EpubBookContent {
        return withPublication(file) { publication ->
            val chapters = publication.readingOrder.mapIndexed { index, link ->
                link.toReaderChapterStub(index)
            }
            if (chapters.isEmpty()) {
                return@withPublication EpubBookContent()
            }
            val initialIndex = chapters.indexOfFirst { it.href == initialHref }
                .takeIf { it >= 0 }
                ?: 0
            val initialChapter = publication.toReaderChapter(
                index = initialIndex,
                link = publication.readingOrder[initialIndex]
            )

            EpubBookContent(
                metadata = EpubMetadata(
                    title = publication.metadata.title,
                    author = publication.metadata.authors.firstOrNull()?.name,
                    language = publication.metadata.languages.firstOrNull()
                ),
                chapters = chapters.replaceChapter(initialChapter)
            )
        }
    }

    suspend fun parseChapter(file: File, index: Int): ReaderChapter {
        return withPublication(file) { publication ->
            val link = publication.readingOrder.getOrNull(index)
                ?: error("章节不存在")
            publication.toReaderChapter(index, link)
        }
    }

    private suspend fun <T> withPublication(
        file: File,
        block: suspend (Publication) -> T
    ): T {
        val assetRetriever = AssetRetriever(appContext.contentResolver, DefaultHttpClient())
        val asset = assetRetriever.retrieve(file, MediaType.EPUB).getOrNull()
            ?: error("Readium 无法识别该 EPUB 文件")

        try {
            val publication = EpubParser()
                .parse(asset, ListWarningLogger())
                .getOrNull()
                ?.build()
                ?: error("Readium 无法解析 EPUB 出版物")

            try {
                return block(publication)
            } finally {
                publication.close()
            }
        } finally {
            asset.close()
        }
    }

    private fun Link.toReaderChapterStub(index: Int): ReaderChapter {
        return ReaderChapter(
            index = index,
            title = title ?: "第${index + 1}章",
            href = href.toString(),
            content = ""
        )
    }

    private fun List<ReaderChapter>.replaceChapter(chapter: ReaderChapter): List<ReaderChapter> {
        return map { current ->
            if (current.index == chapter.index) chapter else current
        }
    }

    private suspend fun Publication.toReaderChapter(index: Int, link: Link): ReaderChapter {
        val fallback = link.toReaderChapterStub(index)
        val resource = get(link) ?: return fallback
        val length = resource.length().getOrNull() ?: return fallback
        if (length <= 0L) return fallback

        val bytes = resource.read(0L..(length - 1)).getOrNull() ?: return fallback
        val html = bytes.toString(Charsets.UTF_8)
        val document = Jsoup.parse(html)
        val title = link.title
            ?: document.selectFirst("h1,h2,h3,h4,h5,h6,title")?.text()
            ?: "第${index + 1}章"
        val paragraphs = document.select("p,blockquote,li")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && it != title }
        val content = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n")
        } else {
            document.body()?.text()?.trim().orEmpty()
        }

        return ReaderChapter(
            index = index,
            title = title,
            href = link.href.toString(),
            content = content
        )
    }
}
