package com.aibook.repository;

import com.aibook.model.entity.User;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 用户 Repository
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查找用户
     */
    Optional<User> findByEmail(String email);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    long countByRole(User.Role role);

    long countByRoleAndEnabledTrue(User.Role role);

    Optional<User> findFirstByOrderByCreatedAtAscIdAsc();

    @Query("SELECT u FROM User u JOIN u.preferences p WHERE p.trashRetentionDays > :retentionDays")
    List<User> findByTrashRetentionDaysGreaterThan(
            @Param("retentionDays") Integer retentionDays);

    @Query("""
            SELECT u FROM User u
            WHERE :keyword = ''
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(u.nickname, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<User> search(@Param("keyword") String keyword, Pageable pageable);
}
