package com.aibook.controller;

import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerSiteActivityManagementService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crawler/sites/{siteId}/activities")
@RequiredArgsConstructor
public class CrawlerSiteActivityManagementController {
    private final UserService users;
    private final CrawlerSiteActivityManagementService service;

    public record Selection(List<Long> ids, boolean retained) { }

    @GetMapping("/retention")
    public CrawlerSiteActivityManagementService.Policy policy(Authentication auth, @PathVariable Long siteId) {
        return service.policy(user(auth), siteId);
    }

    @PutMapping("/retention")
    public CrawlerSiteActivityManagementService.Policy savePolicy(Authentication auth, @PathVariable Long siteId,
            @RequestBody CrawlerSiteActivityManagementService.Policy policy) {
        return service.savePolicy(user(auth), siteId, policy);
    }

    @PostMapping("/retain")
    public Map<String, Integer> retain(Authentication auth, @PathVariable Long siteId,
            @RequestBody Selection selection) {
        return Map.of("affectedCount", service.retain(user(auth), siteId, selection.ids(), selection.retained()));
    }

    @PostMapping("/delete")
    public Map<String, Integer> delete(Authentication auth, @PathVariable Long siteId,
            @RequestBody Selection selection) {
        return Map.of("affectedCount", service.deleteSelected(user(auth), siteId, selection.ids()));
    }

    @PostMapping("/clear")
    public Map<String, Integer> clear(Authentication auth, @PathVariable Long siteId) {
        return Map.of("deletedCount", service.clear(user(auth), siteId));
    }

    private User user(Authentication auth) {
        return users.findByUsername(auth.getName());
    }
}
