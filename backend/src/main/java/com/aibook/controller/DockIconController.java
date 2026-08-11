package com.aibook.controller;

import com.aibook.dto.DockIconStatusDTO;
import com.aibook.model.entity.User;
import com.aibook.service.DockIconService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user/dock-icons")
@RequiredArgsConstructor
public class DockIconController {

    private final UserService userService;
    private final DockIconService dockIconService;

    @GetMapping
    public ResponseEntity<DockIconStatusDTO> getStatus(Authentication authentication) {
        return ResponseEntity.ok(dockIconService.getStatus(currentUser(authentication)));
    }

    @PostMapping(value = "/{name}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DockIconStatusDTO> upload(
            Authentication authentication,
            @PathVariable String name,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(dockIconService.upload(currentUser(authentication), name, file));
    }

    @GetMapping("/{name}")
    public ResponseEntity<Resource> getIcon(
            Authentication authentication,
            @PathVariable String name) {
        DockIconService.DockIconContent icon =
                dockIconService.getIcon(currentUser(authentication), name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, icon.contentType())
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(icon.path()));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<DockIconStatusDTO> delete(
            Authentication authentication,
            @PathVariable String name) {
        return ResponseEntity.ok(dockIconService.delete(currentUser(authentication), name));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
