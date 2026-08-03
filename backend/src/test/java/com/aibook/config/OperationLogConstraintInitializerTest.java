package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.aibook.model.entity.OperationLog;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationLogConstraintInitializerTest {

    @Test
    void rebuildsConstraintWithEveryAction() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        OperationLogConstraintInitializer initializer =
                new OperationLogConstraintInitializer(jdbcTemplate);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute(
                "ALTER TABLE operation_logs DROP CONSTRAINT IF EXISTS operation_logs_action_check");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).execute(sql.capture());
        String createConstraint = sql.getAllValues().get(1);
        assertThat(createConstraint).contains("ADD CONSTRAINT operation_logs_action_check");
        Arrays.stream(OperationLog.Action.values())
                .forEach(action -> assertThat(createConstraint).contains("'" + action.name() + "'"));
    }
}
