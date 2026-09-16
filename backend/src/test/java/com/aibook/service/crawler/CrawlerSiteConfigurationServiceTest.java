package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.crawler.CrawlerDtos.DiscoveryPagePayload;
import com.aibook.dto.crawler.CrawlerDtos.DiscoveryPageView;
import com.aibook.dto.crawler.CrawlerDtos.RulePayload;
import com.aibook.dto.crawler.CrawlerDtos.RuleSaveRequest;
import com.aibook.dto.crawler.CrawlerDtos.RuleVersionView;
import com.aibook.dto.crawler.CrawlerDtos.SiteConfigurationPayload;
import com.aibook.dto.crawler.CrawlerDtos.SitePayload;
import com.aibook.dto.crawler.CrawlerDtos.SiteView;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrawlerSiteConfigurationServiceTest {

    @Test
    void exportsSiteRulesAndDiscoveryPages() {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).build();
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        CrawlerDiscoveryPageService discoveryPages = mock(CrawlerDiscoveryPageService.class);
        SiteView siteView = new SiteView(7L, "示例站", "demo", "https://example.com", null,
                true, false, false, false, false, 1000, 0, 1, "UTF-8", null,
                List.of(), 360, 30, 3, "EPUB", "READY", 0, null, null, null, 1,
                null, null, null, null, null, List.of(), false, null);
        RulePayload rulePayload = rulePayload();
        when(management.ownedSite(user, 7L)).thenReturn(site);
        when(management.siteView(site)).thenReturn(siteView);
        when(management.rules(user, 7L)).thenReturn(List.of(new RuleVersionView(
                3L, 2, "当前规则", true, rulePayload, null, null)));
        when(discoveryPages.pages(user, 7L)).thenReturn(List.of(new DiscoveryPageView(
                5L, 7L, "热门", "https://example.com/hot", true,
                360, 50, null, null)));
        CrawlerSiteConfigurationService service =
                new CrawlerSiteConfigurationService(management, discoveryPages);

        SiteConfigurationPayload result = service.exportConfiguration(user, 7L);

        assertThat(result.schemaVersion()).isEqualTo(1);
        assertThat(result.type()).isEqualTo("AIBOOK_CRAWLER_SITE");
        assertThat(result.site().siteCode()).isEqualTo("demo");
        assertThat(result.site().respectRobotsTxt()).isFalse();
        assertThat(result.rules()).singleElement().satisfies(rule -> {
            assertThat(rule.version()).isEqualTo(2);
            assertThat(rule.enabled()).isTrue();
        });
        assertThat(result.discoveryPages()).singleElement()
                .extracting(DiscoveryPagePayload::pageName).isEqualTo("热门");
    }

    @Test
    void importsSiteBeforeRulesAndDiscoveryPages() {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        CrawlerDiscoveryPageService discoveryPages = mock(CrawlerDiscoveryPageService.class);
        SitePayload sitePayload = new SitePayload("导入站", "imported", "https://example.com",
                null, true, false, false, false, false, 1000, 0, 1, "UTF-8", List.of(),
                360, 30, 3, "EPUB", List.of(), false);
        RuleSaveRequest rule = new RuleSaveRequest(1, "初始规则", rulePayload(), true);
        DiscoveryPagePayload page = new DiscoveryPagePayload(
                "热门", "https://example.com/hot", false, 360, 50);
        SiteView created = new SiteView(9L, "导入站", "imported", "https://example.com", null,
                true, false, false, false, false, 1000, 0, 1, "UTF-8", null,
                List.of(), 360, 30, 3, "EPUB", "READY", 0, null, null, null, 1,
                null, null, null, null, null, List.of(), false, null);
        CrawlerSite importedEntity = CrawlerSite.builder().id(9L).user(user).build();
        when(management.createSite(user, sitePayload)).thenReturn(created);
        when(management.ownedSite(user, 9L)).thenReturn(importedEntity);
        when(management.siteView(importedEntity)).thenReturn(created);
        SiteConfigurationPayload configuration = new SiteConfigurationPayload(
                1, "AIBOOK_CRAWLER_SITE", sitePayload, List.of(rule), List.of(page));
        CrawlerSiteConfigurationService service =
                new CrawlerSiteConfigurationService(management, discoveryPages);

        SiteView result = service.importConfiguration(user, configuration);

        assertThat(result).isSameAs(created);
        verify(management).createRule(user, 9L, rule);
        verify(discoveryPages).create(user, 9L, page);
    }

    private RulePayload rulePayload() {
        return new RulePayload("h1", null, null, null, null, null, null, null, null,
                ".chapters a", null, ":scope", null, "#content", null, null, 100,
                null, null, null, null, null, null, null, null, null, null,
                true, false);
    }
}
