package com.aibook.controller;

import com.aibook.service.CrawlerPollingIntervalOptionsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crawler-settings/polling-interval-options")
@RequiredArgsConstructor
public class CrawlerPollingIntervalOptionsController {
    private final CrawlerPollingIntervalOptionsService service;

    @GetMapping
    public List<Integer> getOptions() {
        return service.getOptions();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Integer> updateOptions(@Valid @RequestBody UpdateRequest request) {
        return service.updateOptions(request.intervals());
    }

    public record UpdateRequest(
            @NotEmpty @Size(max = 20) List<@NotNull @Min(1) @Max(3600) Integer> intervals) { }
}
