package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aibook.model.entity.CrawlerTask;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class CrawlerTaskPriorityConstraintInitializerTest {

    @Test
    void rebuildsConstraintWithEveryCrawlerTaskPriority() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        CrawlerTaskPriorityConstraintInitializer initializer =
                new CrawlerTaskPriorityConstraintInitializer(jdbcTemplate);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute(
                "ALTER TABLE crawler_tasks DROP CONSTRAINT IF EXISTS "
                        + "crawler_tasks_priority_check");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).execute(sql.capture());
        String createConstraint = sql.getAllValues().get(1);
        assertThat(createConstraint)
                .isEqualTo("ALTER TABLE crawler_tasks ADD CONSTRAINT crawler_tasks_priority_check "
                        + "CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'EXTREME'))");
        Arrays.stream(CrawlerTask.Priority.values())
                .forEach(status -> assertThat(createConstraint)
                        .contains("'" + status.name() + "'"));
    }
}
