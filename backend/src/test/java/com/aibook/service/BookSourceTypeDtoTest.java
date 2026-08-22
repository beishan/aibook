package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aibook.dto.BookDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookHighlightRepository;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookmarkRepository;
import com.aibook.repository.ReadingProgressRepository;
import org.junit.jupiter.api.Test;

class BookSourceTypeDtoTest {

    @Test
    void dtoExposesPathOnlyForDirectoryScannedBooks() {
        BookService service = new BookService(
                mock(BookRepository.class),
                mock(ReadingProgressRepository.class),
                mock(BookmarkRepository.class),
                mock(BookHighlightRepository.class),
                mock(BookListRepository.class),
                mock(CategoryService.class),
                mock(TagService.class),
                mock(OperationLogService.class));
        User user = User.builder().id(1L).build();
        Book scanned = Book.builder()
                .id(1L).title("扫描书籍").format("epub").filePath("/books/扫描书籍.epub")
                .user(user).sourceType(Book.SourceType.DIRECTORY_SCAN).build();
        Book uploaded = Book.builder()
                .id(2L).title("上传书籍").format("epub").filePath("/uploads/upload.epub")
                .user(user).sourceType(Book.SourceType.UPLOAD).build();

        BookDTO scannedDto = service.convertToDTO(scanned);
        BookDTO uploadedDto = service.convertToDTO(uploaded);

        assertThat(scannedDto.getSourceType()).isEqualTo("DIRECTORY_SCAN");
        assertThat(scannedDto.getSourcePath()).isEqualTo("/books/扫描书籍.epub");
        assertThat(uploadedDto.getSourceType()).isEqualTo("UPLOAD");
        assertThat(uploadedDto.getSourcePath()).isNull();
    }
}
