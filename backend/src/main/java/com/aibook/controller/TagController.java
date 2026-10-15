package com.aibook.controller;

import com.aibook.dto.TagDTO;
import com.aibook.dto.TagRequest;
import com.aibook.dto.TagLibraryScanPageDTO;
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

    /** 按页扫描当前账户书库内的标签，前端可据此展示实际扫描进度。 */
    @GetMapping("/library-scan")
    public ResponseEntity<TagLibraryScanPageDTO> scanLibraryTags(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        User user = userService.findByUsername(authentication.getName());
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        return ResponseEntity.ok(tagService.scanLibraryTags(user, safePage, safeSize));
    }

    @PostMapping("/library-scan/import")
    public ResponseEntity<List<TagDTO>> addScannedTags(
            Authentication authentication,
            @RequestBody TagNamesRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(tagService.addScannedTags(request.names(), user));
    }

    @PostMapping("/batch-delete")
    public ResponseEntity<BatchDeleteResponse> deleteTags(
            Authentication authentication,
            @RequestBody TagIdsRequest request) {
        User user = userService.findByUsername(authentication.getName());
        int deletedCount = tagService.deleteTags(request.ids(), user);
        return ResponseEntity.ok(new BatchDeleteResponse(deletedCount));
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

    public record TagNamesRequest(List<String> names) {}

    public record TagIdsRequest(List<Long> ids) {}

    public record BatchDeleteResponse(int deletedCount) {}
}
