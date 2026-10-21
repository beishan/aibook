package com.aibook.service;

import com.aibook.dto.OperationLogDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    @Transactional
    public void record(
            User user,
            OperationLog.Action action,
            Book book,
            String description,
            String details) {
        recordEntry(
                user,
                action,
                book == null ? null : book.getId(),
                book == null ? null : book.getTitle(),
                description,
                details);
    }

    @Transactional
    public void recordEntry(
            User user,
            OperationLog.Action action,
            Long bookId,
            String bookTitle,
            String description,
            String details) {
        operationLogRepository.save(OperationLog.builder()
                .user(user)
                .action(action)
                .bookId(bookId)
                .bookTitle(bookTitle)
                .description(description)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<OperationLogDTO> getLogs(User user, Pageable pageable) {
        return operationLogRepository.findByUser(user, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<OperationLogDTO> getLogs(User user, Pageable pageable, String action, String keyword) {
        OperationLog.Action type = null;
        if (action != null && !action.isBlank()) {
            try {
                type = OperationLog.Action.valueOf(action.trim());
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的日志类型");
            }
        }
        String text = keyword == null ? "" : keyword.trim();
        if (text.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索内容最多 200 字符");
        }
        String pattern = text.isEmpty() ? "" : "%" + text.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return operationLogRepository.search(user, type, pattern, pageable).map(this::toDTO);
    }

    @Transactional
    public void retain(User user, Long id) {
        if (operationLogRepository.retain(user, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日志不存在");
        }
    }

    @Transactional
    public void delete(User user, Long id) {
        if (operationLogRepository.deleteOwned(user, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日志不存在");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasRetainedLogs(User user) {
        return operationLogRepository.existsByUserAndRetainedTrue(user);
    }

    private OperationLogDTO toDTO(OperationLog log) {
        return OperationLogDTO.builder()
                .id(log.getId())
                .action(log.getAction().name())
                .bookId(log.getBookId())
                .bookTitle(log.getBookTitle())
                .description(log.getDescription())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .retained(log.isRetained())
                .build();
    }
}
