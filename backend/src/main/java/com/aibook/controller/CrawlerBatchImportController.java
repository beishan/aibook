package com.aibook.controller;

import com.aibook.dto.crawler.CrawlerBatchImportDtos.JobView;
import com.aibook.dto.crawler.CrawlerBatchImportDtos.Request;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerBatchImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crawler/books/batch/import")
@RequiredArgsConstructor
public class CrawlerBatchImportController {
    private final UserService userService;
    private final CrawlerBatchImportService batchImportService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobView submit(Authentication authentication, @Valid @RequestBody Request request) {
        return batchImportService.submit(userService.findByUsername(authentication.getName()),
                request.bookIds(), request.formats());
    }

    @GetMapping("/{jobId}")
    public JobView progress(Authentication authentication, @PathVariable String jobId) {
        return batchImportService.progress(userService.findByUsername(authentication.getName()), jobId);
    }
}
