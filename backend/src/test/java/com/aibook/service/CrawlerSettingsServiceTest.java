package com.aibook.service;

import com.aibook.dto.CrawlerSettingsDtos.CrawlerRequestSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlerSettingsServiceTest {

    @Test
    void returnsSafeDefaultsWhenNoCrawlerSettingsExist() {
        SystemConfigService configs = mock(SystemConfigService.class);
        when(configs.getConfigsByPrefix(CrawlerSettingsService.PREFIX)).thenReturn(Map.of());
        CrawlerSettingsService service = new CrawlerSettingsService(configs, new ObjectMapper());

        CrawlerRequestSettings settings = service.settings();

        assertThat(settings.timeoutMillis()).isEqualTo(15000);
        assertThat(settings.retryCount()).isEqualTo(2);
        assertThat(settings.maxConsecutiveFailures()).isEqualTo(5);
        assertThat(settings.headersJson()).isEqualTo("{}");
    }

    @Test
    void savesNormalizedGlobalCrawlerRequestSettings() {
        SystemConfigService configs = mock(SystemConfigService.class);
        CrawlerSettingsService service = new CrawlerSettingsService(configs, new ObjectMapper());

        CrawlerRequestSettings saved = service.update(new CrawlerRequestSettings(
                30000, 4, 7, " CustomBot/1.0 ", " session=abc ", "{\"X-Source\":\"library\"}"));

        assertThat(saved.userAgent()).isEqualTo("CustomBot/1.0");
        assertThat(saved.cookie()).isEqualTo("session=abc");
        assertThat(saved.headersJson()).isEqualTo("{\"X-Source\":\"library\"}");
        ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
        verify(configs).saveConfigs(values.capture());
        assertThat(values.getValue()).containsEntry("crawler.request.timeoutMillis", "30000")
                .containsEntry("crawler.request.retryCount", "4")
                .containsEntry("crawler.request.maxConsecutiveFailures", "7");
        assertThat(service.settings()).isSameAs(saved);
    }

    @Test
    void rejectsInvalidCustomHeaderJson() {
        CrawlerSettingsService service = new CrawlerSettingsService(
                mock(SystemConfigService.class), new ObjectMapper());

        assertThatThrownBy(() -> service.update(new CrawlerRequestSettings(
                15000, 2, 5, "", "", "not-json")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("字符串键值对 JSON");
    }
}
