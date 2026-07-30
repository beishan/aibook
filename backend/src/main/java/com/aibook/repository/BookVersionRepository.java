package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookVersionRepository extends JpaRepository<BookVersion, Long> {
    List<BookVersion> findByBookOrderByPrimaryVersionDescCreatedAtAsc(Book book);

    Optional<BookVersion> findByBookAndPrimaryVersionTrue(Book book);

    Optional<BookVersion> findByIdAndBook(Long id, Book book);

    Optional<BookVersion> findByFileHash(String fileHash);
}
