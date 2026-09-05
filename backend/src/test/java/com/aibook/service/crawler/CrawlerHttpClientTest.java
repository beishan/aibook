package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class CrawlerHttpClientTest {
    private final CrawlerHttpClient client = new CrawlerHttpClient(new ObjectMapper());
    private final CrawlerSite site = CrawlerSite.builder().id(1L).baseUrl("https://novel.example.com").build();

    @Test void acceptsSameDomainAndSubdomain() {
        assertEquals("novel.example.com", client.validateSiteUrl(site, "https://novel.example.com/book/1").getHost());
        assertEquals("img.novel.example.com", client.validateSiteUrl(site, "https://img.novel.example.com/cover/1").getHost());
    }

    @Test void rejectsForeignDomainAndNonHttpScheme() {
        assertThrows(ResponseStatusException.class, () -> client.validateSiteUrl(site, "https://evil.example/book/1"));
        assertThrows(ResponseStatusException.class, () -> client.validateSiteUrl(site, "file:///etc/passwd"));
    }
}
