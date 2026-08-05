package com.aibook.service.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MetadataCacheServiceTest {

    @Test
    void savesReadsAndEvictsMetadata() {
        MetadataCacheService service = new MetadataCacheService();
        MetadataScraper.BookMetadata metadata = MetadataScraper.BookMetadata.builder()
                .title("测试书籍")
                .author("测试作者")
                .build();
        String key = service.isbnKey("9780000000000");

        service.saveToCache(key, metadata);

        assertSame(metadata, service.getFromCache(key));
        service.evictCache(key);
        assertNull(service.getFromCache(key));
    }

    @Test
    void expiresEntriesUsingConfiguredCachePolicy() {
        AtomicLong ticker = new AtomicLong();
        Cache<String, MetadataScraper.BookMetadata> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .ticker(ticker::get)
                .build();
        MetadataCacheService service = new MetadataCacheService(cache);
        MetadataScraper.BookMetadata metadata = MetadataScraper.BookMetadata.builder()
                .title("过期测试")
                .build();

        service.saveToCache(service.titleKey("过期测试"), metadata);
        ticker.addAndGet(Duration.ofHours(1).plusNanos(1).toNanos());

        assertNull(service.getFromCache(service.titleKey("过期测试")));
    }

    @Test
    void normalizesTitleCacheKeys() {
        MetadataCacheService service = new MetadataCacheService();

        assertEquals("title:三体", service.titleKey("  三体  "));
    }
}
