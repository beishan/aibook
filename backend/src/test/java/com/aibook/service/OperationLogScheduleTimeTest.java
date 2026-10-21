package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.aibook.model.entity.OperationLogCleanupSchedule;
import com.aibook.model.entity.OperationLogCleanupSchedule.Frequency;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OperationLogScheduleTimeTest {
    @Test
    void hourlyRespectsMinuteAndRollsOverYearAtExactBoundary() {
        var schedule = OperationLogCleanupSchedule.builder().minute(35).build();
        assertThat(schedule.nextAfter(LocalDateTime.of(2026, 10, 21, 12, 34, 59)))
                .isEqualTo(LocalDateTime.of(2026, 10, 21, 12, 35));
        assertThat(schedule.nextAfter(LocalDateTime.of(2026, 10, 21, 12, 35)))
                .isEqualTo(LocalDateTime.of(2026, 10, 21, 13, 35));
        assertThat(schedule.nextAfter(LocalDateTime.of(2026, 12, 31, 23, 59)))
                .isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 35));
    }

    @Test
    void dailyAdvancesAtExactTimeAndHandlesLeapDay() {
        var schedule = OperationLogCleanupSchedule.builder().frequency(Frequency.DAILY)
                .hour(3).minute(10).build();
        assertThat(schedule.nextAfter(LocalDateTime.of(2028, 2, 28, 3, 10)))
                .isEqualTo(LocalDateTime.of(2028, 2, 29, 3, 10));
        assertThat(schedule.nextAfter(LocalDateTime.of(2028, 2, 28, 3, 9)))
                .isEqualTo(LocalDateTime.of(2028, 2, 28, 3, 10));
    }

    @Test
    void weeklyUsesConfiguredWeekdayAndMovesSevenDaysAtBoundary() {
        var schedule = OperationLogCleanupSchedule.builder().frequency(Frequency.WEEKLY)
                .dayOfWeek(1).hour(3).minute(35).build();
        assertThat(schedule.nextAfter(LocalDateTime.of(2026, 10, 21, 12, 0)))
                .isEqualTo(LocalDateTime.of(2026, 10, 26, 3, 35));
        assertThat(schedule.nextAfter(LocalDateTime.of(2026, 10, 26, 3, 35)))
                .isEqualTo(LocalDateTime.of(2026, 11, 2, 3, 35));
    }
}
