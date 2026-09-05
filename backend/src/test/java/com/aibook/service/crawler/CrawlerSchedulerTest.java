package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.repository.CrawlerSiteRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class CrawlerSchedulerTest {
    @Test
    void schedulesDueDiscoveryAndUpdatesAndPersistsRunTimes() {
        CrawlerSiteRepository sites = mock(CrawlerSiteRepository.class);
        CrawlerTaskService tasks = mock(CrawlerTaskService.class);
        CrawlerSite site = CrawlerSite.builder().enabled(true).autoScan(true).autoUpdate(true)
                .scanIntervalMinutes(60).updateIntervalMinutes(30).build();
        when(sites.findByEnabledTrue()).thenReturn(List.of(site));
        when(tasks.scheduleSiteScan(site)).thenReturn(true);

        new CrawlerScheduler(sites, tasks).schedule();

        verify(tasks).scheduleSiteScan(site);
        verify(tasks).scheduleBookUpdates(site);
        verify(sites).save(site);
        assertNotNull(site.getLastScanAt());
        assertNotNull(site.getLastUpdateAt());
    }
}
