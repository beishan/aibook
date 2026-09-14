package com.aibook.service;

import com.aibook.dto.BookTocItemDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.LibraryChapter;
import com.aibook.repository.LibraryChapterRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Provides file-free, immutable library publications backed by chapter rows. */
@Service
@RequiredArgsConstructor
public class StructuredPublicationService {

    private final LibraryChapterRepository chapterRepository;
    private final ObjectMapper objectMapper;
    private final TxtParserService txtParserService;

    public boolean supports(BookVersion version) {
        return version != null && "structured".equalsIgnoreCase(version.getFormat());
    }

    @Transactional(readOnly = true)
    public Map<String, String> processedContent(BookVersion version) {
        requireStructured(version);
        List<LibraryChapter> chapters = chapters(version);
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> chapterInfo = new ArrayList<>(chapters.size());
        for (LibraryChapter chapter : chapters) {
            if (!text.isEmpty()) text.append("\n\n");
            int start = text.length();
            text.append(chapter.getTitle()).append("\n\n")
                    .append(txtParserService.processText(chapter.getContent()));
            chapterInfo.add(Map.of(
                    "key", chapter.getChapterKey(),
                    "title", chapter.getTitle(),
                    "startIndex", start,
                    "endIndex", text.length()));
        }
        try {
            return Map.of("text", text.toString(),
                    "chapterInfo", objectMapper.writeValueAsString(chapterInfo));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "结构化目录生成失败", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<BookTocItemDTO> tableOfContents(BookVersion version) {
        requireStructured(version);
        return chapters(version).stream().map(chapter -> BookTocItemDTO.builder()
                .index(chapter.getChapterIndex())
                .title(chapter.getTitle())
                .href("chapter:" + chapter.getId())
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
                            + "/structured/chapters/" + chapter.getId()
                            + "?versionId=" + version.getId());
                    link.put("type", "text/plain; charset=UTF-8");
                    link.put("title", chapter.getTitle());
                    link.put("properties", Map.of("chapterKey", chapter.getChapterKey(),
                            "position", chapter.getChapterIndex()));
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
        LibraryChapter chapter = chapterRepository.findByIdAndBookVersion(chapterId, version)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "章节不存在"));
        return Map.of(
                "id", chapter.getId(),
                "key", chapter.getChapterKey(),
                "index", chapter.getChapterIndex(),
                "title", chapter.getTitle(),
                "content", chapter.getContent(),
                "contentHash", chapter.getContentHash());
    }

    private List<LibraryChapter> chapters(BookVersion version) {
        return chapterRepository.findByBookVersionOrderByChapterIndexAsc(version);
    }

    private void requireStructured(BookVersion version) {
        if (!supports(version)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "当前版本不是结构化章节版本");
        }
    }
}
