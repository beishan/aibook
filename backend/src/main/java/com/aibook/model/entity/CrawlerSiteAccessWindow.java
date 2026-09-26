package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerSiteAccessWindow {
    @Column(name = "start_minute", nullable = false)
    private int startMinute;

    @Column(name = "end_minute", nullable = false)
    private int endMinute;

    public boolean containsMinute(int minuteOfDay) {
        if (startMinute < endMinute) {
            return minuteOfDay >= startMinute && minuteOfDay < endMinute;
        }
        return minuteOfDay >= startMinute || minuteOfDay < endMinute;
    }
}
