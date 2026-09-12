package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.DiscoveryPagePayload;
import com.aibook.model.entity.CrawlerDiscoveryPage;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerDiscoveryPageRepository;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrawlerDiscoveryPageServiceTest {

    @Test
    void createsIndependentPaginatedDiscoveryPage() {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("春晓阁")
                .baseUrl("https://www.chunxiaoge.com").build();
        CrawlerDiscoveryPageRepository pages = mock(CrawlerDiscoveryPageRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        CrawlerHttpClient http = mock(CrawlerHttpClient.class);
        when(management.ownedSite(user, 2L)).thenReturn(site);
        when(http.validateSiteUrl(site, "https://www.chunxiaoge.com/rank/hot/"))
                .thenReturn(URI.create("https://www.chunxiaoge.com/rank/hot/"));
        when(pages.save(any(CrawlerDiscoveryPage.class))).thenAnswer(invocation -> {
            CrawlerDiscoveryPage value = invocation.getArgument(0);
            value.setId(3L);
            return value;
        });
        CrawlerDiscoveryPageService service = new CrawlerDiscoveryPageService(pages, management, http);

        var result = service.create(user, 2L, new DiscoveryPagePayload("热门小说",
                "https://www.chunxiaoge.com/rank/hot/", true, 120, 50));

        assertThat(result.pageName()).isEqualTo("热门小说");
        assertThat(result.pageUrl()).isEqualTo("https://www.chunxiaoge.com/rank/hot/");
        assertThat(result.autoScanEnabled()).isTrue();
        assertThat(result.scanIntervalMinutes()).isEqualTo(120);
        assertThat(result.maxPages()).isEqualTo(50);
    }

    @Test
    void onlyReturnsPageOwnedByCurrentUser() {
        CrawlerDiscoveryPageRepository pages = mock(CrawlerDiscoveryPageRepository.class);
        User user = User.builder().id(1L).username("owner").build();
        CrawlerDiscoveryPage page = CrawlerDiscoveryPage.builder().id(9L).build();
        when(pages.findByIdAndSiteUser(9L, user)).thenReturn(Optional.of(page));
        CrawlerDiscoveryPageService service = new CrawlerDiscoveryPageService(
                pages, mock(CrawlerManagementService.class), mock(CrawlerHttpClient.class));

        assertThat(service.owned(user, 9L)).isSameAs(page);
        verify(pages).findByIdAndSiteUser(9L, user);
    }
}
