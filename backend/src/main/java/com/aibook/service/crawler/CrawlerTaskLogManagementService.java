package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerTask;
import com.aibook.model.entity.CrawlerTaskLog;
import com.aibook.model.entity.CrawlerTaskLogRetention;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerTaskLogRepository;
import com.aibook.repository.CrawlerTaskLogRetentionRepository;
import com.aibook.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerTaskLogManagementService {
    private final CrawlerTaskLogRepository logs;
    private final CrawlerTaskLogRetentionRepository policies;
    private final UserRepository users;
    private final PlatformTransactionManager transactionManager;

    public record LogView(Long id, String taskId, String taskType, String executorName, String siteName,
            String bookName, String chapterName, String description, String details, boolean failed,
            boolean retained, LocalDateTime createdAt) { }
    public record Rule(String taskType, Integer retentionDays, Boolean automatic) { }

    @Transactional(readOnly = true)
    public Page<LogView> list(User user, int page, int size, String keyword, String type) {
        String selected = type == null ? "" : type;
        if (!selected.isEmpty()) validateType(selected);
        String value = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 200) throw bad("搜索内容不能超过200字");
        String pattern = value.isEmpty() ? "" : "%" + value.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
        return logs.searchForManagement(user, pattern, selected,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100))).map(this::view);
    }
    @Transactional
    public int retain(User user, List<Long> ids, boolean retained) {
        return logs.setRetained(user, checkedIds(ids), retained);
    }
    @Transactional
    public int delete(User user, List<Long> ids) {
        return logs.deleteSelected(user, checkedIds(ids));
    }
    @Transactional(readOnly = true)
    public List<Rule> rules(User user) {
        Map<String, CrawlerTaskLogRetention> stored = new HashMap<>();
        policies.findByUserId(user.getId()).forEach(rule -> stored.put(rule.getTaskType(), rule));
        return Arrays.stream(CrawlerTask.TaskType.values()).map(type -> {
            var rule = stored.get(type.name());
            return rule == null ? new Rule(type.name(), 0, false)
                    : new Rule(type.name(), rule.getRetentionDays(), Boolean.TRUE.equals(rule.getAutomatic()));
        }).toList();
    }
    @Transactional
    public List<Rule> saveRules(User user, List<Rule> rules) {
        if (rules == null || rules.size() > CrawlerTask.TaskType.values().length) throw bad("规则数量无效");
        users.findByIdForLogPolicyUpdate(user.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        Map<String, CrawlerTaskLogRetention> stored = new HashMap<>();
        policies.findByUserId(user.getId()).forEach(rule -> stored.put(rule.getTaskType(), rule));
        Set<String> seen = new HashSet<>();
        for (Rule rule : rules) {
            if (rule == null || rule.taskType() == null || rule.retentionDays() == null || rule.automatic() == null) throw bad("任务类型无效");
            validateType(rule.taskType());
            if (!seen.add(rule.taskType()) || rule.retentionDays() < 0 || rule.retentionDays() > 3650)
                throw bad("规则重复或保留天数不在0–3650范围");
            var entity = stored.getOrDefault(rule.taskType(), CrawlerTaskLogRetention.builder()
                    .user(user).taskType(rule.taskType()).build());
            entity.setRetentionDays(rule.retentionDays());
            entity.setAutomatic(rule.automatic());
            policies.save(entity);
        }
        policies.flush();
        return rules(user);
    }
    @Transactional
    public int cleanup(User user) {
        users.findByIdForLogPolicyUpdate(user.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        int count = 0;
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        for (var rule : policies.findByUserId(user.getId())) {
            if (rule.getRetentionDays() > 0)
                count += logs.deleteExpired(user.getId(), rule.getTaskType(), now.minusDays(rule.getRetentionDays()));
        }
        return count;
    }
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void scheduledCleanup() {
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        for (Long userId : policies.findAutomaticUserIds()) {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    if (users.findByIdForLogPolicyUpdate(userId).isEmpty()) return;
                    for (var rule : policies.findByUserId(userId)) {
                        if (Boolean.TRUE.equals(rule.getAutomatic()) && rule.getRetentionDays() > 0) {
                            validateType(rule.getTaskType());
                            logs.deleteExpired(userId, rule.getTaskType(), now.minusDays(rule.getRetentionDays()));
                        }
                    }
                });
            } catch (RuntimeException exception) {
                log.error("爬虫任务日志自动清理失败: userId={}", userId, exception);
            }
        }
    }
    private LogView view(CrawlerTaskLog log) {
        String details = log.getDetails() == null ? "" : log.getDetails();
        String type = Arrays.stream(CrawlerTask.TaskType.values()).map(Enum::name)
                .filter(value -> details.startsWith("任务ID：" + log.getTaskId() + "；任务类型："
                        + value + "；网站："))
                .findFirst().orElse("UNKNOWN");
        return new LogView(log.getId(), log.getTaskId(), type, log.getExecutorName(), log.getSiteName(),
                log.getBookName(), log.getChapterName(), log.getDescription(), details,
                Boolean.TRUE.equals(log.getFailed()), Boolean.TRUE.equals(log.getRetained()), log.getCreatedAt());
    }
    private List<Long> checkedIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw bad("请选择1至100条日志");
        return ids.stream().distinct().toList();
    }
    private void validateType(String type) {
        try { CrawlerTask.TaskType.valueOf(type); }
        catch (IllegalArgumentException exception) { throw bad("无效的任务类型"); }
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
