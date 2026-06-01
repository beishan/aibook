package com.aibook.repository;

import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 分类 Repository
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * 根据用户查询分类
     */
    List<Category> findByUser(User user);

    /**
     * 根据用户和父分类查询子分类
     */
    List<Category> findByUserAndParent(User user, Category parent);

    /**
     * 根据用户和名称查询分类
     */
    Category findByNameAndUser(String name, User user);
}
