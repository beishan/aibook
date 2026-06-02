package com.aibook.service.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 元数据缓存服务
 * 使用 Redis 缓存刮削结果，避免重复请求外部 API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "metadata:";
    private static final long CACHE_TTL_HOURS = 24 * 7; // 缓存 7 天

    /**
     * 获取缓存的元数据
     */
    public MetadataScraper.BookMetadata getFromCache(String key) {
        try {
            String cacheKey = CACHE_PREFIX + key;
            Object cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached instanceof MetadataScraper.BookMetadata) {
                log.debug("缓存命中: {}", key);
                return (MetadataScraper.BookMetadata) cached;
            }

            return null;
        } catch (Exception e) {
            log.warn("读取缓存失败: {}", key, e);
            return null;
        }
    }

    /**
     * 保存元数据到缓存
     */
    public void saveToCache(String key, MetadataScraper.BookMetadata metadata) {
        try {
            String cacheKey = CACHE_PREFIX + key;
            redisTemplate.opsForValue().set(cacheKey, metadata, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.debug("缓存保存: {}", key);
        } catch (Exception e) {
            log.warn("保存缓存失败: {}", key, e);
        }
    }

    /**
     * 删除缓存
     */
    public void evictCache(String key) {
        try {
            String cacheKey = CACHE_PREFIX + key;
            redisTemplate.delete(cacheKey);
            log.debug("缓存删除: {}", key);
        } catch (Exception e) {
            log.warn("删除缓存失败: {}", key, e);
        }
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
