package com.aibook.service.conversion;

import com.aibook.dto.BookConversionUpdateRequest;
import com.aibook.dto.ConversionChapterDTO;
import com.aibook.model.entity.BookConversionTask;
import com.aibook.service.repair.EncodingDetectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class TxtToEpubConverterTest {
    @TempDir Path tempDir;

    @Test
    void generatesValidatedEpub3WithMetadataChaptersAndFirstEntryMimetype() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path source = tempDir.resolve("测试.txt");
        String text = "第一章 初见\n这是第一章。\n\n第二章 重逢\n这是第二章。";
        Files.writeString(source, text, StandardCharsets.UTF_8);

        int secondStart = text.indexOf("第二章");
        List<ConversionChapterDTO> chapters = List.of(
                ConversionChapterDTO.builder().index(0).title("第一章 初见")
                        .startIndex(0).endIndex(secondStart).build(),
                ConversionChapterDTO.builder().index(1).title("第二章 重逢")
                        .startIndex(secondStart).endIndex(text.length()).build());
        BookConversionUpdateRequest settings = new BookConversionUpdateRequest();
        settings.setFirstLineIndent("2em");
        settings.setParagraphSpacing("small");
        settings.setLineHeight(1.6);
        settings.setRemoveExtraBlankLines(true);
        settings.setTrimLineEnd(true);

        BookConversionTask task = BookConversionTask.builder()
                .id(7L).sourcePath(source.toString()).sourceFormat("txt").targetFormat("epub")
                .title("测试书").author("测试作者").description("测试简介").language("zh-CN")
                .encoding("UTF-8").chaptersJson(mapper.writeValueAsString(chapters))
                .settingsJson(mapper.writeValueAsString(settings)).build();
        Path output = tempDir.resolve("测试书.epub");

        new TxtToEpubConverter(mapper, new EncodingDetectService()).convert(task, output);

        assertTrue(Files.size(output) > 0);
        try (ZipFile epub = new ZipFile(output.toFile())) {
            assertEquals("mimetype", epub.entries().nextElement().getName());
            assertEquals(0, epub.getEntry("mimetype").getMethod());
            assertNotNull(epub.getEntry("META-INF/container.xml"));
            assertNotNull(epub.getEntry("OEBPS/content.opf"));
            assertNotNull(epub.getEntry("OEBPS/nav.xhtml"));
            assertNotNull(epub.getEntry("OEBPS/chapter-0001.xhtml"));
            String opf = new String(epub.getInputStream(epub.getEntry("OEBPS/content.opf")).readAllBytes(), StandardCharsets.UTF_8);
            String nav = new String(epub.getInputStream(epub.getEntry("OEBPS/nav.xhtml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(opf.contains("<dc:title>测试书</dc:title>"));
            assertTrue(opf.contains("<dc:creator>测试作者</dc:creator>"));
            assertTrue(nav.contains("第一章 初见"));
            assertTrue(nav.contains("第二章 重逢"));
        }
    }

    @Test
    void omitsIgnoredChapters() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path source = Files.writeString(tempDir.resolve("ignore.txt"), "第一章\n正文\n第二章\n广告");
        List<ConversionChapterDTO> chapters = List.of(
                ConversionChapterDTO.builder().index(0).title("第一章").startIndex(0).endIndex(7).build(),
                ConversionChapterDTO.builder().index(1).title("第二章").startIndex(7).endIndex(14).ignored(true).build());
        BookConversionTask task = BookConversionTask.builder().id(8L).sourcePath(source.toString())
                .sourceFormat("txt").targetFormat("epub").title("忽略测试").encoding("UTF-8")
                .chaptersJson(mapper.writeValueAsString(chapters))
                .settingsJson(mapper.writeValueAsString(new BookConversionUpdateRequest())).build();
        Path output = tempDir.resolve("ignore.epub");

        new TxtToEpubConverter(mapper, new EncodingDetectService()).convert(task, output);

        try (ZipFile epub = new ZipFile(output.toFile())) {
            assertNotNull(epub.getEntry("OEBPS/chapter-0001.xhtml"));
            assertNull(epub.getEntry("OEBPS/chapter-0002.xhtml"));
        }
    }
}
