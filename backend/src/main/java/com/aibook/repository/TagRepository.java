package com.aibook.repository;

import com.aibook.model.entity.Tag;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<Tag> findByUser(User user);

    /**
     * 根据用户和名称查询标签
     */
    Tag findByNameAndUser(String name, User user);
}
