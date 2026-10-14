package com.aibook.service;

import com.aibook.dto.BookDTO;
import com.aibook.dto.BookTocItemDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.service.conversion.EpubTextExtractor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookComparisonServiceTest {
    @TempDir Path directory;
    private final BookService bookService = mock(BookService.class);
    private final StructuredPublicationService structured = mock(StructuredPublicationService.class);
    private final BookComparisonService service = new BookComparisonService(
            bookService, new TxtParserService(), new EpubTextExtractor(), structured);

    @Test
    void readsTextChaptersAndPreservesPrefaceWithoutReturningTheNextChapter() throws Exception {
        Path file = directory.resolve("book.txt");
        Files.writeString(file, "作者说明\n第一章 出发\n正文甲\n第二章 到达\n正文乙\n");
        var version = version("txt", file);
        Book book = Book.builder().id(1L).title("测试书籍").build();
        when(bookService.convertToDTO(book)).thenReturn(BookDTO.builder().id(1L).title("测试书籍").build());

        var summary = service.describe(book, version);
        assertTrue(summary.contentSupported());
        assertEquals(Files.size(file), summary.fileSize());
        assertEquals(3, summary.chapters().size());
        assertEquals("前言", summary.chapters().getFirst().getTitle());
        assertEquals("作者说明\n", service.chapter(version, 0).content());
        assertEquals("正文甲\n", service.chapter(version, 1).content());
        assertEquals("正文乙\n", service.chapter(version, 2).content());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.chapter(version, 3)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.chapter(version, -1)).getStatusCode());
    }

    @Test
    void structuredChaptersUseTheirVersionAndRealChapterIdEvenWithSparseIndices() {
        var version = version("structured", null);
        when(structured.supports(version)).thenReturn(true);
        when(structured.tableOfContents(version)).thenReturn(List.of(
                BookTocItemDTO.builder().index(7).href("chapter:88").title("第八章").build()));
        when(structured.chapter(version, 88L)).thenReturn(Map.of("content", "正文内容"));

        var chapter = service.chapter(version, 0);

        assertEquals("第八章", chapter.title());
        assertEquals("正文内容", chapter.content());
        verify(structured).chapter(version, 88L);
    }

    @Test
    void oversizedPreviewIsExplicitlyMarkedAsTruncated() {
        var version = version("structured", null);
        when(structured.supports(version)).thenReturn(true);
        when(structured.tableOfContents(version)).thenReturn(List.of(
                BookTocItemDTO.builder().index(0).href("chapter:88").title("长章节").build()));
        when(structured.chapter(version, 88L)).thenReturn(Map.of("content", "字".repeat(500_001)));

        var chapter = service.chapter(version, 0);

        assertTrue(chapter.truncated());
        assertEquals(500_000, chapter.content().length());
        assertEquals(500_001, chapter.totalCharacters());
    }

    @Test
    void epubReadsOneSpineChapterAndDoesNotExposeExecutableHtml() throws Exception {
        Path file = directory.resolve("book.epub");
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            entry(zip, "META-INF/container.xml", """
                    <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
                    """);
            entry(zip, "OEBPS/content.opf", """
                    <package><manifest>
                      <item id="a" href="one.xhtml" media-type="application/xhtml+xml"/>
                      <item id="b" href="two.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>
                    """);
            entry(zip, "OEBPS/one.xhtml", "<html><body><h1>第一章</h1><script>alert('bad')</script><p>正文甲</p></body></html>");
            entry(zip, "OEBPS/two.xhtml", "<html><body><h1>第二章</h1><p>正文乙</p></body></html>");
        }
        var extractor = new EpubTextExtractor();
        assertEquals(List.of("第一章", "第二章"), extractor.comparisonChapterTitles(file));
        assertEquals("正文甲", service.chapter(version("epub", file), 0).content());
        assertEquals("正文乙", service.chapter(version("epub", file), 1).content());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.chapter(version("epub", file), 2)).getStatusCode());
    }

    @Test
    void missingAndUnsupportedFilesStillReturnDetailsWithAnExplicitMessage() throws Exception {
        Book book = Book.builder().id(1L).title("文件测试").build();
        when(bookService.convertToDTO(book)).thenReturn(BookDTO.builder().id(1L).title("文件测试").build());
        var missing = service.describe(book, version("txt", directory.resolve("missing.txt")));
        assertFalse(missing.contentSupported());
        assertEquals("书籍文件不存在", missing.message());
        assertEquals("文件测试", missing.book().getTitle());
        Path pdf = directory.resolve("book.pdf");
        Files.writeString(pdf, "%PDF-1.4");
        var unsupported = service.describe(book, version("pdf", pdf));
        assertFalse(unsupported.contentSupported());
        assertTrue(unsupported.message().contains("暂不支持"));
        assertEquals(Files.size(pdf), unsupported.fileSize());
    }

    private BookVersion version(String format, Path file) {
        return BookVersion.builder().id(11L).format(format)
                .filePath(file == null ? null : file.toString()).build();
    }

    private void entry(ZipOutputStream zip, String path, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
