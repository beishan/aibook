package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerDiscoveryPage;
import com.aibook.repository.CrawlerDiscoveryPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerDiscoveryScheduler {
    private final CrawlerDiscoveryPageRepository repository;
    private final CrawlerTaskService taskService;

    @Scheduled(fixedDelayString = "${crawler.discovery-scheduler-delay-ms:60000}", initialDelay = 30000)
    public void scheduleDuePages() {
        LocalDateTime now = LocalDateTime.now();
        for (CrawlerDiscoveryPage page : repository.findByAutoScanEnabledTrueAndSiteEnabledTrue()) {
            int interval = page.getScanIntervalMinutes() == null ? 360 : page.getScanIntervalMinutes();
            if (page.getLastScanAt() != null && page.getLastScanAt().plusMinutes(interval).isAfter(now)) continue;
            try {
                taskService.scanDiscoveryPage(page.getSite().getUser(), page, true);
            } catch (Exception exception) {
                log.debug("自动发现页扫描暂未创建: pageId={}, reason={}", page.getId(), exception.getMessage());
            }
        }
    }
}
