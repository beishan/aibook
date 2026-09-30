package com.aibook.service.conversion;

import com.aibook.dto.BookConversionUpdateRequest;
import com.aibook.dto.ConversionChapterDTO;
import com.aibook.model.entity.BookConversionTask;
import com.aibook.service.repair.EncodingDetectService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class TxtToEpubConverter implements BookConverter {

    private final ObjectMapper objectMapper;
    private final EncodingDetectService encodingDetectService;
    private final EpubPackageWriter epubPackageWriter;

    public TxtToEpubConverter(ObjectMapper objectMapper,
                              EncodingDetectService encodingDetectService,
                              EpubPackageWriter epubPackageWriter) {
        this.objectMapper = objectMapper;
        this.encodingDetectService = encodingDetectService;
        this.epubPackageWriter = epubPackageWriter;
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
        List<ConversionChapterDTO> sourceChapters = objectMapper.readValue(
                task.getChaptersJson(), new TypeReference<>() { });
        sourceChapters = sourceChapters.stream()
                .filter(chapter -> !Boolean.TRUE.equals(chapter.getIgnored())).toList();
        if (sourceChapters.isEmpty()) {
            sourceChapters = List.of(ConversionChapterDTO.builder()
                    .index(0)
                    .title("全文")
                    .startIndex(0)
                    .endIndex(text.length())
                    .build());
        }

        List<EpubPackageWriter.Chapter> chapters = new java.util.ArrayList<>();
        for (ConversionChapterDTO sourceChapter : sourceChapters) {
            int start = Math.max(0, Math.min(text.length(), value(sourceChapter.getStartIndex(), 0)));
            int end = Math.max(start, Math.min(text.length(),
                    value(sourceChapter.getEndIndex(), text.length())));
            String chapterText = ChapterTitleFormatter.stripSourceTitle(
                    text.substring(start, end),
                    defaultString(sourceChapter.getSourceTitle(), sourceChapter.getTitle()));
            String cleanText = clean(chapterText, settings);
            String html = "<h1>" + xml(sourceChapter.getTitle()) + "</h1>"
                    + paragraphs(cleanText, sourceChapter.getTitle());
            chapters.add(new EpubPackageWriter.Chapter(sourceChapter.getTitle(), html, false));
        }

        Path cover = task.getCoverPath() == null ? null : Paths.get(task.getCoverPath());
        if (cover != null && !Files.isRegularFile(cover)) cover = null;
        EpubPackageWriter.Metadata metadata = new EpubPackageWriter.Metadata(
                defaultString(task.getTitle(), "未命名书籍"),
                defaultString(task.getAuthor(), "未知作者"),
                defaultString(task.getLanguage(), "zh-CN"),
                task.getDescription(),
                task.getPublisher(),
                task.getPublishDate(),
                task.getIsbn(),
                "urn:uuid:aibook-conversion-" + task.getId());
        epubPackageWriter.write(output, metadata, chapters, stylesheet(settings), cover);
    }

    private String clean(String text, BookConversionUpdateRequest settings) {
        String cleaned = text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        if (!Boolean.FALSE.equals(settings.getTrimLineEnd())) {
            cleaned = cleaned.replaceAll("(?m)[ \\t]+$", "");
        }
        if (!Boolean.FALSE.equals(settings.getRemoveExtraBlankLines())) {
            cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        }
        if (Boolean.TRUE.equals(settings.getNormalizeWidth())) {
            cleaned = java.text.Normalizer.normalize(
                    cleaned, java.text.Normalizer.Form.NFKC);
        }
        return cleaned;
    }

    private String paragraphs(String text, String title) {
        StringBuilder result = new StringBuilder();
        for (String paragraph : text.split("\\n+")) {
            String value = paragraph.trim();
            if (!value.isEmpty() && !value.equals(title)) {
                result.append("<p>").append(xml(value)).append("</p>");
            }
        }
        return result.toString();
    }

    private String stylesheet(BookConversionUpdateRequest settings) {
        String indent = safeCss(settings.getFirstLineIndent(), "2em", Set.of("0", "1em", "2em"));
        String spacing = switch (Objects.toString(settings.getParagraphSpacing(), "small")) {
            case "none" -> "0";
            case "medium" -> ".8em";
            case "large" -> "1.2em";
            default -> ".45em";
        };
        double lineHeight = settings.getLineHeight() == null
                ? 1.6 : Math.max(1.2, Math.min(2.4, settings.getLineHeight()));
        return "body{font-family:serif;line-height:" + lineHeight + ";padding:1em;}"
                + "h1{text-align:center;margin:1.5em 0;}p{text-indent:" + indent
                + ";margin:" + spacing + " 0;}img{max-width:100%;height:auto;}"
                + ".cover{text-align:center;padding:0}.cover img{max-height:95vh;}";
    }

    private String safeCss(String value, String fallback, Set<String> allowed) {
        return value != null && allowed.contains(value) ? value : fallback;
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String xml(String value) {
        return Objects.toString(value, "").replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
