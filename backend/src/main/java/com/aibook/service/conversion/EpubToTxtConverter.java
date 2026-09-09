package com.aibook.service.conversion;

import com.aibook.dto.BookConversionUpdateRequest;
import com.aibook.dto.ConversionChapterDTO;
import com.aibook.model.entity.BookConversionTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;

/** Converts EPUB spine content to a structured UTF-8 TXT file. */
@Component
public class EpubToTxtConverter implements BookConverter {

    private final ObjectMapper objectMapper;
    private final EpubTextExtractor extractor;

    public EpubToTxtConverter(ObjectMapper objectMapper, EpubTextExtractor extractor) {
        this.objectMapper = objectMapper;
        this.extractor = extractor;
    }

    @Override
    public boolean supports(String sourceFormat, String targetFormat) {
        return "epub".equalsIgnoreCase(sourceFormat) && "txt".equalsIgnoreCase(targetFormat);
    }

    @Override
    public void convert(BookConversionTask task, Path output) throws Exception {
        EpubTextExtractor.ExtractedBook extracted = extractor.extract(Paths.get(task.getSourcePath()));
        BookConversionUpdateRequest settings = task.getSettingsJson() == null
                ? new BookConversionUpdateRequest()
                : objectMapper.readValue(task.getSettingsJson(), BookConversionUpdateRequest.class);
        List<ConversionChapterDTO> chapters = objectMapper.readValue(
                task.getChaptersJson(), new TypeReference<>() {});
        chapters = chapters.stream().filter(chapter -> !Boolean.TRUE.equals(chapter.getIgnored())).toList();
        if (chapters.isEmpty()) {
            chapters = List.of(ConversionChapterDTO.builder()
                    .index(0).title(task.getTitle()).sourceTitle(task.getTitle())
                    .startIndex(0).endIndex(extracted.text().length()).build());
        }

        StringBuilder result = new StringBuilder();
        for (ConversionChapterDTO chapter : chapters) {
            int start = bounded(chapter.getStartIndex(), 0, extracted.text().length(), 0);
            int end = bounded(chapter.getEndIndex(), start, extracted.text().length(), extracted.text().length());
            String body = ChapterTitleFormatter.stripSourceTitle(
                    extracted.text().substring(start, end),
                    defaultString(chapter.getSourceTitle(), chapter.getTitle()));
            body = clean(body, settings);
            if (!result.isEmpty()) result.append("\n\n");
            result.append(defaultString(chapter.getTitle(), "正文")).append("\n\n").append(body.strip());
        }
        result.append('\n');
        Files.createDirectories(output.getParent());
        Files.writeString(output, result.toString(), StandardCharsets.UTF_8);
    }

    private String clean(String text, BookConversionUpdateRequest settings) {
        text = text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        if (!Boolean.FALSE.equals(settings.getTrimLineEnd())) {
            text = text.replaceAll("(?m)[ \\t]+$", "");
        }
        if (!Boolean.FALSE.equals(settings.getRemoveExtraBlankLines())) {
            text = text.replaceAll("\\n{3,}", "\n\n");
        }
        if (Boolean.TRUE.equals(settings.getNormalizeWidth())) {
            text = Normalizer.normalize(text, Normalizer.Form.NFKC);
        }
        return text;
    }

    private int bounded(Integer value, int minimum, int maximum, int fallback) {
        return Math.max(minimum, Math.min(maximum, value == null ? fallback : value));
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
