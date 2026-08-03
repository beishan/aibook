package com.aibook.dto;

import java.time.Instant;

public record SystemRuntimeDTO(Instant startedAt, long uptimeMillis) {}
