package com.aibook.repository;

import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.LibraryChapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LibraryChapterRepository extends JpaRepository<LibraryChapter, Long> {

    List<LibraryChapter> findByBookVersionOrderByChapterIndexAsc(BookVersion bookVersion);

    long countByBookVersion(BookVersion bookVersion);

    Optional<LibraryChapter> findByIdAndBookVersion(Long id, BookVersion bookVersion);

    void deleteByBookVersion(BookVersion bookVersion);
}
