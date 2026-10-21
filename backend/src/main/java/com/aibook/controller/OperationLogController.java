package com.aibook.controller;

import com.aibook.dto.OperationLogDTO;
import com.aibook.dto.OperationLogRetentionDTO;
import com.aibook.model.entity.User;
import com.aibook.service.OperationLogRetentionService;
import com.aibook.service.OperationLogService;
import com.aibook.service.UserService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OperationLogService operationLogService;
    private final UserService userService;
    private final OperationLogRetentionService retentionService;

    @GetMapping
    public ResponseEntity<Page<OperationLogDTO>> getLogs(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword) {
        User user = userService.findByUsername(authentication.getName());
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        PageRequest pageRequest = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return ResponseEntity.ok(operationLogService.getLogs(user, pageRequest, action, keyword));
    }

    @GetMapping("/retention")
    public OperationLogRetentionDTO retention(Authentication authentication) {
        return retentionService.getSettings(currentUser(authentication));
    }

    @PutMapping("/retention")
    public OperationLogRetentionDTO updateRetention(Authentication authentication,
            @RequestBody OperationLogRetentionDTO request) {
        return retentionService.updateSettings(currentUser(authentication), request);
    }

    @PostMapping("/cleanup")
    public Map<String, Integer> cleanup(Authentication authentication) {
        return Map.of("deletedCount", retentionService.cleanup(currentUser(authentication)));
    }

    @PostMapping("/{id}/retain")
    public ResponseEntity<Void> retain(Authentication authentication, @PathVariable Long id) {
        operationLogService.retain(currentUser(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        operationLogService.delete(currentUser(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
