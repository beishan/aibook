package com.aibook.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationLogRetentionDTO(List<Rule> rules, Schedule schedule) {
    /** Older clients submitting only retention rules preserve existing scheduling. */
    public OperationLogRetentionDTO(List<Rule> rules) {
        this(rules, null);
    }

    public record Rule(String action, String label, Integer retentionDays) { }

    public record Schedule(Boolean enabled, String frequency, Integer hour, Integer minute,
            Integer dayOfWeek, LocalDateTime nextRunAt, LocalDateTime lastRunAt) { }
}
