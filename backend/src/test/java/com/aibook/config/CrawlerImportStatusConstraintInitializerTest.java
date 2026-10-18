package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.CrawlerBook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class CrawlerImportStatusConstraintInitializerTest {
    @Test
    void upgradesExistingConstraintToAllowFailedAndAllPreviousStatuses() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new CrawlerImportStatusConstraintInitializer(jdbc)
                .run(new DefaultApplicationArguments(new String[0]));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).execute(sql.capture());
        assertThat(sql.getAllValues().getFirst()).isEqualTo(
                "ALTER TABLE crawler_books DROP CONSTRAINT IF EXISTS crawler_books_import_status_check");
        String constraint = sql.getAllValues().getLast();
        assertThat(constraint).contains("CHECK (import_status IN (", "'FAILED'");
        for (var status : CrawlerBook.ImportStatus.values()) {
            assertThat(constraint).contains("'" + status.name() + "'");
        }
    }
}
