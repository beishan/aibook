package com.aibook.service.conversion;

import com.aibook.dto.BookConversionUpdateRequest;
import com.aibook.dto.ConversionChapterDTO;
import com.aibook.model.entity.BookConversionTask;
import com.aibook.service.repair.EncodingDetectService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.*;

/** 生成不依赖外部命令、兼容 EPUB 3 阅读器的标准 EPUB。 */
@Component
public class TxtToEpubConverter implements BookConverter {
    private final ObjectMapper objectMapper;
    private final EncodingDetectService encodingDetectService;

    public TxtToEpubConverter(
            ObjectMapper objectMapper,
            com.aibook.service.repair.EncodingDetectService encodingDetectService) {
        this.objectMapper = objectMapper;
        this.encodingDetectService = encodingDetectService;
    }

    @Override
    public boolean supports(String sourceFormat, String targetFormat) {
        return "txt".equalsIgnoreCase(sourceFormat) && "epub".equalsIgnoreCase(targetFormat);
    }

    @Override
    public void convert(BookConversionTask task, Path output) throws Exception {
        String text = encodingDetectService.decodeWithEncoding(
                Paths.get(task.getSourcePath()), task.getEncoding());
        BookConversionUpdateRequest settings = task.getSettingsJson() == null
                ? new BookConversionUpdateRequest()
                : objectMapper.readValue(task.getSettingsJson(), BookConversionUpdateRequest.class);
        List<ConversionChapterDTO> chapters = objectMapper.readValue(
                task.getChaptersJson(), new TypeReference<>() {});
        chapters = chapters.stream().filter(ch -> !Boolean.TRUE.equals(ch.getIgnored())).toList();
        if (chapters.isEmpty()) {
            chapters = List.of(ConversionChapterDTO.builder()
                    .index(0).title("全文").startIndex(0).endIndex(text.length()).build());
        }

        Files.createDirectories(output.getParent());
        try (OutputStream file = Files.newOutputStream(output);
             ZipOutputStream zip = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
            writeStored(zip, "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            write(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                    """);

            String coverHref = null;
            String coverMediaType = null;
            if (task.getCoverPath() != null && Files.isRegularFile(Paths.get(task.getCoverPath()))) {
                Path cover = Paths.get(task.getCoverPath());
                String ext = extension(cover.getFileName().toString());
                coverHref = "cover." + ext;
                coverMediaType = switch (ext) {
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    default -> "image/jpeg";
                };
                write(zip, "OEBPS/" + coverHref, Files.readAllBytes(cover));
                write(zip, "OEBPS/cover.xhtml", coverPage(task, coverHref));
            }

            write(zip, "OEBPS/style.css", stylesheet(settings));
            List<String> chapterFiles = new ArrayList<>();
            for (int i = 0; i < chapters.size(); i++) {
                ConversionChapterDTO chapter = chapters.get(i);
                int start = Math.max(0, Math.min(text.length(), value(chapter.getStartIndex(), 0)));
                int end = Math.max(start, Math.min(text.length(), value(chapter.getEndIndex(), text.length())));
                String filename = String.format(Locale.ROOT, "chapter-%04d.xhtml", i + 1);
                chapterFiles.add(filename);
                String chapterText = ChapterTitleFormatter.stripSourceTitle(
                        text.substring(start, end),
                        defaultString(chapter.getSourceTitle(), chapter.getTitle()));
                write(zip, "OEBPS/" + filename,
                        chapterPage(task, chapter.getTitle(), clean(chapterText, settings)));
            }
            write(zip, "OEBPS/nav.xhtml", navigation(task, chapters, chapterFiles));
            write(zip, "OEBPS/content.opf", packageDocument(task, chapters, chapterFiles,
                    coverHref, coverMediaType));
        }
    }

