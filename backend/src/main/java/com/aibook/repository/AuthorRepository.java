package com.aibook.repository;

import com.aibook.model.entity.Author;
import com.aibook.model.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByUserAndNormalizedName(User user, String normalizedName);

    List<Author> findByUserOrderByNameAsc(User user);

    @Modifying
    @Query(value = "INSERT INTO authors (name, normalized_name, user_id, created_at, updated_at) "
            + "VALUES (:name, :normalizedName, :userId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (user_id, normalized_name) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(
            @Param("name") String name,
            @Param("normalizedName") String normalizedName,
            @Param("userId") Long userId);

    @Query("SELECT COUNT(b) FROM Book b JOIN b.authors a "
            + "WHERE a.id = :authorId AND b.deletedAt IS NULL AND b.purgedAt IS NULL")
    long countActiveBooks(@Param("authorId") Long authorId);
}
