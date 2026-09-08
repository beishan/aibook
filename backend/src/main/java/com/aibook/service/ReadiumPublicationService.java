package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
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
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Builds the Readium Web Publication Manifest and exposes EPUB resources safely. */
@Service
public class ReadiumPublicationService {

    private static final long MAX_RESOURCE_BYTES = 64L * 1024 * 1024;
    public Map<String, Object> buildManifest(
            Book book, BookVersion version, String accessToken) throws IOException {
        Path epubPath = validateEpub(version);
        try (ZipFile zip = new ZipFile(epubPath.toFile())) {
            String opfPath = locateOpf(zip);
            Document opf = parseXml(readEntry(zip, opfPath, 8L * 1024 * 1024));
            String opfDirectory = parentPath(opfPath);

            Map<String, ManifestEntry> entriesById = new LinkedHashMap<>();
            NodeList manifestItems = opf.getElementsByTagNameNS("*", "item");
            for (int index = 0; index < manifestItems.getLength(); index++) {
                Element item = (Element) manifestItems.item(index);
                String id = item.getAttribute("id");
                String href = normalizeArchivePath(opfDirectory, item.getAttribute("href"));
                if (!id.isBlank() && !href.isBlank()) {
                    entriesById.put(id, new ManifestEntry(
                            href,
                            item.getAttribute("media-type"),
                            item.getAttribute("properties")));
                }
            }

            List<Map<String, Object>> readingOrder = new ArrayList<>();
            Set<String> spinePaths = new java.util.LinkedHashSet<>();
            NodeList spineItems = opf.getElementsByTagNameNS("*", "itemref");
            for (int index = 0; index < spineItems.getLength(); index++) {
                Element itemRef = (Element) spineItems.item(index);
                ManifestEntry entry = entriesById.get(itemRef.getAttribute("idref"));
                if (entry != null) {
                    spinePaths.add(entry.path());
                    readingOrder.add(toLink(entry, null));
                }
            }
            if (readingOrder.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "EPUB 缺少可阅读的 spine");
            }

            List<Map<String, Object>> resources = new ArrayList<>();
            for (ManifestEntry entry : entriesById.values()) {
                if (!spinePaths.contains(entry.path())) {
                    resources.add(toLink(entry, null));
                }
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("@type", "http://schema.org/Book");
            metadata.put("title", book.getTitle());
            if (book.getAuthor() != null && !book.getAuthor().isBlank()) {
                metadata.put("author", List.of(Map.of("name", book.getAuthor())));
            }
            if (book.getLanguage() != null && !book.getLanguage().isBlank()) {
                metadata.put("language", book.getLanguage());
            }

            String selfHref = "/api/readium-resources/" + accessToken + "/manifest.json";
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("@context", List.of("https://readium.org/webpub-manifest/context.jsonld"));
            result.put("metadata", metadata);
            result.put("links", List.of(Map.of(
                    "rel", "self", "href", selfHref,
                    "type", "application/webpub+json")));
            result.put("readingOrder", readingOrder);
            result.put("resources", resources);
            return result;
        }
    }

    public ResourceData readResource(BookVersion version, String requestedPath) throws IOException {
        Path epubPath = validateEpub(version);
        String resourcePath = sanitizeRequestedPath(requestedPath);
        try (ZipFile zip = new ZipFile(epubPath.toFile())) {
            ZipEntry entry = zip.getEntry(resourcePath);
            if (entry == null || entry.isDirectory()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EPUB 资源不存在");
            }
            if (entry.getSize() > MAX_RESOURCE_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "EPUB 资源超过读取上限");
            }
            byte[] bytes = readEntry(zip, resourcePath, MAX_RESOURCE_BYTES);
            String mediaType = mediaType(resourcePath);
            String etag = "\"" + Long.toHexString(entry.getCrc()) + "-"
                    + Long.toHexString(entry.getSize()) + "\"";
            return new ResourceData(bytes, mediaType, etag);
        }
    }

    private Path validateEpub(BookVersion version) {
        if (!"epub".equalsIgnoreCase(version.getFormat())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Readium 仅支持 EPUB 版本");
        }
        Path path = Paths.get(version.getFilePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EPUB 文件不存在");
        }
        return path;
    }

    private String locateOpf(ZipFile zip) throws IOException {
        Document container = parseXml(readEntry(zip, "META-INF/container.xml", 1024 * 1024));
        NodeList rootFiles = container.getElementsByTagNameNS("*", "rootfile");
        if (rootFiles.getLength() == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "EPUB container.xml 无根文件");
        }
        return sanitizeRequestedPath(((Element) rootFiles.item(0)).getAttribute("full-path"));
    }

    private Document parseXml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "EPUB XML 解析失败", error);
        }
    }

    private byte[] readEntry(ZipFile zip, String path, long limit) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null || entry.isDirectory()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EPUB 资源不存在");
        }
        if (entry.getSize() > limit) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "EPUB 资源超过读取上限");
        }
        try (var input = zip.getInputStream(entry); var output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "EPUB 资源超过读取上限");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private Map<String, Object> toLink(ManifestEntry entry, String title) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("href", "resources/" + encodePath(entry.path()));
        link.put("type", entry.mediaType().isBlank() ? mediaType(entry.path()) : entry.mediaType());
        if (title != null && !title.isBlank()) link.put("title", title);
        if (entry.properties().contains("cover-image")) link.put("rel", "cover");
        return link;
    }

    private String normalizeArchivePath(String base, String href) {
        if (href == null || href.isBlank()) return "";
        String withoutFragment = href.split("#", 2)[0];
        try {
            String decodedHref = URLDecoder.decode(
                    withoutFragment.replace("+", "%2B"), StandardCharsets.UTF_8);
            Path resolved = Paths.get(base).resolve(decodedHref).normalize();
            return sanitizeRequestedPath(resolved.toString().replace('\\', '/'));
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "EPUB 包含无效资源路径", error);
        }
    }

    private String sanitizeRequestedPath(String rawPath) {
        String normalized = rawPath == null ? "" : rawPath.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isBlank() || normalized.contains("\u0000")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EPUB 资源路径无效");
        }
        Path path = Paths.get(normalized).normalize();
        String result = path.toString().replace('\\', '/');
        if (path.isAbsolute() || result.equals("..") || result.startsWith("../")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EPUB 资源路径越界");
        }
        return result;
    }

    private String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    private String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private String mediaType(String path) {
        String extension = path.contains(".")
                ? path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        return switch (extension) {
            case "xhtml", "xht" -> "application/xhtml+xml";
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "text/javascript";
            case "svg" -> "image/svg+xml";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "ttf" -> "font/ttf";
            case "otf" -> "font/otf";
            case "mp3" -> "audio/mpeg";
            case "mp4" -> "video/mp4";
            case "ncx" -> "application/x-dtbncx+xml";
            default -> "application/octet-stream";
        };
    }

    private record ManifestEntry(String path, String mediaType, String properties) {}

    public record ResourceData(byte[] bytes, String mediaType, String etag) {}
}
