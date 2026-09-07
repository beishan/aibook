package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aibook.model.entity.Book;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class BookSourceTypeConstraintInitializerTest {

    @Test
    void rebuildsConstraintWithEveryBookSourceType() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        BookSourceTypeConstraintInitializer initializer =
                new BookSourceTypeConstraintInitializer(jdbcTemplate);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute(
                "ALTER TABLE books DROP CONSTRAINT IF EXISTS books_source_type_check");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).execute(sql.capture());
        String createConstraint = sql.getAllValues().get(1);
        assertThat(createConstraint).contains("ADD CONSTRAINT books_source_type_check");
        Arrays.stream(Book.SourceType.values())
                .forEach(sourceType -> assertThat(createConstraint)
                        .contains("'" + sourceType.name() + "'"));
    }
}
