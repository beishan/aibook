package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.*;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CrawlerRuleHealthService {
    private final CrawlerSiteRepository siteRepository;
    private final CrawlerBookRepository bookRepository;
    private final CrawlerRuleTestService ruleTestService;
    private final CrawlerManagementService managementService;

    public RuleTestView check(User user, Long siteId) {
        return check(managementService.ownedSite(user, siteId));
    }

    private RuleTestView check(CrawlerSite site) {
        if (site.getRule() == null) {
            RuleTestView result = new RuleTestView(false, null, null, null, null, null, null, 0,
                    null, 0, null, 0, "当前没有生效的采集规则");
            site.setLastHealthCheckAt(LocalDateTime.now());
            site.setStatus(CrawlerSite.SiteStatus.PAUSED);
            site.setHealthMessage(result.errorMessage());
            siteRepository.save(site);
            return result;
        }
        CrawlerBook sample = bookRepository.findFirstBySiteOrderByLastCrawlTimeDesc(site).orElse(null);
        RuleTestView result;
        if (sample == null) {
            result = new RuleTestView(false, null, null, null, null, null, null, 0,
                    null, 0, null, 0, "暂无采集书籍，无法执行抽样检查");
            site.setLastHealthCheckAt(LocalDateTime.now());
            site.setHealthMessage("规则未检查：暂无采集书籍样本");
            siteRepository.save(site);
            return result;
        } else {
            result = ruleTestService.test(site, new RuleTestRequest(sample.getBookUrl(), null));
        }
        site.setLastHealthCheckAt(LocalDateTime.now());
        site.setStatus(result.success() ? CrawlerSite.SiteStatus.READY : CrawlerSite.SiteStatus.RULE_ERROR);
        site.setHealthMessage(result.success() ? "规则正常：详情、目录与正文抽样通过" : result.errorMessage());
        siteRepository.save(site);
        return result;
    }
}
