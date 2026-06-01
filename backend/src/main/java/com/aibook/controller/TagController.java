package com.aibook.controller;

import com.aibook.model.entity.Tag;
import com.aibook.model.entity.User;
import com.aibook.service.TagService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<List<Tag>> getTags(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        List<Tag> tags = tagService.getTags(user);
        return ResponseEntity.ok(tags);
    }

    /**
     * 创建标签
     */
    @PostMapping
    public ResponseEntity<Tag> createTag(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        User user = userService.findByUsername(authentication.getName());
        Tag tag = tagService.createTag(user, body.get("name"), body.get("color"));
        return ResponseEntity.ok(tag);
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public ResponseEntity<Tag> updateTag(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = userService.findByUsername(authentication.getName());
        Tag tag = tagService.updateTag(id, body.get("name"), body.get("color"), user);
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
