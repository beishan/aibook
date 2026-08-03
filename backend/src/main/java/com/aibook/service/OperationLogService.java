package com.aibook.service;

import com.aibook.dto.OperationLogDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
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
        operationLogRepository.save(OperationLog.builder()
                .user(user)
                .action(action)
                .bookId(book == null ? null : book.getId())
                .bookTitle(book == null ? null : book.getTitle())
                .description(description)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<OperationLogDTO> getLogs(User user, Pageable pageable) {
        return operationLogRepository.findByUser(user, pageable).map(this::toDTO);
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
