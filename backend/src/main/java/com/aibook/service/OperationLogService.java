package com.aibook.service;

import com.aibook.dto.OperationLogDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public List<OperationLogDTO> getCrawlerLogs(
            User user, String bookTitle, String siteName, int limit) {
        int safeLimit = Math.min(200, Math.max(1, limit));
        Pageable pageable = PageRequest.of(
                0,
                safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return operationLogRepository.findByUserAndActionAndBookTitleAndDetailsContaining(
                        user,
                        OperationLog.Action.CRAWLER_TASK,
                        bookTitle,
                        "网站：" + siteName,
                        pageable)
                .map(this::toDTO)
                .getContent();
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
                .build();
    }
}
