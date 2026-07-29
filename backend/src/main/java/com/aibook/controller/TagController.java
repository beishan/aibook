package com.aibook.controller;

import com.aibook.dto.TagDTO;
import com.aibook.dto.TagRequest;
import com.aibook.model.entity.User;
import com.aibook.service.TagService;
import com.aibook.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签控制器
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TagController {

    private final TagService tagService;
    private final UserService userService;

    /**
     * 获取所有标签
     */
    @GetMapping
    public ResponseEntity<List<TagDTO>> getTags(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        List<TagDTO> tags = tagService.getTags(user);
        return ResponseEntity.ok(tags);
    }

    /**
     * 创建标签
     */
    @PostMapping
    public ResponseEntity<TagDTO> createTag(
            Authentication authentication,
            @Valid @RequestBody TagRequest request) {
        User user = userService.findByUsername(authentication.getName());
        TagDTO tag = tagService.createTag(user, request.getName(), request.getColor());
        return ResponseEntity.ok(tag);
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public ResponseEntity<TagDTO> updateTag(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody TagRequest request) {
        User user = userService.findByUsername(authentication.getName());
        TagDTO tag = tagService.updateTag(
                id, request.getName(), request.getColor(), user);
        return ResponseEntity.ok(tag);
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(
            Authentication authentication,
            @PathVariable Long id) {
        User user = userService.findByUsername(authentication.getName());
        tagService.deleteTag(id, user);
        return ResponseEntity.noContent().build();
    }
}
