package com.aibook.dto.crawler;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class CrawlerBlacklistScreenDtos {
    private CrawlerBlacklistScreenDtos() { }

    public record Request(@Positive Long siteId,
            @Pattern(regexp = "MANAGED|DISCOVERED") String scope) {
        public Request(Long siteId) {
            this(siteId, "MANAGED");
        }
    }
    public record ApplyRequest(boolean all,
            @Size(max = 100) List<@NotNull @Positive Long> bookIds) { }
    public record JobView(String id, Long siteId, String status, long total,
            long processed, long matched, String errorMessage, LocalDateTime createdAt, String scope) { }
    public record Item(Long bookId, String bookName, String author, String siteName,
            String reason, boolean blacklisted, boolean available,
            List<com.aibook.util.CrawlerAutoBlacklist.MatchDetail> matchDetails) { }
    public record ApplyResult(int affectedCount) { }
}
