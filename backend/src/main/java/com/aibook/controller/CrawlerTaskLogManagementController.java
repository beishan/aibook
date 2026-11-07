package com.aibook.controller;

import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerTaskLogManagementService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crawler/task-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CrawlerTaskLogManagementController {
    private final UserService users;
    private final CrawlerTaskLogManagementService service;
    public record Selection(List<Long> ids, boolean retained) { }

    @GetMapping
    public Page<CrawlerTaskLogManagementService.LogView> list(Authentication auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String type) {
        return service.list(user(auth), page, size, keyword, type);
    }
    @PostMapping("/retain")
    public Map<String, Integer> retain(Authentication auth, @RequestBody Selection request) {
        return Map.of("affectedCount", service.retain(user(auth), request.ids(), request.retained()));
    }
    @PostMapping("/delete")
    public Map<String, Integer> delete(Authentication auth, @RequestBody Selection request) {
        return Map.of("affectedCount", service.delete(user(auth), request.ids()));
    }
    @GetMapping("/retention")
    public List<CrawlerTaskLogManagementService.Rule> rules(Authentication auth) {
        return service.rules(user(auth));
    }
    @PutMapping("/retention")
    public List<CrawlerTaskLogManagementService.Rule> rules(Authentication auth,
            @RequestBody List<CrawlerTaskLogManagementService.Rule> request) {
        return service.saveRules(user(auth), request);
    }
    @PostMapping("/cleanup")
    public Map<String, Integer> cleanup(Authentication auth) {
        return Map.of("deletedCount", service.cleanup(user(auth)));
    }
    private User user(Authentication auth) { return users.findByUsername(auth.getName()); }
}
