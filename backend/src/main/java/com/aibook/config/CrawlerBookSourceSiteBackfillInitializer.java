package com.aibook.config;

import com.aibook.repository.CrawlerBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 为升级前已入库的采集书籍补齐来源网站名称。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlerBookSourceSiteBackfillInitializer {

    private final CrawlerBookRepository crawlerBookRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        int updated = crawlerBookRepository.backfillLibrarySourceSiteNames();
        if (updated > 0) {
            log.info("历史采集入库书籍来源网站名称回填完成，更新 {} 本", updated);
        }
    }
}
