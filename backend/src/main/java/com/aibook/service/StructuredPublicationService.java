package com.aibook.service;

import com.aibook.dto.BookTocItemDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.LibraryChapter;
import com.aibook.model.entity.RewriteChapter;
import com.aibook.model.entity.RewriteProject;
import com.aibook.repository.LibraryChapterRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Provides file-free, immutable library publications backed by chapter rows. */
@Service
public class StructuredPublicationService {

    private final LibraryChapterRepository chapterRepository;
    private final ObjectMapper objectMapper;
    private final RewriteContentCodec contentCodec;
    @Autowired(required = false)
    private RewriteService rewriteService;

    @Autowired
    public StructuredPublicationService(LibraryChapterRepository chapterRepository,
                                       ObjectMapper objectMapper,
                                       RewriteContentCodec contentCodec) {
        this.chapterRepository = chapterRepository;
        this.objectMapper = objectMapper;
        this.contentCodec = contentCodec;
    }

    public StructuredPublicationService(LibraryChapterRepository chapterRepository,
                                       ObjectMapper objectMapper) {
        this(chapterRepository, objectMapper, new RewriteContentCodec(objectMapper));
    }

    private record ChapterEntry(Long id, int index, String key, String title,
                                String content, String contentHash,
                                int contentFormatVersion) { }

    public boolean supports(BookVersion version) {
        return version != null && "structured".equalsIgnoreCase(version.getFormat());
    }

    @Transactional(readOnly = true)
    public Map<String, String> processedContent(BookVersion version) {
        requireStructured(version);
        List<ChapterEntry> chapters = chapters(version);
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> chapterInfo = new ArrayList<>(chapters.size());
        List<Map<String, String>> readerBlocks = new ArrayList<>();
        for (ChapterEntry chapter : chapters) {
            if (!text.isEmpty()) text.append("\n\n");
            int start = text.length();
            text.append(chapter.title()).append("\n\n");
            readerBlocks.add(Map.of("text", chapter.title(),
                    "html", "<h1>" + escapeHtml(chapter.title()) + "</h1>"));
            if (chapter.contentFormatVersion() == 1) {
                List<Map<String, String>> blocks = contentCodec.renderedBlocks(chapter.content());
                boolean hasBodyBlock = false;
                for (Map<String, String> block : blocks) {
                    if (hasBodyBlock) text.append("\n\n");
                    text.append(block.get("text"));
                    readerBlocks.add(block);
                    hasBodyBlock = true;
                }
            } else {
                String body = formatChapterContent(chapter.content());
                text.append(body);
                if (!body.isBlank()) {
                    Arrays.stream(body.split("\\n\\s*\\n+"))
                            .map(String::trim).filter(value -> !value.isEmpty())
                            .forEach(value -> readerBlocks.add(Map.of("text", value,
                                    "html", "<p>" + escapeHtml(value).replace("\n", "<br>")
                                            + "</p>")));
                }
            }
            chapterInfo.add(Map.of(
                    "key", chapter.key(),
                    "title", chapter.title(),
                    "startIndex", start,
                    "endIndex", text.length()));
        }
        try {
            return Map.of("text", text.toString(),
                    "chapterInfo", objectMapper.writeValueAsString(chapterInfo),
                    "chapterBlocks", objectMapper.writeValueAsString(readerBlocks));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "结构化目录生成失败", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<BookTocItemDTO> tableOfContents(BookVersion version) {
        requireStructured(version);
        return chapters(version).stream().map(chapter -> BookTocItemDTO.builder()
                .index(chapter.index())
                .title(chapter.title())
                .href("chapter:" + chapter.id())
                .depth(0)
                .build()).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> manifest(Book book, BookVersion version) {
        requireStructured(version);
        List<Map<String, Object>> readingOrder = chapters(version).stream()
                .map(chapter -> {
                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("href", "/api/books/" + book.getId()
                            + "/structured/chapters/" + chapter.id()
                            + "?versionId=" + version.getId());
                    link.put("type", "text/plain; charset=UTF-8");
                    link.put("title", chapter.title());
                    link.put("properties", Map.of("chapterKey", chapter.key(),
                            "position", chapter.index()));
                    return link;
                }).toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("@type", "http://schema.org/Book");
        metadata.put("title", book.getTitle());
        if (book.getAuthor() != null && !book.getAuthor().isBlank()) {
            metadata.put("author", book.getAuthor());
        }
        metadata.put("identifier", "urn:aibook:book:" + book.getId()
                + ":version:" + version.getId());
        metadata.put("numberOfPages", readingOrder.size());
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("@context", "https://readium.org/webpub-manifest/context.jsonld");
        manifest.put("metadata", metadata);
        manifest.put("readingOrder", readingOrder);
        manifest.put("toc", readingOrder);
        return manifest;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> chapter(BookVersion version, Long chapterId) {
        requireStructured(version);
        ChapterEntry chapter = chapters(version).stream()
                .filter(item -> Objects.equals(item.id(), chapterId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "章节不存在"));
        String plainText = chapter.contentFormatVersion() == 1
                ? contentCodec.plainText(chapter.content()) : chapter.content();
        String renderedHtml = chapter.contentFormatVersion() == 1
                ? contentCodec.renderedBlocks(chapter.content()).stream()
                        .map(block -> block.get("html")).collect(Collectors.joining())
                : "<p>" + escapeHtml(formatChapterContent(chapter.content()))
                        .replace("\n", "<br>") + "</p>";
        return Map.of(
                "id", chapter.id(),
                "key", chapter.key(),
                "index", chapter.index(),
                "title", chapter.title(),
                "content", plainText,
                "contentHtml", renderedHtml,
                "contentHash", chapter.contentHash());
    }

    private List<ChapterEntry> chapters(BookVersion version) {
        RewriteProject project = rewriteService == null ? null
                : rewriteService.projectForVersion(version);
        if (project != null && project.getStatus() != RewriteProject.Status.COMPLETED) {
            List<RewriteChapter> draft = rewriteService.readableChapters(project);
            List<ChapterEntry> result = new ArrayList<>(draft.size());
            for (int index = 0; index < draft.size(); index++) {
                RewriteChapter chapter = draft.get(index);
                result.add(new ChapterEntry(chapter.getId(), index,
                        "rewrite:" + project.getId() + ":" + chapter.getId(),
                        chapter.getTitle(), chapter.getContent(),
                        "revision:" + chapter.getRevision(),
                        Objects.requireNonNullElse(chapter.getContentFormatVersion(), 0)));
            }
            return result;
        }
        return chapterRepository.findByBookVersionOrderByChapterIndexAsc(version).stream()
                .map(chapter -> new ChapterEntry(chapter.getId(), chapter.getChapterIndex(),
                        chapter.getChapterKey(), chapter.getTitle(), chapter.getContent(),
                        chapter.getContentHash(), contentCodec.isDocument(chapter.getContent()) ? 1 : 0))
                .toList();
    }

    /**
     * Mirrors the crawler trial reader: blank-line blocks win, otherwise each source line is a
     * paragraph.
     */
    private String formatChapterContent(String content) {
        String normalized = content == null ? "" : content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalized.isEmpty()) return "";
        List<String> blocks = Arrays.stream(normalized.split("\\n\\s*\\n+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (blocks.size() > 1) return String.join("\n\n", blocks);
        return Arrays.stream(normalized.split("\\n"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void requireStructured(BookVersion version) {
        if (!supports(version)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "当前版本不是结构化章节版本");
        }
    }
}
