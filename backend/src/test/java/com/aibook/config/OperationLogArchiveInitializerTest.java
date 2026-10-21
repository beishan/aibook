package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationLogArchiveInitializerTest {
    @Test
    void permitsDetachmentAndIdempotentlyBackfillsIdentityWithoutDeletingLogs() {
        var jdbc = mock(JdbcTemplate.class);
        new OperationLogArchiveInitializer(jdbc).run(new DefaultApplicationArguments());
        verify(jdbc).execute("ALTER TABLE operation_logs ALTER COLUMN user_id DROP NOT NULL");
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture());
        assertThat(sql.getValue()).contains("COALESCE(l.owner_user_id, l.user_id)",
                "COALESCE(l.owner_username, u.username)", "l.user_id = u.id",
                "l.owner_user_id IS NULL OR l.owner_username IS NULL")
                .doesNotContain("DELETE", "retained");
    }
}
