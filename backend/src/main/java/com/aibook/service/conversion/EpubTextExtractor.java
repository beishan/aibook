package com.aibook.service.conversion;

import com.aibook.dto.ConversionChapterDTO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts an EPUB reading order into deterministic UTF-8 plain text. */
@Component
public class EpubTextExtractor {

    private static final long MAX_DOCUMENT_BYTES = 32L * 1024 * 1024;
    private static final long MAX_TOTAL_TEXT_CHARS = 500L * 1024 * 1024;

    public ExtractedBook extract(Path epubPath) throws Exception {
        try (ZipFile zip = new ZipFile(epubPath.toFile(), StandardCharsets.UTF_8)) {
            String opfPath = locateOpf(zip);
            org.w3c.dom.Document opf = parseXml(readEntry(zip, opfPath, 8L * 1024 * 1024));
            String opfDirectory = parentPath(opfPath);
            Map<String, ManifestItem> manifest = manifest(opf, opfDirectory);
            Metadata metadata = metadata(opf);
            List<SpineItem> spine = spine(opf, manifest);
            if (spine.isEmpty()) throw new IOException("EPUB 缺少可读取的正文 spine");

            StringBuilder text = new StringBuilder();
            List<ConversionChapterDTO> chapters = new ArrayList<>();
            for (SpineItem item : spine) {
                if (item.properties().contains("nav")) continue;
                String html = new String(readEntry(zip, item.path(), MAX_DOCUMENT_BYTES), StandardCharsets.UTF_8);
                ChapterText chapter = extractChapter(html, chapters.size() + 1);
                if (chapter.content().isBlank()) continue;
                if (!text.isEmpty()) text.append("\n\n");
                int start = text.length();
                text.append(chapter.title()).append("\n\n").append(chapter.content().strip());
                int end = text.length();
                if (text.length() > MAX_TOTAL_TEXT_CHARS) {
                    throw new IOException("EPUB 解压后的正文超过 500MB 上限");
                }
                chapters.add(ConversionChapterDTO.builder()
                        .index(chapters.size())
                        .sourceTitle(chapter.title())
                        .title(chapter.title())
                        .startIndex(start)
                        .endIndex(end)
                        .ignored(false)
                        .build());
            }
            if (chapters.isEmpty()) throw new IOException("EPUB 未提取到可用正文");
            return new ExtractedBook(text.toString(), chapters, metadata.title(),
                    metadata.author(), metadata.language());
        }
    }

    private Map<String, ManifestItem> manifest(org.w3c.dom.Document opf, String base) {
        Map<String, ManifestItem> result = new LinkedHashMap<>();
        NodeList items = opf.getElementsByTagNameNS("*", "item");
        for (int index = 0; index < items.getLength(); index++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) items.item(index);
            String id = element.getAttribute("id");
            String mediaType = element.getAttribute("media-type");
            if (!id.isBlank() && ("application/xhtml+xml".equals(mediaType)
                    || "text/html".equals(mediaType))) {
                result.put(id, new ManifestItem(
                        resolvePath(base, element.getAttribute("href")),
                        element.getAttribute("properties")));
            }
        }
        return result;
    }

    private List<SpineItem> spine(
            org.w3c.dom.Document opf, Map<String, ManifestItem> manifest) {
        List<SpineItem> result = new ArrayList<>();
        NodeList items = opf.getElementsByTagNameNS("*", "itemref");
        for (int index = 0; index < items.getLength(); index++) {
            org.w3c.dom.Element reference = (org.w3c.dom.Element) items.item(index);
            if ("no".equalsIgnoreCase(reference.getAttribute("linear"))) continue;
            ManifestItem item = manifest.get(reference.getAttribute("idref"));
            if (item != null) result.add(new SpineItem(item.path(), item.properties()));
        }
        return result;
    }

    private Metadata metadata(org.w3c.dom.Document opf) {
        return new Metadata(
                firstText(opf, "title"),
                firstText(opf, "creator"),
                firstText(opf, "language"));
    }

    private String firstText(org.w3c.dom.Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : trimToNull(nodes.item(0).getTextContent());
    }

    private ChapterText extractChapter(String html, int chapterNumber) {
        Document document = Jsoup.parse(html, "", Parser.xmlParser());
        document.select("script,style,noscript,svg").remove();
        Element heading = document.selectFirst("h1,h2,h3,h4,h5,h6");
        String title = heading == null ? trimToNull(document.title()) : trimToNull(heading.text());
        if (title == null) title = "第 " + chapterNumber + " 章";

        List<String> blocks = new ArrayList<>();
        for (Element element : document.select("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre")) {
            String value = normalizeBlock(element.wholeText());
            if (value == null || (element == heading && value.equals(title))) continue;
            if (blocks.isEmpty() || !blocks.get(blocks.size() - 1).equals(value)) blocks.add(value);
        }
        if (blocks.isEmpty() && document.body() != null) {
            String bodyText = normalizeBlock(document.body().wholeText());
            if (bodyText != null && !bodyText.equals(title)) blocks.add(bodyText);
        }
        return new ChapterText(title, String.join("\n\n", blocks));
    }

    private String normalizeBlock(String value) {
        if (value == null) return null;
        String normalized = value.replace('\u00a0', ' ')
                .replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String locateOpf(ZipFile zip) throws Exception {
        org.w3c.dom.Document container = parseXml(
                readEntry(zip, "META-INF/container.xml", 1024 * 1024));
        NodeList roots = container.getElementsByTagNameNS("*", "rootfile");
        if (roots.getLength() == 0) throw new IOException("EPUB container.xml 缺少 rootfile");
        return sanitizePath(((org.w3c.dom.Element) roots.item(0)).getAttribute("full-path"));
    }

    private org.w3c.dom.Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private byte[] readEntry(ZipFile zip, String path, long limit) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null || entry.isDirectory()) throw new IOException("EPUB 资源不存在：" + path);
        if (entry.getSize() > limit) throw new IOException("EPUB 单个正文文件超过读取上限");
        try (var input = zip.getInputStream(entry); var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new IOException("EPUB 单个正文文件超过读取上限");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String resolvePath(String base, String href) {
        String path = Objects.toString(href, "").split("#", 2)[0];
        path = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        return sanitizePath(Paths.get(base).resolve(path).normalize().toString().replace('\\', '/'));
    }

    private String sanitizePath(String value) {
        String normalized = Objects.toString(value, "").replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        Path path = Paths.get(normalized).normalize();
        String result = path.toString().replace('\\', '/');
        if (normalized.isBlank() || path.isAbsolute() || result.equals("..") || result.startsWith("../")) {
            throw new IllegalArgumentException("EPUB 包含越界资源路径");
        }
        return result;
    }

    private String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private record ManifestItem(String path, String properties) {}
    private record SpineItem(String path, String properties) {}
    private record Metadata(String title, String author, String language) {}
    private record ChapterText(String title, String content) {}

    public record ExtractedBook(
            String text,
            List<ConversionChapterDTO> chapters,
            String title,
            String author,
            String language) {}
}
