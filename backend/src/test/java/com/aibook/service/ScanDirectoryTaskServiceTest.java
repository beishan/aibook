package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.repository.ScanDirectoryRepository;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanDirectoryTaskServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesCompletedScanProgressAndUpdatesDirectory() throws Exception {
        User user = User.builder()
                .id(1L)
                .username("reader")
                .scanThreadCount(3)
                .build();
        ScanDirectory directory = ScanDirectory.builder()
                .id(2L)
                .path(tempDir.toString())
                .user(user)
                .build();
        ScanDirectoryRepository repository = mock(ScanDirectoryRepository.class);
        FileScannerService fileScannerService = mock(FileScannerService.class);
        when(repository.findByIdAndUser(2L, user))
                .thenReturn(Optional.of(directory));
        when(repository.findByIdAndUserId(2L, 1L))
                .thenReturn(Optional.of(directory));
        when(fileScannerService.scanDirectory(
                eq(tempDir.toString()),
                eq(1L),
                eq(3),
                isNull(),
                any(FileScannerService.ScanResult.class)))
                .thenAnswer(invocation -> {
                    FileScannerService.ScanResult result = invocation.getArgument(4);
                    result.setTotalCount(3);
                    result.addNew("/books/one.epub");
                    result.addSkipped("/books/two.epub");
                    result.addFailed("/books/three.epub", "损坏文件");
                    result.markScanned("/books/one.epub");
                    result.markScanned("/books/two.epub");
                    result.markScanned("/books/three.epub");
                    result.setEndTime(System.currentTimeMillis());
                    return result;
                });

        ScanDirectoryTaskService service =
                new ScanDirectoryTaskService(repository, fileScannerService);
        try {
            Map<String, Object> started = service.startScan(2L, user);
            assertThat(started.get("status")).isIn("PENDING", "RUNNING");

            Map<String, Object> progress = waitForCompletion(service, user);

            assertThat(progress.get("status")).isEqualTo("COMPLETED");
            assertThat(progress.get("progress")).isEqualTo(100);
            assertThat(progress.get("totalCount")).isEqualTo(3);
            assertThat(progress.get("scannedCount")).isEqualTo(3);
            assertThat(progress.get("newBooks")).isEqualTo(1);
            assertThat(progress.get("skippedBooks")).isEqualTo(1);
            assertThat(progress.get("failedBooks")).isEqualTo(1);
            assertThat(directory.getBookCount()).isEqualTo(2);
            verify(repository).save(directory);
        } finally {
            service.shutdown();
        }
    }

    private Map<String, Object> waitForCompletion(
            ScanDirectoryTaskService service,
            User user) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        Map<String, Object> progress;
        do {
            progress = service.getProgress(2L, user);
            if ("COMPLETED".equals(progress.get("status"))) {
                return progress;
            }
            Thread.sleep(10);
        } while (System.currentTimeMillis() < deadline);
        return progress;
    }
}
