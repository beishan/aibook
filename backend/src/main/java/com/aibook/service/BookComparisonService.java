package com.aibook.service;

import com.aibook.dto.BookComparisonDTO;
import com.aibook.dto.BookTocItemDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.service.conversion.EpubTextExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BookComparisonService {
    private static final long MAX_TEXT_FILE_BYTES = 64L * 1024 * 1024;
    private static final int MAX_PREVIEW_CHARACTERS = 500_000;
    private final BookService bookService;
    private final TxtParserService txtParserService;
    private final EpubTextExtractor epubTextExtractor;
    private final StructuredPublicationService structuredPublicationService;

    public BookComparisonDTO describe(Book book, BookVersion version) {
        List<BookTocItemDTO> chapters = List.of();
        boolean supported = supported(version.getFormat());
        String message = supported ? "" : "该格式暂不支持章节正文提取，可查看书籍详情或打开阅读器核对";
        Long size = version.getFileSize();
        try {
            if (!structuredPublicationService.supports(version)) {
                Path file = file(version);
                size = Files.size(file);
            }
            if (supported) {
                chapters = catalogue(version);
                if (chapters.isEmpty()) message = "暂无可对比章节";
            }
        } catch (Exception exception) {
            supported = false;
            message = exception instanceof ResponseStatusException status
                    ? status.getReason() : "章节目录读取失败，请检查书籍文件";
        }
        return new BookComparisonDTO(bookService.convertToDTO(book), version.getId(),
                version.getFormat(), size, supported, message, chapters);
    }

    public BookComparisonDTO.Chapter chapter(BookVersion version, int index) {
        if (index < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "章节索引无效");
        }
        try {
            String title;
            String content;
            if (structuredPublicationService.supports(version)) {
                var chapters = structuredPublicationService.tableOfContents(version);
                var entry = at(chapters, index);
                Long id = Long.valueOf(entry.getHref().substring("chapter:".length()));
                var chapter = structuredPublicationService.chapter(version, id);
                title = entry.getTitle();
                content = (String) chapter.get("content");
            } else if ("epub".equalsIgnoreCase(version.getFormat())) {
                var chapter = epubTextExtractor.comparisonChapter(file(version), index);
                title = chapter.title();
                content = chapter.content();
            } else if (supported(version.getFormat())) {
                String text = text(version);
                var entry = at(textCatalogue(text), index);
                title = entry.getTitle();
                content = text.substring(entry.getStartIndex(), entry.getEndIndex());
                int newline = content.indexOf('\n');
                String firstLine = newline < 0 ? content : content.substring(0, newline);
                if (firstLine.trim().equals(title.trim())) {
                    content = newline < 0 ? "" : content.substring(newline + 1).stripLeading();
                }
            } else {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "该格式暂不支持章节正文提取");
            }
            content = content == null ? "" : content;
            int length = content.length();
            return new BookComparisonDTO.Chapter(index, title,
                    content.substring(0, Math.min(length, MAX_PREVIEW_CHARACTERS)),
                    length, length > MAX_PREVIEW_CHARACTERS);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IndexOutOfBoundsException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "章节不存在");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "章节正文读取失败", exception);
        }
    }

    private List<BookTocItemDTO> catalogue(BookVersion version) throws Exception {
        if (structuredPublicationService.supports(version)) {
            return structuredPublicationService.tableOfContents(version);
        }
        if ("epub".equalsIgnoreCase(version.getFormat())) {
            var titles = epubTextExtractor.comparisonChapterTitles(file(version));
            List<BookTocItemDTO> chapters = new ArrayList<>();
            for (int index = 0; index < titles.size(); index++) {
                chapters.add(BookTocItemDTO.builder().index(index).title(titles.get(index)).depth(0).build());
            }
            return chapters;
        }
        return textCatalogue(text(version));
    }

    private List<BookTocItemDTO> textCatalogue(String text) {
        var parsed = txtParserService.detectChapters(text);
        List<BookTocItemDTO> result = new ArrayList<>();
        if (!parsed.isEmpty() && parsed.getFirst().getStartIndex() > 0
                && !text.substring(0, parsed.getFirst().getStartIndex()).isBlank()) {
            result.add(BookTocItemDTO.builder().index(0).title("前言")
                    .startIndex(0).endIndex(parsed.getFirst().getStartIndex()).depth(0).build());
        }
        for (var chapter : parsed) {
            result.add(BookTocItemDTO.builder().index(result.size()).title(chapter.getTitle())
                    .startIndex(chapter.getStartIndex()).endIndex(chapter.getEndIndex()).depth(0).build());
        }
        return result;
    }

    private String text(BookVersion version) throws Exception {
        Path file = file(version);
        if (Files.size(file) > MAX_TEXT_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "文本文件超过 64 MiB，暂不支持正文对比");
        }
        String raw = txtParserService.readFileWithEncoding(file);
        if (List.of("html", "htm").contains(version.getFormat().toLowerCase(Locale.ROOT))) {
            var document = Jsoup.parse(raw);
            document.select("script,style,noscript").remove();
            return document.body().wholeText();
        }
        return raw;
    }

    private Path file(BookVersion version) {
        if (version.getFilePath() == null || !Files.isRegularFile(Path.of(version.getFilePath()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "书籍文件不存在");
        }
        return Path.of(version.getFilePath());
    }

    private boolean supported(String format) {
        return format != null && List.of("structured", "epub", "txt", "md", "html", "htm")
                .contains(format.toLowerCase(Locale.ROOT));
    }

    private BookTocItemDTO at(List<BookTocItemDTO> chapters, int index) {
        if (index >= chapters.size()) {
            throw new IndexOutOfBoundsException();
        }
        return chapters.get(index);
    }
}
