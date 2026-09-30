package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.RewriteProject;
import com.aibook.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RewriteProjectRepository extends JpaRepository<RewriteProject, Long> {
    Page<RewriteProject> findByUserAndBookDeletedAtIsNull(User user, Pageable pageable);

    Page<RewriteProject> findByUserAndBookDeletedAtIsNullAndStatusNot(
            User user, RewriteProject.Status status, Pageable pageable);

    Page<RewriteProject> findByUserAndBookDeletedAtIsNullAndStatus(
            User user, RewriteProject.Status status, Pageable pageable);

    @Query("SELECT project FROM RewriteProject project "
            + "WHERE project.user = :user AND project.book.deletedAt IS NULL "
            + "AND ((:status IS NULL AND project.status <> :archived) "
            + "OR project.status = :status) "
            + "AND (:keyword = '' OR LOWER(project.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(project.book.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(project.book.author) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<RewriteProject> search(@Param("user") User user,
            @Param("status") RewriteProject.Status status,
            @Param("archived") RewriteProject.Status archived,
            @Param("keyword") String keyword, Pageable pageable);

    Optional<RewriteProject> findByIdAndUser(Long id, User user);

    Optional<RewriteProject> findByCreationTokenAndUser(String token, User user);

    Optional<RewriteProject> findByRewriteVersion(BookVersion version);

    boolean existsBySourceVersionOrRewriteVersion(BookVersion source, BookVersion rewrite);

    boolean existsByBook(Book book);
}
