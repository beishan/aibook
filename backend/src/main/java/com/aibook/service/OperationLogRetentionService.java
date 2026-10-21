package com.aibook.service;

import com.aibook.dto.OperationLogRetentionDTO;
import com.aibook.dto.OperationLogRetentionDTO.Rule;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.OperationLogRetentionPolicy;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRepository;
import com.aibook.repository.OperationLogRetentionPolicyRepository;
import com.aibook.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OperationLogRetentionService {
    private final OperationLogRetentionPolicyRepository policies;
    private final OperationLogRepository logs;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public OperationLogRetentionDTO getSettings(User user) {
        Map<OperationLog.Action, Integer> configured = new EnumMap<>(OperationLog.Action.class);
        policies.findByUser(user).forEach(policy ->
                configured.put(policy.getAction(), policy.getRetentionDays()));
        return new OperationLogRetentionDTO(Arrays.stream(OperationLog.Action.values())
                .map(action -> new Rule(action.name(), label(action), configured.get(action))).toList());
    }

    @Transactional
    public OperationLogRetentionDTO updateSettings(User user, OperationLogRetentionDTO request) {
        if (request == null || request.rules() == null
                || request.rules().size() > OperationLog.Action.values().length) {
            throw badRequest("日志保留策略格式无效");
        }
        Map<OperationLog.Action, Integer> desired = new EnumMap<>(OperationLog.Action.class);
        for (Rule rule : request.rules()) {
            if (rule == null || rule.action() == null) {
                throw badRequest("无效的日志类型");
            }
            OperationLog.Action action;
            try {
                action = OperationLog.Action.valueOf(rule.action());
            } catch (IllegalArgumentException exception) {
                throw badRequest("无效的日志类型");
            }
            if (desired.containsKey(action)) {
                throw badRequest("日志类型不能重复");
            }
            Integer days = rule.retentionDays();
            if (days != null && (days < 0 || days > 3650)) {
                throw badRequest("保留时间须为 0–3650 天，0 表示永久留存");
            }
            desired.put(action, days);
        }
        lockUser(user);
        Map<OperationLog.Action, OperationLogRetentionPolicy> existing =
                new EnumMap<>(OperationLog.Action.class);
        policies.findByUser(user).forEach(policy -> existing.put(policy.getAction(), policy));
        for (OperationLog.Action action : OperationLog.Action.values()) {
            Integer days = desired.get(action);
            OperationLogRetentionPolicy policy = existing.get(action);
            if (days == null) {
                if (policy != null) {
                    policies.delete(policy);
                }
            } else {
                if (policy == null) {
                    policy = OperationLogRetentionPolicy.builder().user(user).action(action).build();
                }
                policy.setRetentionDays(days);
                policies.save(policy);
            }
        }
        policies.flush();
        return getSettings(user);
    }

    @Transactional
    public int cleanup(User user) {
        return cleanupAt(user, LocalDateTime.now());
    }

    int cleanupAt(User user, LocalDateTime now) {
        lockUser(user);
        int deleted = 0;
        for (OperationLogRetentionPolicy policy : policies.findByUser(user)) {
            Integer days = policy.getRetentionDays();
            if (days != null && days > 0 && days <= 3650) {
                deleted += logs.deleteExpired(user, policy.getAction(), now.minusDays(days));
            }
        }
        return deleted;
    }

    private void lockUser(User user) {
        users.findByIdForLogPolicyUpdate(user.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private String label(OperationLog.Action action) {
        return switch (action) {
            case IMPORT_BOOK -> "导入书籍";
            case OPEN_BOOK -> "打开书籍";
            case DELETE_BOOK -> "删除书籍";
            case PERMANENTLY_DELETE_BOOK -> "永久删除书籍";
            case RESTORE_BOOK -> "恢复书籍";
            case CREATE_USER -> "新增用户";
            case UPDATE_USER -> "修改用户";
            case DELETE_USER -> "删除用户";
            case RESET_PASSWORD -> "重置密码";
            case UPDATE_PROFILE -> "个人资料";
            case UPDATE_AVATAR -> "个人头像";
            case UPDATE_SITE_FAVICON -> "网站图标";
            case UPDATE_SITE_SETTINGS -> "网站设置";
            case UPDATE_SCAN_DIRECTORY_VISIBILITY -> "书库显示设置";
            case CRAWLER_TASK -> "爬虫任务";
            case ADD_TO_SHELF -> "加入书架";
            case REMOVE_FROM_SHELF -> "移出书架";
        };
    }
}
