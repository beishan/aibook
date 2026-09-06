package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.RulePayload;
import com.aibook.dto.crawler.CrawlerDtos.SitePayload;
import com.aibook.dto.crawler.CrawlerDtos.ProxyPayload;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerChapter;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlerManagementServiceTest {
    private CrawlerSiteRepository sites;
    private CrawlerSiteRuleVersionRepository rules;
    private CrawlerBookRepository books;
    private CrawlerChapterRepository chapters;
    private CrawlerManagementService service;

    @BeforeEach
    void setUp() {
        sites = mock(CrawlerSiteRepository.class);
        rules = mock(CrawlerSiteRuleVersionRepository.class);
        books = mock(CrawlerBookRepository.class);
        chapters = mock(CrawlerChapterRepository.class);
        service = new CrawlerManagementService(sites, books,
                chapters, mock(CrawlerTaskRepository.class), rules,
                new ObjectMapper());
        when(sites.save(any(CrawlerSite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rules.save(any(CrawlerSiteRuleVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(books.save(any(CrawlerBook.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapters.save(any(CrawlerChapter.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsSiteWithoutCreatingOrRequiringRule() {
        User user = user();
        when(sites.findByUserAndSiteCode(user, "demo")).thenReturn(Optional.empty());

        var result = service.createSite(user, new SitePayload("示例站", "demo", "https://example.com/",
                null, false, false, false, true, false, 1500, 1000, 1, 15000, 2,
                "UTF-8", null, null, null, List.of(), 360, 30, 3, "EPUB"));

        assertThat(result.rule()).isNull();
        assertThat(result.ruleVersion()).isNull();
        assertThat(result.ruleCount()).isZero();
    }

    @Test
    void generatesReadableUniqueSiteCodeWhenOmitted() {
        User user = user();
        CrawlerSite occupied = CrawlerSite.builder().id(99L).user(user).siteCode("example_com").build();
        when(sites.findByUserAndSiteCode(user, "example_com")).thenReturn(Optional.of(occupied));
        when(sites.findByUserAndSiteCode(user, "example_com_2")).thenReturn(Optional.empty());

        var result = service.createSite(user, new SitePayload("示例站", " ", "https://www.example.com/books",
                null, false, false, false, true, false, 1500, 1000, 1, 15000, 2,
                "UTF-8", null, null, null, List.of(), 360, 30, 3, "EPUB"));

        assertThat(result.siteCode()).isEqualTo("example_com_2");
    }

    @Test
    void updatesExistingSiteWithoutTreatingItsCodeAsConflict() {
        User user = user();
        CrawlerSite existing = CrawlerSite.builder().id(7L).user(user).siteName("旧名称")
                .siteCode("example_com").baseUrl("https://example.com").build();
        when(sites.findByIdAndUser(7L, user)).thenReturn(Optional.of(existing));
        when(sites.findByUserAndSiteCode(user, "example_com")).thenReturn(Optional.of(existing));

        var result = service.updateSite(user, 7L, new SitePayload("新名称", "example_com", "https://example.com",
                null, true, false, false, true, false, 1500, 1000, 1, 15000, 2,
                "UTF-8", null, null, null, List.of(), 360, 30, 3, "EPUB"));

        assertThat(result.siteName()).isEqualTo("新名称");
        assertThat(result.siteCode()).isEqualTo("example_com");
    }

    @Test
    void storesMultipleProxiesButActivatesOnlySelectedProxy() {
        User user = user();
        when(sites.findByUserAndSiteCode(user, "proxy-demo")).thenReturn(Optional.empty());

        var result = service.createSite(user, new SitePayload("代理站", "proxy-demo", "https://example.com",
                null, false, false, false, true, false, 1500, 1000, 1, 15000, 2,
                "UTF-8", null, null, null, List.of(
                        new ProxyPayload("备用", "http://127.0.0.1:7890", false),
                        new ProxyPayload("当前", "http://192.168.1.2:8080", true)), 360, 30, 3, "EPUB"));

        assertThat(result.proxies()).hasSize(2);
        assertThat(result.proxy()).isEqualTo("http://192.168.1.2:8080");
    }

    @Test
    void rejectsMoreThanOneActiveProxy() {
        User user = user();
        when(sites.findByUserAndSiteCode(user, "proxy-conflict")).thenReturn(Optional.empty());
        SitePayload payload = new SitePayload("冲突站", "proxy-conflict", "https://example.com",
                null, false, false, false, true, false, 1500, 1000, 1, 15000, 2,
                "UTF-8", null, null, null, List.of(
                        new ProxyPayload("代理一", "http://127.0.0.1:7890", true),
                        new ProxyPayload("代理二", "http://127.0.0.1:8080", true)), 360, 30, 3, "EPUB");

        assertThatThrownBy(() -> service.createSite(user, payload))
                .hasMessageContaining("只能启用一组代理");
    }

    @Test
    void treatsStoredChapterContentAsSuccessfullyParsed() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(15L).site(site).bookName("示例书籍")
                .externalBookId("book-1").bookUrl("https://example.com/book/1").build();
        CrawlerChapter waiting = CrawlerChapter.builder().id(21L).crawlerBook(book).chapterIndex(0)
                .externalChapterId("1").chapterName("第一章").chapterUrl("https://example.com/1")
                .content("已经成功解析的第一章正文").crawlStatus(CrawlerChapter.CrawlStatus.WAITING).build();
        CrawlerChapter failed = CrawlerChapter.builder().id(22L).crawlerBook(book).chapterIndex(1)
                .externalChapterId("2").chapterName("第二章").chapterUrl("https://example.com/2")
                .content("已经成功解析的第二章正文").crawlStatus(CrawlerChapter.CrawlStatus.FAILED).build();
        when(books.findByIdAndSiteUser(15L, user)).thenReturn(Optional.of(book));
        when(chapters.findByCrawlerBookOrderByChapterIndexAsc(book)).thenReturn(List.of(waiting, failed));
        when(chapters.countByCrawlerBook(book)).thenReturn(2L);
        when(chapters.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.COMPLETED)).thenReturn(2L);
        when(chapters.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.FAILED)).thenReturn(0L);
        when(chapters.countByCrawlerBookAndCrawlStatus(book, CrawlerChapter.CrawlStatus.CONTENT_SUSPECTED)).thenReturn(0L);

        var result = service.chapters(user, 15L);

        assertThat(result).extracting(item -> item.crawlStatus()).containsOnly("COMPLETED");
        assertThat(book.getCrawlStatus()).isEqualTo(CrawlerBook.CrawlStatus.COMPLETED);
        assertThat(book.getCrawledChapterCount()).isEqualTo(2);
        assertThat(book.getFailedChapterCount()).isZero();
        assertThat(book.getImportStatus()).isEqualTo(CrawlerBook.ImportStatus.READY);
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
