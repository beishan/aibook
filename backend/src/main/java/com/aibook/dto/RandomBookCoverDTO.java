package com.aibook.dto;

import java.time.LocalDateTime;

public record RandomBookCoverDTO(
        Long id,
        String name,
        String coverUrl,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt) {}
