package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerSiteActivityRetention;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerSiteActivityRepository;
import com.aibook.repository.CrawlerSiteActivityRetentionRepository;
import com.aibook.repository.CrawlerSiteRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerSiteActivityManagementService {
    private final CrawlerSiteRepository sites;
    private final CrawlerSiteActivityRepository activities;
    private final CrawlerSiteActivityRetentionRepository policies;
    private final PlatformTransactionManager transactions;

    public record Policy(Integer retentionDays, Boolean automatic) { }

    @Transactional(readOnly = true)
    public Policy policy(User user, Long siteId) {
        CrawlerSite site = ownedSite(user, siteId);
        return policies.findBySiteId(site.getId())
                .map(rule -> new Policy(rule.getRetentionDays(), rule.getAutomatic()))
                .orElse(new Policy(0, false));
    }

    @Transactional
    public Policy savePolicy(User user, Long siteId, Policy policy) {
        if (policy == null || policy.retentionDays() == null || policy.automatic() == null
                || policy.retentionDays() < 0 || policy.retentionDays() > 3650) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "保留天数须在 0 至 3650 天之间");
        }
        CrawlerSite site = lockedSite(user, siteId);
        CrawlerSiteActivityRetention rule = policies.findBySiteId(siteId)
                .orElseGet(() -> CrawlerSiteActivityRetention.builder().site(site).build());
        rule.setRetentionDays(policy.retentionDays());
        rule.setAutomatic(policy.automatic());
        policies.save(rule);
        return policy;
    }

    @Transactional
    public int retain(User user, Long siteId, List<Long> ids, boolean retained) {
        return activities.setRetained(ownedSite(user, siteId), checkedIds(ids), retained);
    }

    @Transactional
    public int deleteSelected(User user, Long siteId, List<Long> ids) {
        return activities.deleteSelected(ownedSite(user, siteId), checkedIds(ids));
    }

    @Transactional
    public int clear(User user, Long siteId) {
        return activities.deleteUnretained(lockedSite(user, siteId));
    }

    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void scheduledCleanup() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        for (Long siteId : policies.findAutomaticSiteIds()) {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    CrawlerSite site = sites.findById(siteId).orElse(null);
                    if (site == null) return;
                    site = sites.findLockedByIdAndUser(siteId, site.getUser()).orElse(null);
                    if (site == null) return;
                    CrawlerSiteActivityRetention rule = policies.findBySiteId(siteId).orElse(null);
                    if (rule != null && Boolean.TRUE.equals(rule.getAutomatic())
                            && rule.getRetentionDays() > 0) {
                        activities.deleteExpired(site, now.minusDays(rule.getRetentionDays()));
                    }
                });
            } catch (RuntimeException error) {
                log.error("网站行为记录自动清理失败: siteId={}", siteId, error);
            }
        }
    }

    private CrawlerSite ownedSite(User user, Long siteId) {
        return sites.findByIdAndUser(siteId, user).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "网站不存在"));
    }

    private CrawlerSite lockedSite(User user, Long siteId) {
        return sites.findLockedByIdAndUser(siteId, user).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "网站不存在"));
    }

    private List<Long> checkedIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100
                || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 1 至 100 条记录");
        }
        return ids.stream().distinct().toList();
    }
}
