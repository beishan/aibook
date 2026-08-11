package com.aibook.service;

import com.aibook.dto.TrashCleanupSettingsDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrashCleanupService {

    static final int DEFAULT_RETENTION_DAYS = 0;
    private static final Set<Integer> ALLOWED_RETENTION_DAYS =
            Set.of(0, 7, 15, 30, 60, 90);

    private final UserRepository userRepository;
    private final BookService bookService;

    public TrashCleanupSettingsDTO getSettings(User user) {
        return new TrashCleanupSettingsDTO(normalizeRetentionDays(user.getTrashRetentionDays()));
    }

    @Transactional
    public TrashCleanupSettingsDTO updateSettings(
            User user, TrashCleanupSettingsDTO request) {
        if (request == null || !ALLOWED_RETENTION_DAYS.contains(request.retentionDays())) {
            throw new IllegalArgumentException("回收站保留时间仅支持永久保留、7、15、30、60 或 90 天");
        }
        user.setTrashRetentionDays(request.retentionDays());
        return getSettings(userRepository.save(user));
    }

    @Scheduled(cron = "${trash.cleanup-cron:0 20 * * * ?}")
    public void scheduledCleanup() {
        runCleanup(LocalDateTime.now());
    }

    void runCleanup(LocalDateTime now) {
        for (User user : userRepository.findByTrashRetentionDaysGreaterThan(0)) {
            if (!user.isEnabled()) continue;
            int retentionDays = normalizeRetentionDays(user.getTrashRetentionDays());
            if (retentionDays <= 0) continue;
            try {
                int purged = bookService.purgeExpiredTrash(
                        user, now.minusDays(retentionDays));
                if (purged > 0) {
                    log.info(
                            "回收站自动清理完成: userId={}, retentionDays={}, purged={}",
                            user.getId(), retentionDays, purged);
                }
            } catch (RuntimeException exception) {
                log.error("回收站自动清理失败: userId={}", user.getId(), exception);
            }
        }
    }

    private int normalizeRetentionDays(Integer value) {
        return value != null && ALLOWED_RETENTION_DAYS.contains(value)
                ? value
                : DEFAULT_RETENTION_DAYS;
    }
}
