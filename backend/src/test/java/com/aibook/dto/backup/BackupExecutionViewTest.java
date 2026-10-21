package com.aibook.dto.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibook.model.entity.BackupExecution;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BackupExecutionViewTest {

    @Test
    void calculatesElapsedTimeAcrossDaysForSuccessfulAndFailedExecutions() {
        for (BackupExecution.Status status : new BackupExecution.Status[] {
                BackupExecution.Status.SUCCESS, BackupExecution.Status.FAILED}) {
            BackupExecution execution = new BackupExecution();
            execution.setStatus(status);
            execution.setStartedAt(LocalDateTime.of(2026, 10, 20, 23, 59, 59));
            execution.setFinishedAt(LocalDateTime.of(2026, 10, 21, 1, 1, 1, 250_000_000));
            assertThat(BackupExecutionView.from(execution).durationMillis()).isEqualTo(3_662_250L);
            execution.setDeletionReason("保留策略清理");
            assertThat(BackupExecutionView.from(execution).status()).isEqualTo("DELETED");
            assertThat(BackupExecutionView.from(execution).durationMillis()).isEqualTo(3_662_250L);
        }
    }

    @Test
    void missingOrReversedTimestampsHaveNoDurationAndZeroIsValid() {
        BackupExecution execution = new BackupExecution();
        execution.setStatus(BackupExecution.Status.SUCCESS);
        assertThat(BackupExecutionView.from(execution).durationMillis()).isNull();
        LocalDateTime start = LocalDateTime.of(2026, 10, 21, 0, 0);
        execution.setStartedAt(start);
        assertThat(BackupExecutionView.from(execution).durationMillis()).isNull();
        execution.setFinishedAt(start.minusSeconds(1));
        assertThat(BackupExecutionView.from(execution).durationMillis()).isNull();
        execution.setFinishedAt(start);
        assertThat(BackupExecutionView.from(execution).durationMillis()).isZero();
    }
}
