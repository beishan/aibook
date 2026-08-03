package com.aibook.controller;

import com.aibook.dto.OperationLogDTO;
import com.aibook.model.entity.User;
import com.aibook.service.OperationLogService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping
    public ResponseEntity<Page<OperationLogDTO>> getLogs(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = userService.findByUsername(authentication.getName());
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        PageRequest pageRequest = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return ResponseEntity.ok(operationLogService.getLogs(user, pageRequest));
    }
}
