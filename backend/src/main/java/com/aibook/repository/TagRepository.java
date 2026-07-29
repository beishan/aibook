package com.aibook.repository;

import com.aibook.model.entity.Tag;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 标签 Repository
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * 根据用户查询标签
     */
    List<Tag> findByUserOrderByNameAsc(User user);

    /**
     * 根据用户和名称查询标签
     */
    Tag findByNameIgnoreCaseAndUser(String name, User user);

    /**
     * 删除标签前解除其与全部书籍的关联。
     */
    @Modifying
    @Query(value = "DELETE FROM book_tags WHERE tag_id = :tagId", nativeQuery = true)
    void deleteBookAssociations(@Param("tagId") Long tagId);
}
