package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.repository.BookScanSourceRepository;
import com.aibook.repository.ScanDirectoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScanDirectoryServiceTest {

    @Test
    void updatesLibraryVisibilityIndependentlyAndIdempotently() {
        User user = User.builder().id(1L).username("reader").build();
        ScanDirectory directory = ScanDirectory.builder()
                .id(2L)
                .path("/books/fiction")
                .user(user)
                .enabled(true)
                .libraryVisible(true)
                .build();
        ScanDirectoryRepository directoryRepository = mock(ScanDirectoryRepository.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        when(directoryRepository.findByIdAndUser(2L, user)).thenReturn(Optional.of(directory));
        when(directoryRepository.save(directory)).thenReturn(directory);

        ScanDirectoryService service = new ScanDirectoryService(
                directoryRepository,
                mock(FileScannerService.class),
                mock(CategoryService.class),
                mock(BookScanSourceRepository.class),
                operationLogService);

        ScanDirectory hidden = service.updateLibraryVisibility(2L, false, user);
        assertThat(hidden.getLibraryVisible()).isFalse();
        assertThat(hidden.getEnabled()).isTrue();
        verify(operationLogService).record(
                user,
                OperationLog.Action.UPDATE_SCAN_DIRECTORY_VISIBILITY,
                null,
                "书库隐藏扫描目录",
                "/books/fiction");

        service.updateLibraryVisibility(2L, false, user);
        verify(operationLogService).record(
                user,
                OperationLog.Action.UPDATE_SCAN_DIRECTORY_VISIBILITY,
                null,
                "书库隐藏扫描目录",
                "/books/fiction");
        verify(directoryRepository, never()).delete(directory);
    }
}
