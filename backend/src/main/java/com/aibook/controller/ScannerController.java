package com.aibook.controller;

import com.aibook.model.entity.User;
import com.aibook.service.FileScannerService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 文件扫描控制器
 */
@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ScannerController {

    private final FileScannerService scannerService;
    private final UserService userService;

    /**
     * 手动触发扫描
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> triggerScan(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        FileScannerService.ScanResult result = scannerService.scan(user);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "扫描完成",
            "newBooks", result.getNewCount(),
            "skippedBooks", result.getSkippedCount(),
            "failedBooks", result.getFailedCount(),
            "duration", result.getDuration()
        ));
    }
}
