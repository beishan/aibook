package com.aibook.dto;

import java.util.List;
import java.util.Map;

public record DockIconStatusDTO(Long userId, List<String> icons, Map<String, Long> versions) {}