    private String clean(String text, BookConversionUpdateRequest settings) {
        text = text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        if (!Boolean.FALSE.equals(settings.getTrimLineEnd())) text = text.replaceAll("(?m)[ \\t]+$", "");
        if (!Boolean.FALSE.equals(settings.getRemoveExtraBlankLines())) text = text.replaceAll("\\n{3,}", "\n\n");
        if (Boolean.TRUE.equals(settings.getNormalizeWidth())) {
            text = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC);
        }
        return text;
    }

    private String stylesheet(BookConversionUpdateRequest s) {
        String indent = safeCss(s.getFirstLineIndent(), "2em", Set.of("0", "1em", "2em"));
        String spacing = switch (Objects.toString(s.getParagraphSpacing(), "small")) {
            case "none" -> "0"; case "medium" -> ".8em"; case "large" -> "1.2em"; default -> ".45em";
        };
        double lineHeight = s.getLineHeight() == null ? 1.6 : Math.max(1.2, Math.min(2.4, s.getLineHeight()));
        return "body{font-family:serif;line-height:" + lineHeight + ";padding:1em;}"
                + "h1{text-align:center;margin:1.5em 0;}p{text-indent:" + indent
                + ";margin:" + spacing + " 0;}img{max-width:100%;height:auto;}"
                + ".cover{text-align:center;padding:0}.cover img{max-height:95vh;}";
    }

    private String chapterPage(BookConversionTask task, String title, String body) {
        StringBuilder paragraphs = new StringBuilder();
        for (String paragraph : body.split("\\n+")) {
            String value = paragraph.trim();
            if (!value.isEmpty() && !value.equals(title)) paragraphs.append("<p>").append(xml(value)).append("</p>");
        }
        return xhtmlHead(task, title) + "<body><section><h1>" + xml(title)
                + "</h1>" + paragraphs + "</section></body></html>";
    }

    private String coverPage(BookConversionTask task, String coverHref) {
        return xhtmlHead(task, "封面") + "<body class=\"cover\"><img src=\"" + coverHref
                + "\" alt=\"" + xml(task.getTitle()) + "\"/></body></html>";
    }

    private String xhtmlHead(BookConversionTask task, String title) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\""
                + xml(defaultString(task.getLanguage(), "zh-CN")) + "\"><head><meta charset=\"UTF-8\"/>"
                + "<title>" + xml(title) + "</title><link rel=\"stylesheet\" href=\"style.css\"/></head>";
    }

    private String navigation(BookConversionTask task, List<ConversionChapterDTO> chapters, List<String> files) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < chapters.size(); i++) items.append("<li><a href=\"").append(files.get(i))
                .append("\">").append(xml(chapters.get(i).getTitle())).append("</a></li>");
        return xhtmlHead(task, "目录") + "<body><nav epub:type=\"toc\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
                + "<h1>目录</h1><ol>" + items + "</ol></nav></body></html>";
    }

    private String packageDocument(BookConversionTask task, List<ConversionChapterDTO> chapters,
            List<String> files, String coverHref, String coverMediaType) {
        String id = "urn:uuid:aibook-conversion-" + task.getId();
        StringBuilder manifest = new StringBuilder("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/><item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>");
        StringBuilder spine = new StringBuilder();
        if (coverHref != null) {
            manifest.append("<item id=\"cover-image\" href=\"").append(coverHref).append("\" media-type=\"").append(coverMediaType).append("\" properties=\"cover-image\"/><item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>");
            spine.append("<itemref idref=\"cover\"/>");
        }
        for (int i = 0; i < files.size(); i++) {
            manifest.append("<item id=\"c").append(i).append("\" href=\"").append(files.get(i)).append("\" media-type=\"application/xhtml+xml\"/>");
            spine.append("<itemref idref=\"c").append(i).append("\"/>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"book-id\" xml:lang=\"" + xml(defaultString(task.getLanguage(), "zh-CN")) + "\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"book-id\">" + id + "</dc:identifier><dc:title>" + xml(task.getTitle()) + "</dc:title><dc:creator>" + xml(defaultString(task.getAuthor(), "未知作者")) + "</dc:creator><dc:language>" + xml(defaultString(task.getLanguage(), "zh-CN")) + "</dc:language>" + optional("dc:description", task.getDescription()) + optional("dc:publisher", task.getPublisher()) + optional("dc:date", task.getPublishDate()) + optional("dc:identifier", task.getIsbn()) + "<meta property=\"dcterms:modified\">" + LocalDate.now() + "T00:00:00Z</meta></metadata><manifest>" + manifest + "</manifest><spine>" + spine + "</spine></package>";
    }

    private String optional(String tag, String value) { return value == null || value.isBlank() ? "" : "<" + tag + ">" + xml(value) + "</" + tag + ">"; }
    private String extension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? "jpg" : name.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private String defaultString(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String safeCss(String value, String fallback, Set<String> allowed) { return value != null && allowed.contains(value) ? value : fallback; }
    private String xml(String value) { return Objects.toString(value, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }
    private void write(ZipOutputStream zip, String name, String value) throws IOException { write(zip, name, value.getBytes(StandardCharsets.UTF_8)); }
    private void write(ZipOutputStream zip, String name, byte[] value) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(value); zip.closeEntry(); }
    private void writeStored(ZipOutputStream zip, String name, byte[] value) throws IOException { CRC32 crc = new CRC32(); crc.update(value); ZipEntry entry = new ZipEntry(name); entry.setMethod(ZipEntry.STORED); entry.setSize(value.length); entry.setCompressedSize(value.length); entry.setCrc(crc.getValue()); zip.putNextEntry(entry); zip.write(value); zip.closeEntry(); }
}
