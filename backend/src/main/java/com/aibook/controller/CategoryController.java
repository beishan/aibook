package com.aibook.controller;

import com.aibook.dto.CategoryDTO;
import com.aibook.dto.CategoryMoveRequest;
import com.aibook.dto.CategoryReorderRequest;
import com.aibook.dto.CategoryRequest;
import com.aibook.model.entity.User;
import com.aibook.service.CategoryService;
import com.aibook.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 分类管理控制器。
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final UserService userService;

    /**
     * 获取扁平分类列表，兼容下拉框等轻量场景。
     */
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getCategories(Authentication authentication) {
        return ResponseEntity.ok(categoryService.getCategories(currentUser(authentication)));
    }

    /**
     * 获取包含子分类和书籍数量的分类树。
     */
    @GetMapping("/tree")
    public ResponseEntity<List<CategoryDTO>> getCategoryTree(Authentication authentication) {
        return ResponseEntity.ok(categoryService.getCategoryTree(currentUser(authentication)));
    }

    @GetMapping("/{parentId}/subcategories")
    public ResponseEntity<List<CategoryDTO>> getSubCategories(
            Authentication authentication,
            @PathVariable Long parentId) {
        return ResponseEntity.ok(
                categoryService.getSubCategories(currentUser(authentication), parentId));
    }

    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(
            Authentication authentication,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(
                categoryService.createCategory(currentUser(authentication), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(
                categoryService.updateCategory(id, request, currentUser(authentication)));
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<CategoryDTO> moveCategory(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody CategoryMoveRequest request) {
        return ResponseEntity.ok(
                categoryService.moveCategory(id, request, currentUser(authentication)));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorderCategories(
            Authentication authentication,
            @Valid @RequestBody CategoryReorderRequest request) {
        categoryService.reorderCategories(request, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) Long targetCategoryId) {
        categoryService.deleteCategory(id, targetCategoryId, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<CategoryDTO> mergeCategory(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(categoryService.mergeCategory(
                id, body.get("targetCategoryId"), currentUser(authentication)));
    }

    /**
     * 为当前用户幂等补齐常见分类。
     */
    @PostMapping("/presets/initialize")
    public ResponseEntity<List<CategoryDTO>> initializePresets(Authentication authentication) {
        return ResponseEntity.ok(
                categoryService.initializePresets(currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
