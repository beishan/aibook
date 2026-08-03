package com.aibook.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibook.dto.SystemRuntimeDTO;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SystemRuntimeControllerTest {

    @Test
    void returnsCurrentProcessStartTimeAndUptime() {
        SystemRuntimeDTO runtime = new SystemRuntimeController().runtime().getBody();

        assertThat(runtime).isNotNull();
        assertThat(runtime.startedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(runtime.uptimeMillis()).isNotNegative();
    }
}
