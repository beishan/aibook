package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aibook.model.entity.CrawlerChapter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class CrawlerChapterStatusConstraintInitializerTest {

    @Test
    void rebuildsConstraintWithEveryCrawlerChapterStatus() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        CrawlerChapterStatusConstraintInitializer initializer =
                new CrawlerChapterStatusConstraintInitializer(jdbcTemplate);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute(
                "ALTER TABLE crawler_chapters DROP CONSTRAINT IF EXISTS "
                        + "crawler_chapters_crawl_status_check");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).execute(sql.capture());
        String createConstraint = sql.getAllValues().get(1);
        assertThat(createConstraint)
                .contains("ADD CONSTRAINT crawler_chapters_crawl_status_check");
        Arrays.stream(CrawlerChapter.CrawlStatus.values())
                .forEach(status -> assertThat(createConstraint)
                        .contains("'" + status.name() + "'"));
    }
}
