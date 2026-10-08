package com.aibook.service.crawler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerSiteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class CrawlerSiteIconServiceTest {
    @TempDir Path directory;
    private CrawlerSiteRepository sites;
    private CrawlerSiteIconService service;
    private final User user = User.builder().id(1L).build();
    // Real 1px PNG rather than a file extension disguised as an image.
    private final byte[] png = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jZ9kAAAAASUVORK5CYII=");

    @BeforeEach
    void setup() {
        sites = mock(CrawlerSiteRepository.class);
        service = spy(new CrawlerSiteIconService(sites, new ObjectMapper()));
        ReflectionTestUtils.setField(service, "uploadPath", directory.toString());
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user)
                .baseUrl("https://example.com").homeUrl("https://example.com/books/").build();
        when(sites.findByIdAndUser(7L, user)).thenReturn(Optional.of(site));
    }

    private Connection.Response response(String url, String html, byte[] bytes) throws Exception {
        Connection.Response result = mock(Connection.Response.class);
        when(result.url()).thenReturn(new URL(url));
        when(result.body()).thenReturn(html);
        when(result.bodyAsBytes()).thenReturn(bytes);
        return result;
    }

    @Test
    void resolvesRelativeBaseAndCdnIconsAndPrioritizesTabIcon() {
        var doc = Jsoup.parse("<base href='https://cdn.example.com/assets/'>"
                + "<link rel='apple-touch-icon' href='/apple.png'>"
                + "<link rel='shortcut ICON' href='tab.png'>"
                + "<link rel='stylesheet' href='/style.css'>"
                + "<link rel='icon icon' href='//other.example.com/logo.ico'>",
                "https://example.com/books/");
        assertEquals(java.util.List.of("https://cdn.example.com/assets/tab.png",
                "https://other.example.com/logo.ico", "https://cdn.example.com/apple.png"),
                new java.util.ArrayList<>(CrawlerSiteIconService.discover(doc)));
    }

    @Test
    void automaticallyCollectsAndPersistsWithoutRepeatedNetworkRequests() throws Exception {
        doReturn(response("https://example.com/books/", "<link rel='icon' href='/icon.png'>", new byte[0]))
                .when(service).fetch(eq("https://example.com/books/"), anyInt(), anyLong());
        doReturn(response("https://example.com/icon.png", "", png))
                .when(service).fetch(eq("https://example.com/icon.png"), anyInt(), anyLong());
        var icon = service.get(user, 7L);
        assertEquals("AUTO", icon.source());
        assertEquals("https://example.com/icon.png", icon.sourceUrl());
        assertTrue(icon.dataUrl().startsWith("data:image/png;base64,"));
        assertEquals(icon, service.get(user, 7L));
        verify(service, times(1)).fetch(eq("https://example.com/icon.png"), anyInt(), anyLong());
    }

    @Test
    void fallsBackToConventionalIconWhenHomepageFails() throws Exception {
        doThrow(new IOException("blocked")).when(service)
                .fetch(eq("https://example.com/books/"), anyInt(), anyLong());
        doReturn(response("https://example.com/favicon.ico", "", png)).when(service)
                .fetch(eq("https://example.com/favicon.ico"), anyInt(), anyLong());
        assertEquals("https://example.com/favicon.ico", service.get(user, 7L).sourceUrl());
    }

    @Test
    void failedAutomaticCollectionIsCachedUntilExplicitRetry() throws Exception {
        doThrow(new IOException("offline")).when(service).fetch(anyString(), anyInt(), anyLong());
        assertNotNull(service.get(user, 7L).error());
        service.get(user, 7L);
        verify(service, times(2)).fetch(anyString(), anyInt(), anyLong());
    }

    @Test
    void failedRefreshRetainsUploadedIcon() throws Exception {
        var original = service.upload(user, 7L, new MockMultipartFile("file", "logo.png", "image/png", png));
        doThrow(new IOException("offline")).when(service).fetch(anyString(), anyInt(), anyLong());
        assertThrows(ResponseStatusException.class, () -> service.refresh(user, 7L));
        assertEquals(original, service.get(user, 7L));
    }

    @Test
    void successfulRefreshReplacesCustomIconWithCollectedIcon() throws Exception {
        service.upload(user, 7L, new MockMultipartFile("file", png));
        doReturn(response("https://example.com/books/", "", new byte[0])).when(service)
                .fetch(eq("https://example.com/books/"), anyInt(), anyLong());
        doReturn(response("https://example.com/favicon.ico", "", png)).when(service)
                .fetch(eq("https://example.com/favicon.ico"), anyInt(), anyLong());
        assertEquals("AUTO", service.refresh(user, 7L).source());
    }

    @Test
    void customUploadSurvivesServiceRestart() {
        var uploaded = service.upload(user, 7L, new MockMultipartFile("file", png));
        var restarted = new CrawlerSiteIconService(sites, new ObjectMapper());
        ReflectionTestUtils.setField(restarted, "uploadPath", directory.toString());
        assertEquals(uploaded, restarted.get(user, 7L));
    }

    @Test
    void validatesBytesInsteadOfTrustingFilenameOrMime() {
        assertThrows(ResponseStatusException.class, () -> service.upload(user, 7L,
                new MockMultipartFile("file", "fake.png", "image/png", "<html>".getBytes())));
        assertThrows(ResponseStatusException.class, () -> service.upload(user, 7L,
                new MockMultipartFile("file", new byte[CrawlerSiteIconService.MAX_BYTES + 1])));
        assertThrows(ResponseStatusException.class, () -> service.upload(user, 7L,
                new MockMultipartFile("file", new byte[0])));
    }

    @Test
    void otherAccountCannotReadRefreshOrOverwriteIcon() {
        User other = User.builder().id(2L).build();
        when(sites.findByIdAndUser(7L, other)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.get(other, 7L));
        assertThrows(ResponseStatusException.class, () -> service.refresh(other, 7L));
        assertThrows(ResponseStatusException.class, () -> service.upload(other, 7L,
                new MockMultipartFile("file", png)));
    }

    @Test
    void deletionRemovesPersistedIcon() {
        service.upload(user, 7L, new MockMultipartFile("file", png));
        service.delete(7L);
        assertFalse(java.nio.file.Files.exists(directory.resolve("crawler-site-icons/7.json")));
    }

    @Test
    void rejectsPrivateAndNonHttpTargetsBeforeConnecting() {
        for (String url : java.util.List.of("file:///tmp/icon.png", "http://127.0.0.1/a",
                "http://10.0.0.1/a", "http://169.254.169.254/a", "http://[::1]/a",
                "http://[fd00::1]/a", "http://100.64.0.1/a", "https://user:pass@example.com/a")) {
            assertThrows(IOException.class, () -> CrawlerSiteIconService.validateTarget(url), url);
        }
    }
}
