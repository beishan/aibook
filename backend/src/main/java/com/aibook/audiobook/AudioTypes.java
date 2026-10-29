package com.aibook.audiobook;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Versioned API contract. Media positions and wall-clock listening time are distinct. */
public final class AudioTypes {
    private AudioTypes() {}

    public record EditionRequest(
            Long bookId, @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String author, @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String narrator, @Size(max = 80) String language,
            @Size(max = 20000) String description) {}

    public record ProbeChapter(String title, long startMs, long endMs) {}
    public record ProbeResult(long durationMs, String mimeType, String codec,
                              String title, String narrator, Integer discNumber, Integer trackNumber, List<ProbeChapter> chapters) {}
    public record Candidate(String path, String root, String group, String title,
                            long durationMs, long size, long modifiedMs, String hash,
                            String mimeType, String codec, Integer discNumber, Integer trackNumber, List<ProbeChapter> chapters,
                            String error) {}
    public record ImportRequest(@NotBlank String taskId, @NotEmpty List<String> paths,
                                @NotNull @Valid EditionRequest edition, Long editionId) {}
    public record ScanRequest(@NotNull Long directoryId, @Size(max = 1500) String relativePath) {}
    public record SessionRequest(boolean takeover) {}
    public record Heartbeat(@Min(0) long sequence, @Min(0) long revision,
                            @Min(1) long contentRevision, @NotNull Long trackId,
                            @Min(0) long positionMs, @Min(0) long elapsedMs,
                            boolean finished) {}
    public record BookmarkRequest(@NotNull Long trackId, @Min(0) long positionMs,
                                  @NotBlank @Size(max = 200) String title) {}
    public record TrackEdit(@NotBlank @Size(max = 500) String title) {}
    public record Segment(@NotNull Long trackId, @Min(0) long startMs, @Min(1) long endMs) {}
    public record ChapterEdit(@NotBlank @Size(max = 500) String title,
                              @NotEmpty List<@Valid Segment> segments) {}
    public record Settings(@DecimalMin("0.5") @DecimalMax("3.0") double speed,
                           @Min(5) @Max(120) int backSeconds,
                           @Min(5) @Max(120) int forwardSeconds,
                           @Min(1) @Max(180) int sleepMinutes) {}
}
