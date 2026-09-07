package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.dto.CrawlerSettingsDtos.CrawlerRequestSettings;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.ProxySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlerHttpClientTest {
    private final ProxySettingsService proxySettings = mock(ProxySettingsService.class);
    private final CrawlerSettingsService crawlerSettings = mock(CrawlerSettingsService.class);
    private final CrawlerHttpClient client = new CrawlerHttpClient(
            new ObjectMapper(), proxySettings, crawlerSettings);
    private final CrawlerSite site = CrawlerSite.builder().id(1L).baseUrl("https://novel.example.com").build();

    @Test void acceptsSameDomainAndSubdomain() {
        assertEquals("novel.example.com", client.validateSiteUrl(site, "https://novel.example.com/book/1").getHost());
        assertEquals("img.novel.example.com", client.validateSiteUrl(site, "https://img.novel.example.com/cover/1").getHost());
    }

    @Test void rejectsForeignDomainAndNonHttpScheme() {
        assertThrows(ResponseStatusException.class, () -> client.validateSiteUrl(site, "https://evil.example/book/1"));
        assertThrows(ResponseStatusException.class, () -> client.validateSiteUrl(site, "file:///etc/passwd"));
    }

    @Test void siteProxyOverridesCrawlerPoolOtherwiseUsesPriorityOrderedPool() {
        when(proxySettings.activeCrawlerProxyUrls()).thenReturn(java.util.List.of(
                "http://proxy-high:8080", "http://proxy-low:8080"));
        assertEquals(java.util.List.of("http://proxy-high:8080", "http://proxy-low:8080"),
                client.proxyUrls(site));

        site.setProxy("http://site-proxy:7890");
        assertEquals(java.util.List.of("http://site-proxy:7890"), client.proxyUrls(site));
    }

    @Test void usesGlobalConsecutiveFailureLimit() {
        when(crawlerSettings.settings()).thenReturn(
                new CrawlerRequestSettings(30000, 4, 7, "CrawlerBot/2.0", "", "{}"));

        assertEquals(7, client.maxConsecutiveFailures());
    }
}
