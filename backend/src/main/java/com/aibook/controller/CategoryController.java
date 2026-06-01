package com.aibook.controller;

import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.service.CategoryService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 分类控制器
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryService categoryService;
    private final UserService userService;

    /**
     * 获取所有分类
     */
    @GetMapping
    public ResponseEntity<List<Category>> getCategories(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        List<Category> categories = categoryService.getCategories(user);
        return ResponseEntity.ok(categories);
    }

    /**
     * 获取子分类
     */
    @GetMapping("/{parentId}/subcategories")
    public ResponseEntity<List<Category>> getSubCategories(
            Authentication authentication,
            @PathVariable Long parentId) {
        User user = userService.findByUsername(authentication.getName());
        List<Category> categories = categoryService.getSubCategories(user, parentId);
        return ResponseEntity.ok(categories);
    }

    /**
     * 创建分类
     */
    @PostMapping
    public ResponseEntity<Category> createCategory(
            Authentication authentication,
            @RequestBody Map<String, Object> body) {
        User user = userService.findByUsername(authentication.getName());
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        Long parentId = body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null;

        Category category = categoryService.createCategory(user, name, description, parentId);
        return ResponseEntity.ok(category);
    }

    /**
     * 更新分类
     */
    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = userService.findByUsername(authentication.getName());
        Category category = categoryService.updateCategory(id, body.get("name"), body.get("description"), user);
        return ResponseEntity.ok(category);
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            Authentication authentication,
            @PathVariable Long id) {
        User user = userService.findByUsername(authentication.getName());
        categoryService.deleteCategory(id, user);
        return ResponseEntity.noContent().build();
    }
}
