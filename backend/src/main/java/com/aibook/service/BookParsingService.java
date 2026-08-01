package com.aibook.service;

import com.aibook.dto.BookTocItemDTO;
import com.aibook.model.entity.Book;
import com.aibook.repository.BookRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;
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
import java.util.regex.Pattern;

/**
 * 从书籍原始文件重新提取本地元数据。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookParsingService {

    private static final int MAX_DESCRIPTION_CHAPTER_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DESCRIPTION_LENGTH = 3000;
    private static final Pattern GENERATED_STORAGE_NAME = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

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
                case "epub" -> "EPUB 元数据、内容简介与章节已重新解析";
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
            Map<String, ManifestItem> manifest = readManifest(opf);
            String description = normalizeDescription(text(opf, "description"));
            if (!isMeaningfulDescription(description)) {
                description = extractEpubDescription(
                        zipFile, opfPath, opf, manifest);
            }
            updateIfPresent(
                    book.getDescription(),
                    description,
                    book::setDescription,
                    "description",
                    updatedFields);

            int chapterCount = opf.getElementsByTagNameNS("*", "itemref").getLength();
            book.setChapterCount(chapterCount);
            updatedFields.add("chapterCount");
            extractEpubCover(book, zipFile, opfPath, opf, updatedFields);
        }
    }

    /**
     * 读取可供客户端展示和跳转的书籍目录。
     */
    public List<BookTocItemDTO> getTableOfContents(Book book) {
        Path file = Paths.get(book.getFilePath());
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "书籍原始文件不存在");
        }
        try {
            return switch (book.getFormat().toLowerCase(Locale.ROOT)) {
                case "txt", "md" -> readTextTableOfContents(book, file);
                case "epub" -> readEpubTableOfContents(file);
                default -> List.of();
            };
        } catch (Exception exception) {
            log.error("读取书籍目录失败: {}", book.getFilePath(), exception);
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "目录读取失败: " + exception.getMessage(),
                    exception);
        }
    }

    private List<BookTocItemDTO> readTextTableOfContents(
            Book book, Path file) throws Exception {
        String chapterInfo = book.getChapterInfo();
        if (chapterInfo == null || chapterInfo.isBlank()) {
            chapterInfo = txtParserService.parseChapters(file);
        }
        JsonNode chapters = objectMapper.readTree(chapterInfo);
        List<BookTocItemDTO> result = new ArrayList<>();
        if (chapters == null || !chapters.isArray()) {
            return result;
        }
        for (int index = 0; index < chapters.size(); index++) {
            JsonNode chapter = chapters.get(index);
            result.add(BookTocItemDTO.builder()
                    .index(index)
                    .title(chapter.path("title").asText("第 " + (index + 1) + " 章"))
                    .startIndex(chapter.path("startIndex").asInt(0))
                    .endIndex(chapter.path("endIndex").asInt(0))
                    .depth(0)
                    .build());
        }
        return result;
    }

    private List<BookTocItemDTO> readEpubTableOfContents(Path file) throws Exception {
        try (ZipFile zipFile = new ZipFile(file.toFile())) {
            Document container = parseXml(zipFile, "META-INF/container.xml");
            Element rootFile = (Element) container
                    .getElementsByTagNameNS("*", "rootfile")
                    .item(0);
            if (rootFile == null) {
                return List.of();
            }

            String opfPath = rootFile.getAttribute("full-path");
            Document opf = parseXml(zipFile, opfPath);
            Map<String, ManifestItem> manifest = readManifest(opf);

            ManifestItem navigation = manifest.values().stream()
                    .filter(item -> containsToken(item.properties(), "nav"))
                    .findFirst()
                    .orElse(null);
            if (navigation != null) {
                List<BookTocItemDTO> items = readEpubNavigation(
                        zipFile, opfPath, navigation.href());
                if (!items.isEmpty()) {
                    return items;
                }
            }

            ManifestItem ncx = findNcxItem(opf, manifest);
            if (ncx != null) {
                List<BookTocItemDTO> items = readNcxNavigation(
                        zipFile, opfPath, ncx.href());
                if (!items.isEmpty()) {
                    return items;
                }
            }

            return buildSpineFallback(opf, manifest);
        }
    }

    private List<BookTocItemDTO> readEpubNavigation(
            ZipFile zipFile, String opfPath, String navigationHref) throws Exception {
        Document navigation = parseXml(
                zipFile, resolveZipPath(opfPath, navigationHref));
        Element tocNavigation = null;
        NodeList navNodes = navigation.getElementsByTagNameNS("*", "nav");
        for (int index = 0; index < navNodes.getLength(); index++) {
            Element candidate = (Element) navNodes.item(index);
            String type = candidate.getAttribute("epub:type");
            if (type.isBlank()) {
                type = candidate.getAttributeNS(
                        "http://www.idpf.org/2007/ops", "type");
            }
            if (containsToken(type, "toc")) {
                tocNavigation = candidate;
                break;
            }
        }
        if (tocNavigation == null && navNodes.getLength() > 0) {
            tocNavigation = (Element) navNodes.item(0);
        }
        if (tocNavigation == null) {
            return List.of();
        }

        List<BookTocItemDTO> result = new ArrayList<>();
        NodeList links = tocNavigation.getElementsByTagNameNS("*", "a");
        for (int index = 0; index < links.getLength(); index++) {
            Element link = (Element) links.item(index);
            String title = link.getTextContent().trim();
            String href = link.getAttribute("href");
            if (title.isBlank() || href.isBlank()) {
                continue;
            }
            result.add(BookTocItemDTO.builder()
                    .index(result.size())
                    .title(title)
                    .href(href)
                    .depth(Math.max(0, countAncestors(link, "ol") - 1))
                    .build());
        }
        return result;
    }

    private ManifestItem findNcxItem(
            Document opf, Map<String, ManifestItem> manifest) {
        NodeList spineNodes = opf.getElementsByTagNameNS("*", "spine");
        if (spineNodes.getLength() > 0) {
            String tocId = ((Element) spineNodes.item(0)).getAttribute("toc");
            if (!tocId.isBlank() && manifest.containsKey(tocId)) {
                return manifest.get(tocId);
            }
        }
        return manifest.values().stream()
                .filter(item -> "application/x-dtbncx+xml".equals(item.mediaType()))
                .findFirst()
                .orElse(null);
    }

    private String extractEpubDescription(
            ZipFile zipFile,
            String opfPath,
            Document opf,
            Map<String, ManifestItem> manifest) {
        try {
            DescriptionChapter best = null;

            ManifestItem navigation = manifest.values().stream()
                    .filter(item -> containsToken(item.properties(), "nav"))
                    .findFirst()
                    .orElse(null);
            if (navigation != null) {
                String navigationPath = resolveZipPath(opfPath, navigation.href());
                Document navigationDocument = parseXml(zipFile, navigationPath);
                NodeList links = navigationDocument.getElementsByTagNameNS("*", "a");
                for (int index = 0; index < links.getLength(); index++) {
                    Element link = (Element) links.item(index);
                    best = betterDescriptionChapter(
                            best,
                            link.getTextContent(),
                            link.getAttribute("href"),
                            navigationPath);
                }
            }

            ManifestItem ncx = findNcxItem(opf, manifest);
            if (ncx != null) {
                String ncxPath = resolveZipPath(opfPath, ncx.href());
                Document ncxDocument = parseXml(zipFile, ncxPath);
                NodeList points = ncxDocument.getElementsByTagNameNS("*", "navPoint");
                for (int index = 0; index < points.getLength(); index++) {
                    Element point = (Element) points.item(index);
                    NodeList labels = point.getElementsByTagNameNS("*", "navLabel");
                    NodeList contents = point.getElementsByTagNameNS("*", "content");
                    if (labels.getLength() == 0 || contents.getLength() == 0) {
                        continue;
                    }
                    best = betterDescriptionChapter(
                            best,
                            labels.item(0).getTextContent(),
                            ((Element) contents.item(0)).getAttribute("src"),
                            ncxPath);
                }
            }

            for (ManifestItem item : manifest.values()) {
                if (!isHtmlItem(item)) {
                    continue;
                }
                int score = descriptionChapterScore(item.id() + " " + item.href());
                if (score < 0 || (best != null && score <= best.score())) {
                    continue;
                }
                best = new DescriptionChapter(
                        item.id(),
                        resolveZipPath(opfPath, item.href()),
                        score);
            }

            return best == null
                    ? null
                    : readDescriptionChapter(zipFile, best);
        } catch (Exception exception) {
            log.warn("从 EPUB 简介章节提取内容失败: {}", exception.getMessage());
            return null;
        }
    }

    private DescriptionChapter betterDescriptionChapter(
            DescriptionChapter current,
            String title,
            String href,
            String containingEntryPath) {
        int score = descriptionChapterScore(title);
        if (score < 0 || href == null || href.isBlank()
                || (current != null && score <= current.score())) {
            return current;
        }
        return new DescriptionChapter(
                title.trim(),
                resolveZipPath(containingEntryPath, href),
                score);
    }

    private int descriptionChapterScore(String title) {
        if (title == null || title.isBlank()) {
            return -1;
        }
        String normalized = title.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{P}\\p{S}]+", "");
        if (normalized.contains("作者简介")
                || normalized.contains("译者简介")
                || normalized.contains("人物简介")) {
            return -1;
        }
        if (normalized.equals("内容简介")
                || normalized.equals("书籍简介")
                || normalized.equals("图书简介")
                || normalized.equals("本书简介")
                || normalized.equals("作品简介")
                || normalized.equals("内容提要")
                || normalized.equals("内容梗概")) {
            return 120;
        }
        if (normalized.contains("内容简介")
                || normalized.contains("书籍简介")
                || normalized.contains("图书简介")
                || normalized.contains("本书简介")
                || normalized.contains("作品简介")
                || normalized.contains("内容提要")
                || normalized.contains("内容梗概")) {
            return 110;
        }
        if (normalized.equals("aboutthisbook")
                || normalized.equals("bookdescription")
                || normalized.equals("synopsis")
                || normalized.equals("summary")) {
            return 100;
        }
        if (normalized.equals("简介")
                || normalized.equals("description")
                || normalized.equals("introduction")) {
            return 90;
        }
        if (normalized.contains("aboutthisbook")
                || normalized.contains("bookdescription")
                || normalized.contains("synopsis")
                || normalized.contains("summary")) {
            return 80;
        }
        return -1;
    }

    private boolean isHtmlItem(ManifestItem item) {
        return "application/xhtml+xml".equalsIgnoreCase(item.mediaType())
                || "text/html".equalsIgnoreCase(item.mediaType());
    }

    private String readDescriptionChapter(
            ZipFile zipFile, DescriptionChapter chapter) throws Exception {
        ZipEntry entry = zipFile.getEntry(chapter.entryPath());
        if (entry == null
                || entry.getSize() > MAX_DESCRIPTION_CHAPTER_BYTES) {
            return null;
        }
        byte[] content;
        try (InputStream input = zipFile.getInputStream(entry)) {
            content = input.readNBytes(MAX_DESCRIPTION_CHAPTER_BYTES + 1);
        }
        if (content.length > MAX_DESCRIPTION_CHAPTER_BYTES) {
            return null;
        }

        var document = Jsoup.parse(
                new String(content, StandardCharsets.UTF_8));
        document.select("script, style, nav").remove();
        List<String> paragraphs = document.select("p").stream()
                .map(org.jsoup.nodes.Element::wholeText)
                .map(this::normalizeWhitespace)
                .filter(value -> !value.isBlank())
                .toList();
        String description = paragraphs.isEmpty()
                ? document.body().wholeText()
                : String.join("\n\n", paragraphs);
        description = normalizeWhitespace(description);
        if (chapter.title() != null && !chapter.title().isBlank()) {
            description = description.replaceFirst(
                    "^\\s*" + java.util.regex.Pattern.quote(chapter.title().trim())
                            + "\\s*[:：]?\\s*",
                    "");
        }
        if (!isMeaningfulDescription(description)) {
            return null;
        }
        return description.length() > MAX_DESCRIPTION_LENGTH
                ? description.substring(0, MAX_DESCRIPTION_LENGTH).trim() + "…"
                : description;
    }

    private String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var document = Jsoup.parseBodyFragment(value);
        document.select("script, style").remove();
        List<String> paragraphs = document.select("p").stream()
                .map(org.jsoup.nodes.Element::wholeText)
                .map(this::normalizeWhitespace)
                .filter(paragraph -> !paragraph.isBlank())
                .toList();
        String normalized = paragraphs.isEmpty()
                ? document.body().wholeText()
                : String.join("\n\n", paragraphs);
        normalized = normalizeWhitespace(normalized);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\p{Zs}]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private boolean isMeaningfulDescription(String value) {
        return value != null
                && value.replaceAll("\\s+", "").length() >= 12;
    }

    private List<BookTocItemDTO> readNcxNavigation(
            ZipFile zipFile, String opfPath, String ncxHref) throws Exception {
        Document ncx = parseXml(zipFile, resolveZipPath(opfPath, ncxHref));
        NodeList points = ncx.getElementsByTagNameNS("*", "navPoint");
        List<BookTocItemDTO> result = new ArrayList<>();
        for (int index = 0; index < points.getLength(); index++) {
            Element point = (Element) points.item(index);
            NodeList labels = point.getElementsByTagNameNS("*", "navLabel");
            NodeList contents = point.getElementsByTagNameNS("*", "content");
            if (labels.getLength() == 0 || contents.getLength() == 0) {
                continue;
            }
            String title = labels.item(0).getTextContent().trim();
            String href = ((Element) contents.item(0)).getAttribute("src");
            if (title.isBlank() || href.isBlank()) {
                continue;
            }
            result.add(BookTocItemDTO.builder()
                    .index(result.size())
                    .title(title)
                    .href(href)
                    .depth(countAncestors(point, "navPoint"))
                    .build());
        }
        return result;
    }

    private List<BookTocItemDTO> buildSpineFallback(
            Document opf, Map<String, ManifestItem> manifest) {
        NodeList spineItems = opf.getElementsByTagNameNS("*", "itemref");
        List<BookTocItemDTO> result = new ArrayList<>();
        for (int index = 0; index < spineItems.getLength(); index++) {
            String idref = ((Element) spineItems.item(index)).getAttribute("idref");
            ManifestItem item = manifest.get(idref);
            if (item == null) {
                continue;
            }
            result.add(BookTocItemDTO.builder()
                    .index(result.size())
                    .title("第 " + (result.size() + 1) + " 章")
                    .href(item.href())
                    .depth(0)
                    .build());
        }
        return result;
    }

    private int countAncestors(Node node, String localName) {
        int count = 0;
        Node current = node.getParentNode();
        while (current != null) {
            String currentName = current.getLocalName() != null
                    ? current.getLocalName()
                    : current.getNodeName();
            if (localName.equals(currentName)) {
                count++;
            }
            current = current.getParentNode();
        }
        return count;
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

    private String resolveZipPath(String containingEntryPath, String href) {
        String cleanHref = URLDecoder.decode(
                href.split("#", 2)[0], StandardCharsets.UTF_8);
        Path parent = Paths.get(containingEntryPath).getParent();
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
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try (InputStream input = zipFile.getInputStream(entry)) {
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) ->
                    new InputSource(new StringReader("")));
            return builder.parse(input);
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
        // Uploaded files use UUIDs on disk. That storage implementation detail must
        // never replace a meaningful title obtained from the upload name or metadata.
        if (isGeneratedStorageTitle(parsedTitle)
                && book.getTitle() != null
                && !book.getTitle().isBlank()) {
            return;
        }
        if (parsedTitle.equals(book.getTitle())) {
            return;
        }
        book.setTitle(parsedTitle);
        updatedFields.add("title");
    }

    static boolean isGeneratedStorageTitle(String value) {
        return value != null && GENERATED_STORAGE_NAME.matcher(value.trim()).matches();
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

    private record DescriptionChapter(
            String title, String entryPath, int score) {}

    @Data
    @Builder
    public static class ParseResult {
        private Book book;
        private List<String> updatedFields;
        private Integer chapterCount;
        private String message;
    }
}
