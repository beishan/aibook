package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.DiscoveryPagePayload;
import com.aibook.dto.crawler.CrawlerDtos.DiscoveryPageView;
import com.aibook.dto.crawler.CrawlerDtos.RuleSaveRequest;
import com.aibook.dto.crawler.CrawlerDtos.RuleVersionView;
import com.aibook.dto.crawler.CrawlerDtos.SiteConfigurationPayload;
import com.aibook.dto.crawler.CrawlerDtos.SitePayload;
import com.aibook.dto.crawler.CrawlerDtos.SiteView;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Imports and exports a complete user-configurable crawler site definition. */
@Service
@RequiredArgsConstructor
public class CrawlerSiteConfigurationService {

    public static final int SCHEMA_VERSION = 1;
    public static final String CONFIGURATION_TYPE = "AIBOOK_CRAWLER_SITE";

    private final CrawlerManagementService managementService;
    private final CrawlerDiscoveryPageService discoveryPageService;

    @Transactional
    public SiteConfigurationPayload exportConfiguration(User user, Long siteId) {
        CrawlerSite site = managementService.ownedSite(user, siteId);
        SiteView view = managementService.siteView(site);
        SitePayload sitePayload = new SitePayload(
                view.siteName(), view.siteCode(), view.baseUrl(), view.homeUrl(), view.enabled(),
                view.autoScan(), view.autoCrawl(), view.autoUpdate(), view.autoImportLibrary(),
                view.requestIntervalMillis(), view.randomDelayMillis(), view.maxConcurrency(),
                view.encoding(), view.proxies(), view.scanIntervalMinutes(),
                view.updateIntervalMinutes(), view.maxDiscoveryPages(), view.autoImportFormat(),
                view.contentMarkers());
        List<RuleSaveRequest> rules = managementService.rules(user, siteId).stream()
                .map(this::rulePayload).toList();
        List<DiscoveryPagePayload> discoveryPages = discoveryPageService.pages(user, siteId).stream()
                .map(this::discoveryPagePayload).toList();
        return new SiteConfigurationPayload(SCHEMA_VERSION, CONFIGURATION_TYPE,
                sitePayload, rules, discoveryPages);
    }

    @Transactional
    public SiteView importConfiguration(User user, SiteConfigurationPayload configuration) {
        if (configuration.schemaVersion() != SCHEMA_VERSION
                || !CONFIGURATION_TYPE.equals(configuration.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的网站配置 JSON 类型或版本");
        }
        long activeRules = values(configuration.rules()).stream()
                .filter(rule -> Boolean.TRUE.equals(rule.enabled())).count();
        if (activeRules > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "网站配置中最多只能有一条生效规则");
        }
        SiteView importedSite = managementService.createSite(user, configuration.site());
        for (RuleSaveRequest rule : values(configuration.rules())) {
            managementService.createRule(user, importedSite.id(), rule);
        }
        for (DiscoveryPagePayload page : values(configuration.discoveryPages())) {
            discoveryPageService.create(user, importedSite.id(), page);
        }
        return managementService.siteView(managementService.ownedSite(user, importedSite.id()));
    }

    private RuleSaveRequest rulePayload(RuleVersionView rule) {
        return new RuleSaveRequest(rule.version(), rule.changeSummary(), rule.rule(), rule.enabled());
    }

    private DiscoveryPagePayload discoveryPagePayload(DiscoveryPageView page) {
        return new DiscoveryPagePayload(page.pageName(), page.pageUrl(), page.autoScanEnabled(),
                page.scanIntervalMinutes(), page.maxPages());
    }

    private <T> List<T> values(List<T> values) {
        return values == null ? List.of() : values;
    }
}
