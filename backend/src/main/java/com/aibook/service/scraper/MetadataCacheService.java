package com.aibook.service.scraper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 元数据缓存服务
 * 使用进程内缓存保存刮削结果，避免重复请求外部 API
 */
@Service
@Slf4j
public class MetadataCacheService {

    private static final String CACHE_PREFIX = "metadata:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final long CACHE_MAXIMUM_SIZE = 10_000;

    private final Cache<String, MetadataScraper.BookMetadata> cache;

    public MetadataCacheService() {
        this(Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(CACHE_TTL)
                .build());
    }

    MetadataCacheService(Cache<String, MetadataScraper.BookMetadata> cache) {
        this.cache = cache;
    }

    /**
     * 获取缓存的元数据
     */
    public MetadataScraper.BookMetadata getFromCache(String key) {
        MetadataScraper.BookMetadata cached = cache.getIfPresent(CACHE_PREFIX + key);
        if (cached != null) {
            log.debug("缓存命中: {}", key);
        }
        return cached;
    }

    /**
     * 保存元数据到缓存
     */
    public void saveToCache(String key, MetadataScraper.BookMetadata metadata) {
        cache.put(CACHE_PREFIX + key, metadata);
        log.debug("缓存保存: {}", key);
    }

    /**
     * 删除缓存
     */
    public void evictCache(String key) {
        cache.invalidate(CACHE_PREFIX + key);
        log.debug("缓存删除: {}", key);
    }

    /**
     * 生成 ISBN 缓存键
     */
    public String isbnKey(String isbn) {
        return "isbn:" + isbn;
    }

    /**
     * 生成书名缓存键
     */
    public String titleKey(String title) {
        return "title:" + title.toLowerCase().trim();
    }

    /**
     * 生成豆瓣 ID 缓存键
     */
    public String doubanKey(String doubanId) {
        return "douban:" + doubanId;
    }
}
