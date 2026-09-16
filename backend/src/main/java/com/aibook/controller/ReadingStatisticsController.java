package com.aibook.controller;

import com.aibook.dto.ReadingStatisticsDTO;
import com.aibook.model.entity.User;
import com.aibook.service.ReadingStatisticsService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读统计控制器：提供当前账户的阅读报告数据。
 */
@RestController
@RequestMapping("/api/reading-statistics")
@RequiredArgsConstructor
public class ReadingStatisticsController {

    private final ReadingStatisticsService readingStatisticsService;
    private final UserService userService;

    /**
     * 获取当前用户的阅读统计数据。
     */
    @GetMapping
    public ResponseEntity<ReadingStatisticsDTO> getStatistics(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(readingStatisticsService.generateStatistics(user));
    }
}
