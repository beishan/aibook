package com.aibook.repository;

import com.aibook.model.entity.Author;
import com.aibook.model.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    interface AuthorBookCount {
        Long getAuthorId();

        long getBookCount();
    }

    interface AuthorRelationStatistics {
        long getActiveAuthorCount();

        long getRelatedBookCount();
    }

    Optional<Author> findByUserAndNormalizedName(User user, String normalizedName);

    long countByUser(User user);

    @Query(
            value = "SELECT a FROM Author a WHERE a.user = :user "
                    + "AND (:keyword = '' OR a.normalizedName LIKE CONCAT('%', LOWER(:keyword), '%'))",
            countQuery = "SELECT COUNT(a) FROM Author a WHERE a.user = :user "
                    + "AND (:keyword = '' OR a.normalizedName LIKE CONCAT('%', LOWER(:keyword), '%'))")
    Page<Author> findPage(
            @Param("user") User user,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Modifying
    @Query(value = "INSERT INTO authors (name, normalized_name, user_id, created_at, updated_at) "
            + "VALUES (:name, :normalizedName, :userId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (user_id, normalized_name) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(
            @Param("name") String name,
            @Param("normalizedName") String normalizedName,
            @Param("userId") Long userId);

    @Query("SELECT a.id AS authorId, COUNT(b) AS bookCount FROM Book b JOIN b.authors a "
            + "WHERE b.user = :user AND a.id IN :authorIds "
            + "AND b.deletedAt IS NULL AND b.purgedAt IS NULL GROUP BY a.id")
    List<AuthorBookCount> countActiveBooksByAuthorIds(
            @Param("user") User user,
            @Param("authorIds") List<Long> authorIds);

    @Query("SELECT COUNT(DISTINCT a.id) AS activeAuthorCount, COUNT(b) AS relatedBookCount "
            + "FROM Book b JOIN b.authors a WHERE b.user = :user "
            + "AND b.deletedAt IS NULL AND b.purgedAt IS NULL")
    AuthorRelationStatistics summarizeActiveBooks(@Param("user") User user);
}
