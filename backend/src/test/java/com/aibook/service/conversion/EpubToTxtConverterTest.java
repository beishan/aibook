package com.aibook.service.conversion;

import com.aibook.dto.BookConversionUpdateRequest;
import com.aibook.dto.ConversionChapterDTO;
import com.aibook.model.entity.BookConversionTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpubToTxtConverterTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsMetadataAndTextInSpineOrder() throws Exception {
        Path source = createEpub();

        EpubTextExtractor.ExtractedBook extracted = new EpubTextExtractor().extract(source);

        assertEquals("EPUB 转换测试", extracted.title());
        assertEquals("测试作者", extracted.author());
        assertEquals("zh-CN", extracted.language());
        assertEquals(List.of("第一章 初见", "第二章 重逢"),
                extracted.chapters().stream().map(ConversionChapterDTO::getTitle).toList());
        assertTrue(extracted.text().indexOf("第一章正文") < extracted.text().indexOf("第二章正文"));
        assertTrue(extracted.text().contains("项目一"));
        assertFalse(extracted.text().contains("不应出现的脚本"));
        assertFalse(extracted.text().contains("不应出现的样式"));
    }

    @Test
    void convertsToUtf8AndAppliesChapterEditsAndCleanup() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path source = createEpub();
        EpubTextExtractor extractor = new EpubTextExtractor();
        List<ConversionChapterDTO> chapters = extractor.extract(source).chapters();
        chapters.get(0).setTitle("第1章 相遇");
        chapters.get(1).setIgnored(true);

        BookConversionUpdateRequest settings = new BookConversionUpdateRequest();
        settings.setRemoveExtraBlankLines(true);
        settings.setTrimLineEnd(true);
        settings.setNormalizeWidth(true);
        BookConversionTask task = BookConversionTask.builder()
                .id(21L)
                .sourcePath(source.toString())
                .sourceFormat("epub")
                .targetFormat("txt")
                .title("EPUB 转换测试")
                .chaptersJson(mapper.writeValueAsString(chapters))
                .settingsJson(mapper.writeValueAsString(settings))
                .build();
        Path output = tempDir.resolve("result.txt");

        new EpubToTxtConverter(mapper, extractor).convert(task, output);

        String result = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(result.startsWith("第1章 相遇\n\n第一章正文"));
        assertTrue(result.contains("ABC"));
        assertFalse(result.contains("第一章 初见"));
        assertFalse(result.contains("第二章 重逢"));
        assertFalse(result.contains("第二章正文"));
        assertTrue(result.endsWith("\n"));
    }

    private Path createEpub() throws Exception {
        Path epub = tempDir.resolve("source.epub");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(epub), StandardCharsets.UTF_8)) {
            add(output, "mimetype", "application/epub+zip");
            add(output, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                    """);
            add(output, "OPS/book.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>EPUB 转换测试</dc:title><dc:creator>测试作者</dc:creator><dc:language>zh-CN</dc:language>
                      </metadata>
                      <manifest>
                        <item id="chapter-two" href="text/chapter%202.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter-one" href="text/chapter-1.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine><itemref idref="chapter-one"/><itemref idref="chapter-two"/></spine>
                    </package>
                    """);
            add(output, "OPS/text/chapter-1.xhtml", """
                    <html xmlns="http://www.w3.org/1999/xhtml"><head><title>备用标题</title>
                      <style>不应出现的样式</style><script>不应出现的脚本</script></head>
                      <body><h1>第一章 初见</h1><p>第一章正文   </p><p>ＡＢＣ</p><ul><li>项目一</li></ul></body></html>
                    """);
            add(output, "OPS/text/chapter 2.xhtml", """
                    <html xmlns="http://www.w3.org/1999/xhtml"><body><h2>第二章 重逢</h2><p>第二章正文</p></body></html>
                    """);
        }
        return epub;
    }

    private void add(ZipOutputStream output, String name, String content) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
