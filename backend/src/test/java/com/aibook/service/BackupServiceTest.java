package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.BackupExecution;
import com.aibook.repository.BackupExecutionRepository;
import com.aibook.repository.BackupTaskRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class BackupServiceTest {

    @TempDir
    Path root;

    private BackupExecutionRepository repository;
    private SystemConfigService config;
    private BackupService service;

    @BeforeEach
    void setUp() {
        repository = mock(BackupExecutionRepository.class);
        config = mock(SystemConfigService.class);
        service = new BackupService(mock(BackupTaskRepository.class), repository, config, Runnable::run);
        ReflectionTestUtils.setField(service, "backupPath", root.toString());
        ReflectionTestUtils.setField(service, "backupHostPath", root.toString());
    }

    private BackupExecution execution(LocalDateTime time) {
        BackupExecution execution = new BackupExecution();
        execution.setId(1L);
        execution.setStatus(BackupExecution.Status.SUCCESS);
        execution.setTaskName("备份测试");
        execution.setOutputPath(root.resolve("aibook-"
                + time.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-1").toString());
        when(repository.findTop100ByOrderByStartedAtDesc()).thenReturn(List.of(execution));
        return execution;
    }

    @Test
    void executionPaginationReturnsTotalsStableOrderAndGlobalActivity() {
        BackupExecution row = new BackupExecution();
        row.setId(123L);
        row.setStatus(BackupExecution.Status.RUNNING);
        row.setTaskName("分页记录");
        when(repository.findAll(any(org.springframework.data.domain.Pageable.class))).thenAnswer(call -> {
            org.springframework.data.domain.Pageable request = call.getArgument(0);
            assertThat(request.getPageNumber()).isEqualTo(1);
            assertThat(request.getPageSize()).isEqualTo(20);
            assertThat(request.getSort().getOrderFor("startedAt").isDescending()).isTrue();
            assertThat(request.getSort().getOrderFor("id").isDescending()).isTrue();
            return new org.springframework.data.domain.PageImpl<>(List.of(row), request, 123);
        });
        when(repository.existsByStatusIn(any())).thenReturn(true);
        var result = service.executions(1, 20);
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(123);
        assertThat(result.totalPages()).isEqualTo(7);
        assertThat(result.active()).isTrue();
    }

    @Test
    void executionPaginationClampsSizeAndRecoversOutOfRangePage() {
        when(repository.findAll(any(org.springframework.data.domain.Pageable.class))).thenAnswer(call -> {
            org.springframework.data.domain.Pageable request = call.getArgument(0);
            assertThat(request.getPageSize()).isEqualTo(100);
            return new org.springframework.data.domain.PageImpl<>(List.of(), request, 123);
        });
        var result = service.executions(99, 10000);
        assertThat(result.number()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(123);
        assertThat(result.size()).isEqualTo(100);
    }

    @Test
    void executionPaginationNormalizesEmptyAndNegativeRequests() {
        when(repository.findAll(any(org.springframework.data.domain.Pageable.class))).thenAnswer(call -> {
            org.springframework.data.domain.Pageable request = call.getArgument(0);
            assertThat(request.getPageNumber()).isZero();
            assertThat(request.getPageSize()).isEqualTo(1);
            return new org.springframework.data.domain.PageImpl<>(List.of(), request, 0);
        });
        var result = service.executions(-1, 0);
        assertThat(result.number()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.active()).isFalse();
    }

    @Test
    void retentionCleanupRecordsReasonAndKeepsOriginalResult() throws Exception {
        BackupExecution execution = execution(LocalDateTime.now().minusDays(40));
        execution.setDetails("备份成功");
        Path directory = Files.createDirectory(Path.of(execution.getOutputPath()));
        Files.writeString(directory.resolve("manifest.json"), "{}");
        when(config.getBooleanConfig("backup.retention.enabled", false)).thenReturn(true);
        when(config.getIntConfig("backup.retention.recent-days", 7)).thenReturn(7);
        when(config.getIntConfig("backup.retention.monthly-months", 12)).thenReturn(0);
        when(repository.findAllById(any())).thenReturn(List.of(execution));

        service.cleanExpiredBackups();

        assertThat(directory).doesNotExist();
        assertThat(execution.getDeletedAt()).isNotNull();
        assertThat(execution.getDeletionReason()).contains("自动保留策略", "7", "0");
        assertThat(execution.getStatus()).isEqualTo(BackupExecution.Status.SUCCESS);
        assertThat(execution.getDetails()).startsWith("备份成功");
        assertThat(execution.getOutputPath()).isEqualTo(directory.toString());
        assertThat(service.executions().getFirst().status()).isEqualTo("DELETED");
        assertThat(service.executions().getFirst().executionStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void legacyCleanupIsMarkedWithoutInventingDeletionTime() {
        BackupExecution execution = execution(LocalDateTime.now());
        execution.setOutputPath(null);
        execution.setDetails("备份成功\n备份文件已按保留策略清理");

        var view = service.executions().getFirst();

        assertThat(view.status()).isEqualTo("DELETED");
        assertThat(view.deletionReason()).contains("保留策略", "历史记录");
        assertThat(view.deletedAt()).isNull();
        verify(repository).save(execution);
    }

    @Test
    void missingDirectoryHasUnknownReason() {
        execution(LocalDateTime.now());

        var view = service.executions().getFirst();

        assertThat(view.status()).isEqualTo("DELETED");
        assertThat(view.deletionReason()).contains("原因未知");
    }

    @Test
    void existingAndRunningBackupsAreNotMarkedDeleted() throws Exception {
        BackupExecution execution = execution(LocalDateTime.now());
        Files.createDirectory(Path.of(execution.getOutputPath()));
        assertThat(service.executions().getFirst().status()).isEqualTo("SUCCESS");
        execution.setStatus(BackupExecution.Status.RUNNING);
        execution.setOutputPath(root.resolve("aibook-20260101-000000-1").toString());
        assertThat(service.executions().getFirst().status()).isEqualTo("RUNNING");
        verify(repository, never()).save(any());
    }

    @Test
    void unavailableRootAndUnrelatedPathsAreNotMarkedDeleted() {
        BackupExecution execution = execution(LocalDateTime.now());
        ReflectionTestUtils.setField(service, "backupPath", root.resolve("unavailable").toString());
        assertThat(service.executions().getFirst().status()).isEqualTo("SUCCESS");
        ReflectionTestUtils.setField(service, "backupPath", root.toString());
        execution.setOutputPath(root.resolve("aibook-20260101-000000-2").toString());
        assertThat(service.executions().getFirst().status()).isEqualTo("SUCCESS");
        verify(repository, never()).save(any());
    }
    private BackupExecution deletableExecution() {
        BackupExecution row = execution(LocalDateTime.of(2026, 10, 21, 12, 0));
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(row));
        return row;
    }

    @Test
    void manualDeleteRemovesNestedFilesAndRecordButDoesNotFollowLinks() throws Exception {
        BackupExecution row = deletableExecution();
        Path directory = Files.createDirectory(Path.of(row.getOutputPath()));
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Files.writeString(nested.resolve("database.dump"), "backup");
        Path outside = Files.createTempDirectory(root.getParent(), "backup-outside-");
        try {
            Path protectedFile = Files.writeString(outside.resolve("original.txt"), "original");
            Files.createSymbolicLink(directory.resolve("link"), outside);
            service.deleteExecution(1L);
            assertThat(directory).doesNotExist();
            assertThat(protectedFile).hasContent("original");
            verify(repository).delete(row);
        } finally {
            Files.deleteIfExists(outside.resolve("original.txt"));
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void manualDeleteRejectsActiveBackupsBeforeTouchingFiles() throws Exception {
        BackupExecution row = deletableExecution();
        Path directory = Files.createDirectory(Path.of(row.getOutputPath()));
        for (var status : List.of(BackupExecution.Status.QUEUED, BackupExecution.Status.RUNNING)) {
            row.setStatus(status);
            assertThatThrownBy(() -> service.deleteExecution(1L))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("409");
        }
        assertThat(directory).isDirectory();
        verify(repository, never()).delete(any(BackupExecution.class));
    }

    @Test
    void manualDeleteValidatesPathAndExecutionIdAndRejectsDirectorySymlink() throws Exception {
        BackupExecution row = deletableExecution();
        String validPath = row.getOutputPath();
        for (String invalidPath : List.of("/", root.toString(), root.resolve("other").toString(),
                root.resolve("aibook-20261021-120000-2").toString(),
                root.getParent().resolve("aibook-20261021-120000-1").toString())) {
            row.setOutputPath(invalidPath);
            assertThatThrownBy(() -> service.deleteExecution(1L))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("409");
        }
        row.setOutputPath(validPath);
        Path target = Files.createDirectory(root.resolve("originals"));
        Files.createSymbolicLink(Path.of(validPath), target);
        assertThatThrownBy(() -> service.deleteExecution(1L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThat(target).isDirectory();
        verify(repository, never()).delete(any(BackupExecution.class));
    }

    @Test
    void manualDeleteSupportsMissingFilesOrNoOutputAndRejectsUnavailableRoot() {
        BackupExecution row = deletableExecution();
        ReflectionTestUtils.setField(service, "backupPath", root.resolve("unmounted").toString());
        assertThatThrownBy(() -> service.deleteExecution(1L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("500");
        verify(repository, never()).delete(any(BackupExecution.class));
        ReflectionTestUtils.setField(service, "backupPath", root.toString());
        service.deleteExecution(1L);
        verify(repository).delete(row);
        row.setOutputPath(null);
        service.deleteExecution(1L);
        verify(repository, org.mockito.Mockito.times(2)).delete(row);
    }

    @Test
    void manualDeleteMapsHostPathToMountedBackupDirectory() throws Exception {
        BackupExecution row = deletableExecution();
        Path directory = Files.createDirectory(Path.of(row.getOutputPath()));
        Files.writeString(directory.resolve("data"), "backup");
        ReflectionTestUtils.setField(service, "backupHostPath", "/NAS/backups");
        row.setOutputPath("/NAS/backups/" + directory.getFileName());
        service.deleteExecution(1L);
        assertThat(directory).doesNotExist();
        verify(repository).delete(row);
    }

    @Test
    void manualDeleteMissingRecordReturnsNotFound() {
        assertThatThrownBy(() -> service.deleteExecution(404L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(repository, never()).delete(any(BackupExecution.class));
    }

}
