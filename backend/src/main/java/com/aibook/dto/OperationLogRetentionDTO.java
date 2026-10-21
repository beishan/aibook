package com.aibook.dto;

import java.util.List;

public record OperationLogRetentionDTO(List<Rule> rules) {
    public record Rule(String action, String label, Integer retentionDays) { }
}
