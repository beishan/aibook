package com.aibook.controller;

import com.aibook.dto.CrawlerSettingsDtos.CrawlerRequestSettings;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.crawler.CrawlerHttpClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crawler-settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CrawlerSettingsController {
    private final CrawlerSettingsService service;
    private final CrawlerHttpClient httpClient;

    @GetMapping public CrawlerRequestSettings settings() { return service.settings(); }
    @PutMapping public CrawlerRequestSettings update(
            @Valid @RequestBody CrawlerRequestSettings request) {
        CrawlerRequestSettings updated = service.update(request);
        httpClient.refreshGlobalConfiguration(updated);
        return updated;
    }
}
