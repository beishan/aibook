package com.aibook.controller;

import com.aibook.dto.recommendation.RecommendationRuleRequest;
import com.aibook.model.entity.User;
import com.aibook.service.RecommendationCandidates;
import com.aibook.service.RecommendationService;
import com.aibook.service.UserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {
    private final RecommendationService service;
    private final RecommendationCandidates candidates;
    private final UserService users;

    private User user(Authentication authentication) {
        return users.findByUsername(authentication.getName());
    }

    @GetMapping("/rules")
    public List<RecommendationService.RuleView> rules(Authentication auth) {
        return service.rules(user(auth));
    }

    @PostMapping("/rules")
    public RecommendationService.RuleView create(Authentication auth,
            @RequestBody RecommendationRuleRequest request) {
        return service.save(user(auth), null, request);
    }

    @PutMapping("/rules/{id}")
    public RecommendationService.RuleView update(Authentication auth, @PathVariable Long id,
            @RequestBody RecommendationRuleRequest request) {
        return service.save(user(auth), id, request);
    }

    @DeleteMapping("/rules/{id}")
    public void delete(Authentication auth, @PathVariable Long id) {
        service.delete(user(auth), id);
    }

    @GetMapping("/today")
    public RecommendationService.TodayView today(Authentication auth) {
        return service.today(user(auth));
    }

    @PostMapping("/rules/{id}/reroll")
    public RecommendationService.GroupView reroll(Authentication auth, @PathVariable Long id) {
        return service.reroll(user(auth), id);
    }

    @PostMapping("/preview")
    public Map<String, Integer> preview(Authentication auth, @RequestBody RecommendationRuleRequest request) {
        return Map.of("count", service.preview(user(auth), request));
    }

    @GetMapping("/options")
    public Map<String, Object> options(Authentication auth) {
        return candidates.options(user(auth));
    }
}
