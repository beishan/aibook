package com.aibook.controller;

import com.aibook.dto.FontAssetDTO;
import com.aibook.dto.FontAssetUpdateRequest;
import com.aibook.dto.FontScanResultDTO;
import com.aibook.model.entity.User;
import com.aibook.service.FontService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/fonts")
@RequiredArgsConstructor
public class FontController {

    private final FontService fontService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<FontAssetDTO>> list() {
        return ResponseEntity.ok(fontService.list());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FontAssetDTO>> upload(
            Authentication authentication,
            @RequestParam(name = "files", required = false)
            List<MultipartFile> files,
            @RequestParam(name = "file", required = false)
            MultipartFile singleFile) {
        requireAdmin(authentication);
        List<MultipartFile> all = new ArrayList<>();
        if (files != null) {
            all.addAll(files);
        }
        if (singleFile != null) {
            all.add(singleFile);
        }
        return ResponseEntity.ok(fontService.upload(all));
    }

    @PostMapping("/scan")
    public ResponseEntity<FontScanResultDTO> scan(Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(fontService.scan());
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<FileSystemResource> content(
            @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false)
            String ifNoneMatch) {
        FontService.FontContent content = fontService.content(id);
        if (content.etag().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(content.etag())
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.path().toFile().length())
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .eTag(content.etag())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"font-" + id + "."
                                + extension(content.path()) + "\"")
                .body(new FileSystemResource(content.path()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FontAssetDTO> update(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody FontAssetUpdateRequest request) {
        requireAdmin(authentication);
        return ResponseEntity.ok(fontService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication authentication, @PathVariable Long id) {
        requireAdmin(authentication);
        fontService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (user.getRole() != User.Role.ADMIN) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "仅管理员可以管理字体");
        }
    }

    private String extension(java.nio.file.Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "bin" : name.substring(dot + 1);
    }
}
