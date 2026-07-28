package com.aibook.repository;

import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
     * 根据用户查询分类并按显示顺序排列
     */
    List<Category> findByUserOrderBySortOrderAscNameAsc(User user);

    /**
     * 根据用户和父分类查询子分类
     */
    List<Category> findByUserAndParentOrderBySortOrderAscNameAsc(User user, Category parent);

    /**
     * 根据用户和名称查询分类
     */
    Optional<Category> findByIdAndUser(Long id, User user);

    /**
     * 判断同一父分类下是否重名。
     */
    boolean existsByUserAndParentAndNameIgnoreCase(User user, Category parent, String name);

    /**
     * 更新分类时排除自身进行重名检查。
     */
    boolean existsByUserAndParentAndNameIgnoreCaseAndIdNot(
            User user, Category parent, String name, Long id);

    long countByUser(User user);
}
