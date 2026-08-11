package com.aibook.service;

import com.aibook.dto.ScheduledScanSettingsDTO;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.repository.ScanDirectoryRepository;
import com.aibook.repository.UserRepository;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 持久化用户定时扫描设置，并在到点后启动已有的目录扫描任务。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledScanService {

    static final String DEFAULT_SCAN_TIME = "02:00";
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private final UserRepository userRepository;
    private final ScanDirectoryRepository scanDirectoryRepository;
    private final ScanDirectoryTaskService scanDirectoryTaskService;
    private final Map<Long, LocalDateTime> lastTriggeredMinutes = new ConcurrentHashMap<>();

    @Value("${scanning.enabled:true}")
    private boolean scanningEnabled;

    @Value("${scanning.time-zone:Asia/Shanghai}")
    private String timeZone;

    public ScheduledScanSettingsDTO getSettings(User user) {
        return new ScheduledScanSettingsDTO(
                user.getScheduledScanEnabled() == null
                        || Boolean.TRUE.equals(user.getScheduledScanEnabled()),
                normalizeStoredTime(user.getScheduledScanTime()));
    }

    @Transactional
    public ScheduledScanSettingsDTO updateSettings(
            User user, ScheduledScanSettingsDTO request) {
        String scanTime = requireValidTime(request.time());
        user.setScheduledScanEnabled(request.enabled());
        user.setScheduledScanTime(scanTime);
        return getSettings(userRepository.save(user));
    }

    /** 每分钟检查一次，实际执行时间由每个用户的网页设置决定。 */
    @Scheduled(
            cron = "${scanning.scheduler-poll-cron:0 * * * * ?}",
            zone = "${scanning.time-zone:Asia/Shanghai}")
    public void scheduledScan() {
        if (!scanningEnabled) {
            return;
        }
        runDueScans(LocalDateTime.now(resolveTimeZone()));
    }

    void runDueScans(LocalDateTime now) {
        LocalDateTime currentMinute = now.truncatedTo(ChronoUnit.MINUTES);
        String currentTime = currentMinute.toLocalTime().format(TIME_FORMATTER);
        for (User user : userRepository.findAll()) {
            if (!user.isEnabled()
                    || Boolean.FALSE.equals(user.getScheduledScanEnabled())
                    || !normalizeStoredTime(user.getScheduledScanTime()).equals(currentTime)
                    || currentMinute.equals(lastTriggeredMinutes.get(user.getId()))) {
                continue;
            }

            lastTriggeredMinutes.put(user.getId(), currentMinute);
            int started = 0;
            for (ScanDirectory directory : scanDirectoryRepository.findByUser(user)) {
                if (!Boolean.TRUE.equals(directory.getEnabled())) {
                    continue;
                }
                try {
                    scanDirectoryTaskService.startScan(directory.getId(), user);
                    started++;
                } catch (RuntimeException exception) {
                    log.error(
                            "启动定时目录扫描失败: userId={}, directoryId={}, path={}",
                            user.getId(),
                            directory.getId(),
                            directory.getPath(),
                            exception);
                }
            }
            log.info("用户定时扫描已触发: userId={}, directoryCount={}", user.getId(), started);
        }
    }

    private ZoneId resolveTimeZone() {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            log.warn("无效的扫描时区 {}，改用 Asia/Shanghai", timeZone);
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private String normalizeStoredTime(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_SCAN_TIME;
        }
        try {
            return LocalTime.parse(value).format(TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            log.warn("用户定时扫描时间无效，改用默认值: {}", value);
            return DEFAULT_SCAN_TIME;
        }
    }

    private String requireValidTime(String value) {
        if (value == null || !value.matches("\\d{2}:\\d{2}")) {
            throw new IllegalArgumentException("扫描时间格式必须为 HH:mm");
        }
        try {
            return LocalTime.parse(value).format(TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("扫描时间无效: " + value);
        }
    }
}
