package com.aibook.controller;

import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerExportService;
import com.aibook.service.crawler.CrawlerImportLibrariesService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/crawler/books/{id}/import-libraries")
public class CrawlerImportLibrariesController {
    private final CrawlerImportLibrariesService service;
    private final CrawlerExportService exports;
    private final UserService users;

    public record Selection(List<Long> libraryIds) {}

    @GetMapping
    public List<Long> get(Authentication auth, @PathVariable Long id) {
        return service.get(users.findByUsername(auth.getName()), id);
    }

    @PutMapping
    @Transactional
    public List<Long> save(Authentication auth, @PathVariable Long id, @RequestBody Selection request) {
        var user = users.findByUsername(auth.getName());
        var ids = service.save(user, id, request.libraryIds());
        // 已完成的书籍立即执行；运行中的任务在正文成功后执行。
        if (!ids.isEmpty()) exports.autoImportCompleted(user, id);
        return ids;
    }
}
