package com.aibook.service;

import com.aibook.repository.OperationLogRetentionPolicyRepository;
import com.aibook.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationLogCleanupScheduler {
    private final OperationLogRetentionPolicyRepository policies;
    private final UserRepository users;
    private final OperationLogRetentionService retention;

    @Scheduled(cron = "${operation-logs.cleanup-poll-cron:0 * * * * ?}", zone = "Asia/Shanghai")
    public void cleanup() {
        for (Long userId : policies.findExpiringPolicyUserIds()) {
            try {
                users.findById(userId).ifPresent(user -> {
                    int deleted = retention.runScheduledCleanup(user);
                    if (deleted > 0) {
                        log.info("操作日志自动清理完成: userId={}, deleted={}", userId, deleted);
                    }
                });
            } catch (RuntimeException exception) {
                log.error("操作日志自动清理失败: userId={}", userId, exception);
            }
        }
    }
}
