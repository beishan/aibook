package com.aibook.dto.crawler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class CrawlerAutoImportDtos {
    private CrawlerAutoImportDtos() { }

    public enum ExpressionType { KEYWORD, TAG, CHAPTER_COUNT }
    public enum Comparison { GT, GTE, EQ, NE, LT, LTE }

    public record Expression(
            @NotNull ExpressionType type,
            @Size(max = 50) List<@NotBlank @Size(max = 200) String> values,
            Comparison comparison,
            @Min(0) Integer chapterCount) { }

    public record Rule(
            @NotBlank @Size(max = 100) String name,
            @NotEmpty @Size(max = 20) List<@NotNull @Valid Expression> expressions) { }

    public record Configuration(
            boolean enabled,
            @NotEmpty @Size(max = 3)
            List<@NotBlank @Pattern(regexp = "STRUCTURED|TXT|EPUB") String> formats,
            @NotNull @Size(max = 50) List<@NotNull @Valid Rule> rules) { }
}
