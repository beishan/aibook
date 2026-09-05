package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.repository.CrawlerSiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlerScheduler {
    private final CrawlerSiteRepository siteRepository;
    private final CrawlerTaskService taskService;

    @Scheduled(fixedDelayString = "${crawler.scheduler-poll-ms:60000}")
    public void schedule() {
        LocalDateTime now = LocalDateTime.now();
        for (CrawlerSite site : siteRepository.findByEnabledTrue()) {
            try {
                boolean changed = false;
                if (Boolean.TRUE.equals(site.getAutoScan()) && due(site.getLastScanAt(), site.getScanIntervalMinutes(), now)) {
                    if (taskService.scheduleSiteScan(site)) { site.setLastScanAt(now); changed = true; }
                }
                if (Boolean.TRUE.equals(site.getAutoUpdate()) && due(site.getLastUpdateAt(), site.getUpdateIntervalMinutes(), now)) {
                    taskService.scheduleBookUpdates(site); site.setLastUpdateAt(now); changed = true;
                }
                if (changed) siteRepository.save(site);
            } catch (Exception exception) {
                log.warn("网站 {} 的自动采集调度失败", site.getSiteCode(), exception);
            }
        }
    }

    private boolean due(LocalDateTime lastRun, Integer minutes, LocalDateTime now) {
        return lastRun == null || !lastRun.plusMinutes(minutes == null ? 30 : Math.max(1, minutes)).isAfter(now);
    }
}
