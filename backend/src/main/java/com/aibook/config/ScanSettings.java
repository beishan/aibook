package com.aibook.config;

/**
 * 目录扫描并发参数。
 */
public final class ScanSettings {

    public static final int MIN_THREAD_COUNT = 1;
    public static final int MAX_THREAD_COUNT = 16;
    public static final int DEFAULT_THREAD_COUNT = 2;

    private ScanSettings() {
    }

    public static int normalizeThreadCount(Integer value) {
        if (value == null) {
            return DEFAULT_THREAD_COUNT;
        }
        return Math.max(MIN_THREAD_COUNT, Math.min(MAX_THREAD_COUNT, value));
    }

    public static boolean isValidThreadCount(Integer value) {
        return value != null
                && value >= MIN_THREAD_COUNT
                && value <= MAX_THREAD_COUNT;
    }
}
