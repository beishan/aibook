package com.aibook.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookHtmlSanitizerTest {

    private final BookHtmlSanitizer sanitizer = new BookHtmlSanitizer();

    @Test
    void removesExecutableAndRemoteContentWhileKeepingBookMarkup() {
        String clean = sanitizer.sanitize("""
                <article onclick="alert(1)">
                  <h1 id="chapter-1">标题</h1>
                  <script>alert('xss')</script>
                  <iframe src="https://evil.example"></iframe>
                  <img src="https://tracker.example/pixel.png" onerror="alert(2)">
                  <img src="data:image/png;base64,AA==">
                  <a href="https://example.com">参考资料</a>
                </article>
                """);

        assertThat(clean)
                .contains("<article>", "id=\"chapter-1\"", "data:image/png;base64,AA==")
                .contains("target=\"_blank\"", "rel=\"noopener noreferrer\"")
                .doesNotContain("script", "iframe", "onclick", "onerror", "tracker.example");
    }
}
