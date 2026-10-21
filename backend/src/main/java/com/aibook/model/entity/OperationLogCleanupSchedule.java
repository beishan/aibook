package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "operation_log_cleanup_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogCleanupSchedule {
    @Id
    @Column(name = "user_id")
    private Long userId;

    // user_id is assigned directly; deriving it with @MapsId breaks first-save merge.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Frequency frequency = Frequency.HOURLY;

    @Builder.Default
    @Column(nullable = false)
    private int hour = 3;

    @Builder.Default
    @Column(nullable = false)
    private int minute = 35;

    @Builder.Default
    @Column(nullable = false)
    private int dayOfWeek = 1;

    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;

    public enum Frequency { HOURLY, DAILY, WEEKLY }

    public LocalDateTime nextAfter(LocalDateTime now) {
        LocalDateTime next = now.withSecond(0).withNano(0).withMinute(minute);
        if (frequency != Frequency.HOURLY) {
            next = next.withHour(hour);
        }
        if (frequency == Frequency.WEEKLY) {
            next = next.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)));
        }
        if (!next.isAfter(now)) {
            next = switch (frequency) {
                case HOURLY -> next.plusHours(1);
                case DAILY -> next.plusDays(1);
                case WEEKLY -> next.plusWeeks(1);
            };
        }
        return next;
    }
}
