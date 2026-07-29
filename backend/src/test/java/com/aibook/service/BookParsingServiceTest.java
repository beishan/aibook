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

        BookParsingService parsingService = service();
        BookParsingService.ParseResult result = parsingService.reparse(book);

        assertEquals(2, result.getChapterCount());
        assertEquals(2, result.getBook().getChapterCount());
        assertEquals(2, parsingService.getTableOfContents(book).size());
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
                        <item id="navigation" href="nav.xhtml"
                              media-type="application/xhtml+xml" properties="nav"/>
                        <item id="chapter1" href="text/chapter1.xhtml"
                              media-type="application/xhtml+xml"/>
                        <item id="chapter2" href="text/chapter2.xhtml"
                              media-type="application/xhtml+xml"/>
                        <item id="chapter3" href="text/chapter3.xhtml"
                              media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter1"/>
                        <itemref idref="chapter2"/>
                        <itemref idref="chapter3"/>
                      </spine>
                    </package>
                    """);
            addEntry(zip, "OEBPS/nav.xhtml", """
                    <?xml version="1.0"?>
                    <html xmlns="http://www.w3.org/1999/xhtml"
                          xmlns:epub="http://www.idpf.org/2007/ops">
                      <body>
                        <nav epub:type="toc">
                          <ol>
                            <li><a href="text/chapter1.xhtml">第一章</a></li>
                            <li>
                              <a href="text/chapter2.xhtml">第二章</a>
                              <ol>
                                <li><a href="text/chapter3.xhtml">第二章附录</a></li>
                              </ol>
                            </li>
                          </ol>
                        </nav>
                      </body>
                    </html>
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

        BookParsingService parsingService = service();
        BookParsingService.ParseResult result = parsingService.reparse(book);

        assertEquals("重新解析的书名", result.getBook().getTitle());
        assertEquals("测试作者", result.getBook().getAuthor());
        assertEquals(3, result.getChapterCount());
        assertTrue(result.getBook().getCoverUrl().startsWith("covers/"));
        assertTrue(Files.exists(tempDir.resolve(result.getBook().getCoverUrl())));
        var toc = parsingService.getTableOfContents(book);
        assertEquals(3, toc.size());
        assertEquals("第一章", toc.get(0).getTitle());
        assertEquals("text/chapter3.xhtml", toc.get(2).getHref());
        assertEquals(1, toc.get(2).getDepth());
    }

    @Test
    void shouldReadNcxWithStandardDoctypeWithoutLoadingExternalDtd() throws Exception {
        Path file = tempDir.resolve("ncx-doctype.epub");
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
                      <metadata/>
                      <manifest>
                        <item id="toc" href="toc.ncx"
                              media-type="application/x-dtbncx+xml"/>
                        <item id="chapter1" href="chapter1.xhtml"
                              media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine toc="toc">
                        <itemref idref="chapter1"/>
                      </spine>
                    </package>
                    """);
            addEntry(zip, "OEBPS/toc.ncx", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN"
                      "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                      <navMap>
                        <navPoint id="chapter-1">
                          <navLabel><text>第一章 开始</text></navLabel>
                          <content src="chapter1.xhtml"/>
                        </navPoint>
                      </navMap>
                    </ncx>
                    """);
        }
        Book book = Book.builder()
                .title("带 NCX 目录的书籍")
                .format("epub")
                .filePath(file.toString())
                .build();

        var toc = service().getTableOfContents(book);

        assertEquals(1, toc.size());
        assertEquals("第一章 开始", toc.get(0).getTitle());
        assertEquals("chapter1.xhtml", toc.get(0).getHref());
    }

    @Test
    void shouldNotResolveExternalEntityDeclaredByEpubXml() throws Exception {
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "不应读取的外部内容", StandardCharsets.UTF_8);
        Path file = tempDir.resolve("external-entity.epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            addEntry(zip, "META-INF/container.xml", """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="content.opf"/>
                      </rootfiles>
                    </container>
                    """);
            addEntry(zip, "content.opf", """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata/>
                      <manifest>
                        <item id="toc" href="toc.ncx"
                              media-type="application/x-dtbncx+xml"/>
                        <item id="chapter1" href="chapter1.xhtml"
                              media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine toc="toc"><itemref idref="chapter1"/></spine>
                    </package>
                    """);
            addEntry(zip, "toc.ncx", """
                    <?xml version="1.0"?>
                    <!DOCTYPE ncx [
                      <!ENTITY external SYSTEM "%s">
                    ]>
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                      <navMap>
                        <navPoint id="chapter-1">
                          <navLabel><text>&external;</text></navLabel>
                          <content src="chapter1.xhtml"/>
                        </navPoint>
                      </navMap>
                    </ncx>
                    """.formatted(secret.toUri()));
        }
        Book book = Book.builder()
                .title("外部实体测试")
                .format("epub")
                .filePath(file.toString())
                .build();

        var toc = service().getTableOfContents(book);

        assertEquals(1, toc.size());
        assertEquals("第 1 章", toc.get(0).getTitle());
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
