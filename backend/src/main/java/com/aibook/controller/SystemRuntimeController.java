package com.aibook.controller;

import com.aibook.dto.SystemRuntimeDTO;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/runtime")
public class SystemRuntimeController {

    @GetMapping
    public ResponseEntity<SystemRuntimeDTO> runtime() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        return ResponseEntity.ok(new SystemRuntimeDTO(
                Instant.ofEpochMilli(runtime.getStartTime()),
                runtime.getUptime()));
    }
}
