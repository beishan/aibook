package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.SystemConfig;
import com.aibook.repository.SystemConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class CrawlerBookAutoUpdateMigrationInitializerTest {

    @Test
    void disablesAutomaticUpdatesForLegacyCompletedBooksOnce() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        when(jdbcTemplate.update(contains("crawl_status = 'COMPLETED'"))).thenReturn(12);

        new CrawlerBookAutoUpdateMigrationInitializer(jdbcTemplate, configRepository).run(null);

        verify(jdbcTemplate).update(contains("auto_update_enabled = FALSE"));
        ArgumentCaptor<SystemConfig> marker = ArgumentCaptor.forClass(SystemConfig.class);
        verify(configRepository).save(marker.capture());
        assertThat(marker.getValue().getConfigKey())
                .isEqualTo(CrawlerBookAutoUpdateMigrationInitializer.MIGRATION_KEY);
        assertThat(marker.getValue().getConfigValue()).isEqualTo("complete");
    }

    @Test
    void skipsMigrationAfterCompletionMarkerExists() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        when(configRepository.findById(CrawlerBookAutoUpdateMigrationInitializer.MIGRATION_KEY))
                .thenReturn(Optional.of(SystemConfig.builder().configValue("complete").build()));

        new CrawlerBookAutoUpdateMigrationInitializer(jdbcTemplate, configRepository).run(null);

        verify(jdbcTemplate, never()).update(any(String.class));
        verify(configRepository, never()).save(any(SystemConfig.class));
    }
}
