package com.aibook.service;

import com.aibook.model.entity.OperationLog;
import com.aibook.repository.OperationLogRepository;
import com.aibook.repository.UserRepository;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Stores bounded diagnostics without request bodies, credentials or database row values. */
@Service
@RequiredArgsConstructor
public class SystemErrorLogService {

    private final OperationLogRepository logs;
    private final UserRepository users;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String errorId, String username, String method, String route,
            Throwable error) {
        var user = username == null ? null : users.findByUsername(username).orElse(null);
        StringBuilder details = new StringBuilder("错误编号：").append(errorId)
                .append("\n请求：").append(method).append(' ').append(route);
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        for (int depth = 0; current != null && depth < 12 && visited.add(current); depth++) {
            details.append("\n异常：").append(current.getClass().getName());
            if (current instanceof SQLException sql) {
                details.append("\nSQLState：").append(sql.getSQLState());
            }
            if (current instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                if (constraint != null && constraint.matches("[a-zA-Z0-9_]{1,200}")) {
                    details.append("\n数据库约束：").append(constraint);
                }
            }
            StackTraceElement[] frames = current.getStackTrace();
            for (int index = 0; index < Math.min(frames.length, 16); index++) {
                details.append("\n  at ").append(frames[index]);
            }
            current = current.getCause();
        }
        details.append("\n诊断内容已省略异常原始消息、请求参数和数据库行值；完整异常请按错误编号查询后端运行日志。");
        logs.saveAndFlush(OperationLog.builder()
                .user(user)
                .ownerUserId(user == null ? null : user.getId())
                .ownerUsername(user == null ? "系统" : user.getUsername())
                .action(OperationLog.Action.SYSTEM_ERROR)
                .description("系统异常 · " + errorId)
                .details(details.substring(0, Math.min(details.length(), 24000)))
                .build());
    }
}
