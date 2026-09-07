package com.aibook.config;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CrawlerManualModeInitializerTest {

    @Test
    void disablesEveryLegacyCrawlerAutomationFlag() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(contains("UPDATE crawler_sites"))).thenReturn(2);
        when(jdbcTemplate.update(contains("UPDATE crawler_books"))).thenReturn(5);

        new CrawlerManualModeInitializer(jdbcTemplate).run(null);

        verify(jdbcTemplate).update(contains("auto_scan = FALSE"));
        verify(jdbcTemplate).update(contains("auto_update_enabled = FALSE"));
    }
}
