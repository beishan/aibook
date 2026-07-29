package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.repository.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookParsingServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReparseTextChapters() throws Exception {
        Path file = tempDir.resolve("测试书籍.txt");
        Files.writeString(
                file,
                "第一章 开始\n正文\n第二章 继续\n正文",
                StandardCharsets.UTF_8);
        Book book = Book.builder()
                .title("测试书籍")
                .format("txt")
                .filePath(file.toString())
                .build();

        BookParsingService.ParseResult result = service().reparse(book);

        assertEquals(2, result.getChapterCount());
        assertEquals(2, result.getBook().getChapterCount());
    }

    @Test
    void shouldReparseEpubMetadataAndSpineCount() throws Exception {
        Path file = tempDir.resolve("sample.epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            addEntry(zip, "META-INF/container.xml", """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf"/>
                      </rootfiles>
                    </container>
                    """);
            addEntry(zip, "OEBPS/content.opf", """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>重新解析的书名</dc:title>
                        <dc:creator>测试作者</dc:creator>
                        <dc:language>zh-CN</dc:language>
                        <meta name="cover" content="cover-image"/>
                      </metadata>
                      <manifest>
                        <item id="cover-image" href="images/cover.png" media-type="image/png"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter1"/>
                        <itemref idref="chapter2"/>
                        <itemref idref="chapter3"/>
                      </spine>
                    </package>
                    """);
            zip.putNextEntry(new ZipEntry("OEBPS/images/cover.png"));
            zip.write(new byte[] {
                    (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0
            });
            zip.closeEntry();
        }
        Book book = Book.builder()
                .title("旧书名")
                .format("epub")
                .filePath(file.toString())
                .build();

        BookParsingService.ParseResult result = service().reparse(book);

        assertEquals("重新解析的书名", result.getBook().getTitle());
        assertEquals("测试作者", result.getBook().getAuthor());
        assertEquals(3, result.getChapterCount());
        assertTrue(result.getBook().getCoverUrl().startsWith("covers/"));
        assertTrue(Files.exists(tempDir.resolve(result.getBook().getCoverUrl())));
    }

    private BookParsingService service() {
        BookRepository repository = mock(BookRepository.class);
        when(repository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BookCoverService coverService = new BookCoverService(repository);
        ReflectionTestUtils.setField(coverService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(coverService, "coverDir", "covers");
        return new BookParsingService(
                repository,
                new TxtParserService(),
                new ObjectMapper(),
                coverService);
    }

    private void addEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
