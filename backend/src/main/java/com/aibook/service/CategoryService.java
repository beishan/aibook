package com.aibook.service;

import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类服务
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 获取用户所有分类
     */
    public List<Category> getCategories(User user) {
        return categoryRepository.findByUser(user);
    }

    /**
     * 获取子分类
     */
    public List<Category> getSubCategories(User user, Long parentId) {
        Category parent = categoryRepository.findById(parentId).orElse(null);
        return categoryRepository.findByUserAndParent(user, parent);
    }

    /**
     * 创建分类
     */
    @Transactional
    public Category createCategory(User user, String name, String description, Long parentId) {
        Category existing = categoryRepository.findByNameAndUser(name, user);
        if (existing != null) {
            throw new RuntimeException("分类已存在");
        }

        Category category = Category.builder()
                .name(name)
                .description(description)
                .user(user)
                .build();

        if (parentId != null) {
            Category parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("父分类不存在"));
            category.setParent(parent);
        }

        return categoryRepository.save(category);
    }

    /**
     * 更新分类
     */
    @Transactional
    public Category updateCategory(Long id, String name, String description, User user) {
        Category category = categoryRepository.findById(id)
                .filter(c -> c.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("分类不存在"));

        category.setName(name);
        category.setDescription(description);

        return categoryRepository.save(category);
    }

    /**
     * 删除分类
     */
    @Transactional
    public void deleteCategory(Long id, User user) {
        Category category = categoryRepository.findById(id)
                .filter(c -> c.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("分类不存在"));

        categoryRepository.delete(category);
    }
}
