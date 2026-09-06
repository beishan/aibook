package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerTaskServiceLoggingTest {
    @Test
    void contentPreviewFlattensWhitespaceAndLimitsToFiftyCharacters() {
        String preview = CrawlerTaskService.contentPreview("  第一行\n\t第二行  " + "字".repeat(50));

        assertThat(preview).doesNotContain("\n", "\r", "\t").endsWith("…");
        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(51);
    }

    @Test
    void contentPreviewDoesNotSplitSupplementaryCharacters() {
        String preview = CrawlerTaskService.contentPreview("😀".repeat(51));

        assertThat(preview).isEqualTo("😀".repeat(50) + "…");
    }

    @Test
    void contentPreviewHandlesEmptyContent() {
        assertThat(CrawlerTaskService.contentPreview(" \n\t ")).isEqualTo("(空)");
    }

    @Test
    void matchesAnyConfiguredContentFailureMarkerIgnoringCase() {
        CrawlerSite site = CrawlerSite.builder()
                .contentFailureMarkers("以下内容为VIP专属，升级会员即可继续阅读\nAccess Denied").build();

        assertThat(CrawlerTaskService.matchedContentFailureMarker(site,
                "本章提示：以下内容为VIP专属，升级会员即可继续阅读"))
                .isEqualTo("以下内容为VIP专属，升级会员即可继续阅读");
        assertThat(CrawlerTaskService.matchedContentFailureMarker(site, "ACCESS DENIED"))
                .isEqualTo("Access Denied");
        assertThat(CrawlerTaskService.matchedContentFailureMarker(site, "正常章节正文")).isNull();
    }
}
