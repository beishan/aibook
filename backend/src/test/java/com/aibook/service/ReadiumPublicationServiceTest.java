package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadiumPublicationServiceTest {

    @TempDir
    Path tempDirectory;

    private final ReadiumPublicationService service = new ReadiumPublicationService();

    @Test
    void buildsManifestInSpineOrderAndReadsResources() throws IOException {
        Path epub = createEpub();
        Book book = Book.builder().id(7L).title("测试书").author("作者").language("zh").build();
        BookVersion version = BookVersion.builder()
                .id(12L).format("epub").filePath(epub.toString()).build();

        Map<String, Object> manifest = service.buildManifest(book, version, "test-token");
        assertThat((Iterable<?>) manifest.get("readingOrder")).hasSize(1);
        assertThat(manifest).containsKeys("metadata", "links", "resources");

        ReadiumPublicationService.ResourceData chapter =
                service.readResource(version, "/OPS/chapter.xhtml");
        assertThat(chapter.mediaType()).isEqualTo("application/xhtml+xml");
        assertThat(new String(chapter.bytes())).contains("正文");
        assertThat(chapter.etag()).startsWith("\"");
    }

    @Test
    void rejectsArchiveTraversal() throws IOException {
        BookVersion version = BookVersion.builder()
                .id(12L).format("epub").filePath(createEpub().toString()).build();

        assertThatThrownBy(() -> service.readResource(version, "../../secret"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("路径越界");
    }

    private Path createEpub() throws IOException {
        Path epub = tempDirectory.resolve("sample.epub");
        try (ZipOutputStream zip = new ZipOutputStream(java.nio.file.Files.newOutputStream(epub))) {
            write(zip, "META-INF/container.xml", """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles>
                    </container>
                    """);
            write(zip, "OPS/book.opf", """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <manifest>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                        <item id="style" href="style.css" media-type="text/css"/>
                      </manifest>
                      <spine><itemref idref="chapter"/></spine>
                    </package>
                    """);
            write(zip, "OPS/chapter.xhtml", "<html><body>正文</body></html>");
            write(zip, "OPS/style.css", "body { color: black; }");
        }
        return epub;
    }

    private void write(ZipOutputStream zip, String path, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
