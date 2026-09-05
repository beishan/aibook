package com.aibook.dto;

import java.time.LocalDateTime;

public record ReaderBackgroundDTO(
        Long id,
        String name,
        String imageUrl,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt) {}
