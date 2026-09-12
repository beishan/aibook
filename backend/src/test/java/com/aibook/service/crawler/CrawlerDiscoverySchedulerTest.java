package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerDiscoveryPage;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerDiscoveryPageRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class CrawlerDiscoverySchedulerTest {

    @Test
    void triggersOnlyDiscoveryPagesWhoseIntervalHasElapsed() {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).enabled(true).build();
        CrawlerDiscoveryPage due = CrawlerDiscoveryPage.builder().id(3L).site(site)
                .autoScanEnabled(true).scanIntervalMinutes(60).lastScanAt(LocalDateTime.now().minusMinutes(61)).build();
        CrawlerDiscoveryPage recent = CrawlerDiscoveryPage.builder().id(4L).site(site)
                .autoScanEnabled(true).scanIntervalMinutes(60).lastScanAt(LocalDateTime.now().minusMinutes(5)).build();
        CrawlerDiscoveryPageRepository pages = mock(CrawlerDiscoveryPageRepository.class);
        CrawlerTaskService tasks = mock(CrawlerTaskService.class);
        when(pages.findByAutoScanEnabledTrueAndSiteEnabledTrue()).thenReturn(List.of(due, recent));

        new CrawlerDiscoveryScheduler(pages, tasks).scheduleDuePages();

        verify(tasks).scanDiscoveryPage(user, due, true);
        verify(tasks, never()).scanDiscoveryPage(user, recent, true);
    }
}
