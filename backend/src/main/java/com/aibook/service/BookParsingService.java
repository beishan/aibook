package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.repository.BookRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 从书籍原始文件重新提取本地元数据。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookParsingService {

    private final BookRepository bookRepository;
    private final TxtParserService txtParserService;
    private final ObjectMapper objectMapper;
    private final BookCoverService bookCoverService;

    @Transactional
    public ParseResult reparse(Book book) {
        Path file = Paths.get(book.getFilePath());
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "书籍原始文件不存在");
        }

        List<String> updatedFields = new ArrayList<>();
        try {
            book.setFileSize(Files.size(file));
            updatedFields.add("fileSize");

            String format = book.getFormat().toLowerCase(Locale.ROOT);
            switch (format) {
                case "txt", "md" -> parseText(book, file, updatedFields);
                case "epub" -> parseEpubMetadata(book, file, updatedFields);
                default -> updateTitleFromFilename(book, file, updatedFields);
            }

            Book saved = bookRepository.save(book);
            String message = switch (format) {
                case "txt", "md" -> "文本信息与章节已重新解析";
                case "epub" -> "EPUB 元数据与章节数已重新解析";
                default -> "文件信息已刷新，当前格式暂不支持章节解析";
            };
            return ParseResult.builder()
                    .book(saved)
                    .updatedFields(updatedFields)
                    .chapterCount(saved.getChapterCount())
                    .message(message)
                    .build();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("重新解析书籍失败: {}", book.getFilePath(), exception);
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "解析失败: " + exception.getMessage(),
                    exception);
        }
    }

    private void parseText(Book book, Path file, List<String> updatedFields) throws Exception {
        String chapterInfo = txtParserService.parseChapters(file);
        List<?> chapters = objectMapper.readValue(
                chapterInfo, new TypeReference<List<Object>>() {});
        book.setChapterInfo(chapterInfo);
        book.setChapterCount(chapters.size());
        updatedFields.add("chapterInfo");
        updatedFields.add("chapterCount");
        updateTitleFromFilename(book, file, updatedFields);
    }

    /**
     * 提取 EPUB 元数据、章节数和内嵌封面，可用于新书扫描和重新解析。
     */
    public void parseEpubMetadata(
            Book book, Path file, List<String> updatedFields) throws Exception {
        try (ZipFile zipFile = new ZipFile(file.toFile())) {
            Document container = parseXml(zipFile, "META-INF/container.xml");
            Element rootFile = (Element) container
                    .getElementsByTagNameNS("*", "rootfile")
                    .item(0);
            if (rootFile == null) {
                throw new IllegalArgumentException("EPUB 缺少 OPF 路径");
            }

            String opfPath = rootFile.getAttribute("full-path");
            Document opf = parseXml(zipFile, opfPath);
            updateIfPresent(book.getTitle(), text(opf, "title"), book::setTitle, "title", updatedFields);
            updateIfPresent(book.getAuthor(), text(opf, "creator"), book::setAuthor, "author", updatedFields);
            updateIfPresent(book.getPublisher(), text(opf, "publisher"), book::setPublisher, "publisher", updatedFields);
            updateIfPresent(book.getLanguage(), text(opf, "language"), book::setLanguage, "language", updatedFields);
            String identifier = text(opf, "identifier");
            if (identifier != null) {
                String normalizedIdentifier = identifier.replaceAll("[^0-9Xx]", "");
                if (normalizedIdentifier.length() == 10 || normalizedIdentifier.length() == 13) {
                    updateIfPresent(
                            book.getIsbn(),
                            normalizedIdentifier,
                            book::setIsbn,
                            "isbn",
                            updatedFields);
                }
            }
            updateIfPresent(book.getDescription(), text(opf, "description"), book::setDescription, "description", updatedFields);

            int chapterCount = opf.getElementsByTagNameNS("*", "itemref").getLength();
            book.setChapterCount(chapterCount);
            updatedFields.add("chapterCount");
            extractEpubCover(book, zipFile, opfPath, opf, updatedFields);
        }
    }

    private void extractEpubCover(
            Book book,
            ZipFile zipFile,
            String opfPath,
            Document opf,
            List<String> updatedFields) throws Exception {
        if (book.getCoverUrl() != null && !book.getCoverUrl().isBlank()) {
            return;
        }

        Map<String, ManifestItem> manifest = readManifest(opf);
        ManifestItem coverItem = findCoverItem(opf, manifest);
        if (coverItem == null) {
            return;
        }

        String coverPath = resolveZipPath(opfPath, coverItem.href());
        ZipEntry coverEntry = zipFile.getEntry(coverPath);
        if (coverEntry == null) {
            log.warn("EPUB 封面文件不存在: {}", coverPath);
            return;
        }
        try (InputStream input = zipFile.getInputStream(coverEntry)) {
            byte[] imageBytes = input.readNBytes(10 * 1024 * 1024 + 1);
            if (bookCoverService.storeExtractedCover(
                    book, imageBytes, coverItem.mediaType())) {
                updatedFields.add("coverUrl");
            }
        }
    }

    private Map<String, ManifestItem> readManifest(Document opf) {
        Map<String, ManifestItem> manifest = new LinkedHashMap<>();
        NodeList nodes = opf.getElementsByTagNameNS("*", "item");
        for (int index = 0; index < nodes.getLength(); index++) {
            if (!(nodes.item(index) instanceof Element item)) {
                continue;
            }
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            if (id.isBlank() || href.isBlank()) {
                continue;
            }
            manifest.put(id, new ManifestItem(
                    id,
                    href,
                    item.getAttribute("media-type"),
                    item.getAttribute("properties")));
        }
        return manifest;
    }

    private ManifestItem findCoverItem(
            Document opf, Map<String, ManifestItem> manifest) {
        NodeList metaNodes = opf.getElementsByTagNameNS("*", "meta");
        for (int index = 0; index < metaNodes.getLength(); index++) {
            if (!(metaNodes.item(index) instanceof Element meta)) {
                continue;
            }
            if ("cover".equalsIgnoreCase(meta.getAttribute("name"))) {
                ManifestItem item = manifest.get(meta.getAttribute("content"));
                if (item != null) {
                    return item;
                }
            }
        }

        return manifest.values().stream()
                .filter(item -> containsToken(item.properties(), "cover-image"))
                .findFirst()
                .orElseGet(() -> manifest.values().stream()
                        .filter(item -> item.mediaType().startsWith("image/"))
                        .filter(item -> (item.id() + " " + item.href())
                                .toLowerCase(Locale.ROOT)
                                .contains("cover"))
                        .findFirst()
                        .orElse(null));
    }

    private boolean containsToken(String value, String token) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String part : value.trim().split("\\s+")) {
            if (token.equalsIgnoreCase(part)) {
                return true;
            }
        }
        return false;
    }

    private String resolveZipPath(String opfPath, String href) {
        String cleanHref = URLDecoder.decode(
                href.split("#", 2)[0], StandardCharsets.UTF_8);
        Path parent = Paths.get(opfPath).getParent();
        Path resolved = parent == null
                ? Paths.get(cleanHref).normalize()
                : parent.resolve(cleanHref).normalize();
        String normalized = resolved.toString().replace('\\', '/');
        if (resolved.isAbsolute() || normalized.startsWith("../")) {
            throw new IllegalArgumentException("EPUB 封面路径不安全");
        }
        return normalized;
    }

    private Document parseXml(ZipFile zipFile, String entryName) throws Exception {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            throw new IllegalArgumentException("EPUB 缺少文件: " + entryName);
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (InputStream input = zipFile.getInputStream(entry)) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private String text(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void updateTitleFromFilename(
            Book book, Path file, List<String> updatedFields) {
        String fileName = file.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String parsedTitle = extensionIndex > 0
                ? fileName.substring(0, extensionIndex)
                : fileName;
        if (parsedTitle.equals(book.getTitle())) {
            return;
        }
        book.setTitle(parsedTitle);
        updatedFields.add("title");
    }

    private void updateIfPresent(
            String currentValue,
            String parsedValue,
            java.util.function.Consumer<String> setter,
            String field,
            List<String> updatedFields) {
        if (parsedValue == null || parsedValue.equals(currentValue)) {
            return;
        }
        setter.accept(parsedValue);
        updatedFields.add(field);
    }

    private record ManifestItem(
            String id, String href, String mediaType, String properties) {}

    @Data
    @Builder
    public static class ParseResult {
        private Book book;
        private List<String> updatedFields;
        private Integer chapterCount;
        private String message;
    }
}
