package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.ReadingProgressRepository;
import com.aibook.repository.VersionReadingProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookVersionServiceTest {

    @Test
    void shouldUseOriginalUploadFilenameForPrimaryVersion() {
        BookVersionRepository versionRepository = mock(BookVersionRepository.class);
        ReadingProgressRepository progressRepository = mock(ReadingProgressRepository.class);
        when(versionRepository.findByBookAndPrimaryVersionTrue(any(Book.class)))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any(BookVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(progressRepository.findAllByBook(any(Book.class))).thenReturn(List.of());

        BookVersionService service = new BookVersionService(
                versionRepository,
                mock(BookRepository.class),
                progressRepository,
                mock(VersionReadingProgressRepository.class),
                new TxtParserService(),
                new ObjectMapper());
        Book book = Book.builder()
                .title("解析后的书名")
                .format("txt")
                .filePath("uploads/cfe3012c-dff6-4b6e-b3bc-4af90d3fcae3.txt")
                .build();

        BookVersion version = service.ensurePrimaryVersion(
                book, "原始上传书名.txt");

        assertEquals("原始上传书名.txt", version.getDisplayName());
    }
}
