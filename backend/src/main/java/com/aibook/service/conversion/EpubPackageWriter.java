package com.aibook.service.conversion;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes the shared EPUB 3 container used by import conversions and rewrite exports. */
@Component
public class EpubPackageWriter {

    public record Metadata(String title, String author, String language, String description,
                           String publisher, String publishDate, String isbn, String identifier) { }

    public record Chapter(String title, String navigationTitle, String contentHtml,
                          boolean includeHeading) {
        public Chapter(String title, String contentHtml, boolean includeHeading) {
            this(title, title, contentHtml, includeHeading);
        }
    }

    public void write(Path output, Metadata metadata, List<Chapter> chapters,
                      String stylesheet, Path coverPath) throws IOException {
        if (chapters == null || chapters.isEmpty()) {
            throw new IllegalArgumentException("EPUB 至少需要一个章节");
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(output), StandardCharsets.UTF_8)) {
            writeStored(zip, "mimetype",
                    "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            write(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                    """);

            String coverHref = writeCover(zip, coverPath, metadata.title());
            String coverMediaType = coverMediaType(coverHref);
            write(zip, "OEBPS/style.css", stylesheet);

            List<String> chapterFiles = writeChapters(zip, metadata, chapters);
            write(zip, "OEBPS/nav.xhtml", navigation(metadata, chapters, chapterFiles));
            write(zip, "OEBPS/content.opf", packageDocument(metadata, chapters,
                    chapterFiles, coverHref, coverMediaType));
        }
    }

    private String writeCover(ZipOutputStream zip, Path coverPath, String title)
            throws IOException {
        if (coverPath == null || !Files.isRegularFile(coverPath)) return null;
        String extension = extension(coverPath.getFileName().toString());
        String coverHref = "cover." + extension;
        write(zip, "OEBPS/" + coverHref, Files.readAllBytes(coverPath));
        write(zip, "OEBPS/cover.xhtml", xhtml("封面", "zh-CN",
                "<body class=\"cover\"><img src=\"" + coverHref + "\" alt=\""
                        + xml(title) + "\"/></body>"));
        return coverHref;
    }

    private String coverMediaType(String coverHref) {
        if (coverHref == null) return null;
        return switch (extension(coverHref)) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/jpeg";
        };
    }

    private List<String> writeChapters(ZipOutputStream zip, Metadata metadata,
                                       List<Chapter> chapters) throws IOException {
        List<String> files = new ArrayList<>();
        for (int index = 0; index < chapters.size(); index++) {
            Chapter chapter = chapters.get(index);
            String filename = String.format(Locale.ROOT, "chapter-%04d.xhtml", index + 1);
            files.add(filename);
            String heading = chapter.includeHeading()
                    ? "<h1>" + xml(chapter.title()) + "</h1>" : "";
            String body = "<body><section>" + heading + chapter.contentHtml() + "</section></body>";
            write(zip, "OEBPS/" + filename,
                    xhtml(chapter.title(), metadata.language(), body));
        }
        return files;
    }

    private String navigation(Metadata metadata, List<Chapter> chapters, List<String> files) {
        StringBuilder items = new StringBuilder();
        for (int index = 0; index < chapters.size(); index++) {
            items.append("<li><a href=\"").append(files.get(index)).append("\">")
                    .append(xml(chapters.get(index).navigationTitle()))
                    .append("</a></li>");
        }
        String body = "<body><nav epub:type=\"toc\"><h1>目录</h1><ol>"
                + items + "</ol></nav></body>";
        return xhtml("目录", metadata.language(), body);
    }

    private String packageDocument(Metadata metadata, List<Chapter> chapters,
                                   List<String> files, String coverHref,
                                   String coverMediaType) {
        StringBuilder manifest = new StringBuilder(
                "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
                        + "<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>");
        StringBuilder spine = new StringBuilder();
        if (coverHref != null) {
            manifest.append("<item id=\"cover-image\" href=\"").append(coverHref)
                    .append("\" media-type=\"").append(coverMediaType)
                    .append("\" properties=\"cover-image\"/><item id=\"cover\" "
                            + "href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>");
            spine.append("<itemref idref=\"cover\"/>");
        }
        for (int index = 0; index < files.size(); index++) {
            manifest.append("<item id=\"chapter-").append(index + 1)
                    .append("\" href=\"").append(files.get(index))
                    .append("\" media-type=\"application/xhtml+xml\"/>");
            spine.append("<itemref idref=\"chapter-").append(index + 1).append("\"/>");
        }
        String modified = DateTimeFormatter.ISO_INSTANT.format(
                Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" "
                + "unique-identifier=\"book-id\" xml:lang=\"" + xml(metadata.language()) + "\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:dcterms=\"http://purl.org/dc/terms/\">"
                + "<dc:identifier id=\"book-id\">" + xml(metadata.identifier())
                + "</dc:identifier><dc:title>" + xml(metadata.title()) + "</dc:title>"
                + "<dc:creator>" + xml(metadata.author()) + "</dc:creator>"
                + "<dc:language>" + xml(metadata.language()) + "</dc:language>"
                + optional("dc:description", metadata.description())
                + optional("dc:publisher", metadata.publisher())
                + optional("dc:date", metadata.publishDate())
                + optional("dc:identifier", metadata.isbn())
                + "<meta property=\"dcterms:modified\">" + modified
                + "</meta></metadata><manifest>" + manifest + "</manifest><spine>"
                + spine + "</spine></package>";
    }

    private String xhtml(String title, String language, String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" "
                + "xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\""
                + xml(defaultString(language, "zh-CN")) + "\"><head><meta charset=\"UTF-8\"/>"
                + "<title>" + xml(title) + "</title><link rel=\"stylesheet\" href=\"style.css\"/>"
                + "</head>" + body + "</html>";
    }

    private String optional(String tag, String value) {
        return value == null || value.isBlank()
                ? "" : "<" + tag + ">" + xml(value) + "</" + tag + ">";
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "jpg"
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return List.of("jpg", "jpeg", "png", "webp", "gif").contains(extension)
                ? extension : "jpg";
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String xml(String value) {
        return Objects.toString(value, "").replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private void write(ZipOutputStream zip, String path, String value) throws IOException {
        write(zip, path, value.getBytes(StandardCharsets.UTF_8));
    }

    private void write(ZipOutputStream zip, String path, byte[] value) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(value);
        zip.closeEntry();
    }

    private void writeStored(ZipOutputStream zip, String path, byte[] value) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(value);
        ZipEntry entry = new ZipEntry(path);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(value.length);
        entry.setCompressedSize(value.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(value);
        zip.closeEntry();
    }
}
