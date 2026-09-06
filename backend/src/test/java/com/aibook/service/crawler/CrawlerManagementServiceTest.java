package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.RulePayload;
import com.aibook.dto.crawler.CrawlerDtos.SitePayload;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerSiteRuleVersion;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.repository.CrawlerSiteRepository;
import com.aibook.repository.CrawlerSiteRuleVersionRepository;
import com.aibook.repository.CrawlerTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlerManagementServiceTest {
    private CrawlerSiteRepository sites;
    private CrawlerSiteRuleVersionRepository rules;
    private CrawlerManagementService service;

    @BeforeEach
    void setUp() {
        sites = mock(CrawlerSiteRepository.class);
        rules = mock(CrawlerSiteRuleVersionRepository.class);
        service = new CrawlerManagementService(sites, mock(CrawlerBookRepository.class),
                mock(CrawlerChapterRepository.class), mock(CrawlerTaskRepository.class), rules,
                new ObjectMapper());
        when(sites.save(any(CrawlerSite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rules.save(any(CrawlerSiteRuleVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsSiteWithoutCreatingOrRequiringRule() {
        User user = user();
        when(sites.findByUserAndSiteCode(user, "demo")).thenReturn(Optional.empty());

        var result = service.createSite(user, new SitePayload("示例站", "demo", "https://example.com/",
                null, false, false, false, true, false, 1500, 1000, 1, 15000, 2,
                "UTF-8", null, null, null, null, 360, 30, 3, "EPUB"));

        assertThat(result.rule()).isNull();
        assertThat(result.ruleVersion()).isNull();
        assertThat(result.ruleCount()).isZero();
    }

    @Test
    void enablingVersionDisablesEveryOtherVersionAndReplacesRuntimeRule() throws Exception {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("示例站").siteCode("demo")
                .baseUrl("https://example.com").build();
        RulePayload firstPayload = rule("h1.old", "#old");
        RulePayload secondPayload = rule("h1.new", "#new");
        CrawlerSiteRuleVersion first = CrawlerSiteRuleVersion.builder().id(11L).site(site).version(1)
                .changeSummary("旧版").configJson(new ObjectMapper().writeValueAsString(firstPayload)).enabled(true).build();
        CrawlerSiteRuleVersion second = CrawlerSiteRuleVersion.builder().id(12L).site(site).version(2)
                .changeSummary("新版").configJson(new ObjectMapper().writeValueAsString(secondPayload)).enabled(false).build();
        when(sites.findLockedByIdAndUser(7L, user)).thenReturn(Optional.of(site));
        when(rules.findByIdAndSite(12L, site)).thenReturn(Optional.of(second));
        when(rules.findBySiteOrderByVersionDesc(site)).thenReturn(List.of(second, first));

        service.setRuleStatus(user, 7L, 12L, true);

        assertThat(first.getEnabled()).isFalse();
        assertThat(second.getEnabled()).isTrue();
        assertThat(site.getRule().getRuleVersion()).isEqualTo(2);
        assertThat(site.getRule().getTitleSelector()).isEqualTo("h1.new");
        assertThat(site.getRule().getContentSelector()).isEqualTo("#new");
    }

    private RulePayload rule(String title, String content) {
        return new RulePayload(title, ".author", null, "#intro", null, null, null, null,
                "#list dd", ":scope", "a", "h1", content, ".ads", null, 100,
                ".books article", "a", ".title", null, null, null, null, "a.next",
                null, null, true, false);
    }

    private User user() {
        return User.builder().id(1L).username("owner").role(User.Role.ADMIN).enabled(true).build();
    }
}
