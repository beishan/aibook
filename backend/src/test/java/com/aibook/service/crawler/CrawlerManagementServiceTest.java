package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.RulePayload;
import com.aibook.dto.crawler.CrawlerDtos.SitePayload;
import com.aibook.dto.crawler.CrawlerDtos.ProxyPayload;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerSiteRuleVersion;
import com.aibook.model.entity.CrawlerTaskLog;
import com.aibook.model.entity.CrawlerTask;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.repository.CrawlerSiteRepository;
import com.aibook.repository.CrawlerSiteRuleVersionRepository;
import com.aibook.repository.CrawlerTaskRepository;
import com.aibook.repository.CrawlerTaskLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrawlerManagementServiceTest {
    private CrawlerSiteRepository sites;
    private CrawlerSiteRuleVersionRepository rules;
    private CrawlerBookRepository books;
    private CrawlerChapterRepository chapters;
    private CrawlerTaskLogRepository crawlerLogs;
    private CrawlerTaskRepository tasks;
    private CrawlerManagementService service;

    @BeforeEach
    void setUp() {
        sites = mock(CrawlerSiteRepository.class);
        rules = mock(CrawlerSiteRuleVersionRepository.class);
        books = mock(CrawlerBookRepository.class);
        chapters = mock(CrawlerChapterRepository.class);
        crawlerLogs = mock(CrawlerTaskLogRepository.class);
        tasks = mock(CrawlerTaskRepository.class);
        service = new CrawlerManagementService(sites, books,
                chapters, tasks, crawlerLogs, rules,
                mock(com.aibook.repository.CrawlerDiscoveryPageRepository.class),
                mock(com.aibook.repository.CrawlerScanResultRepository.class),
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
                null, false, false, false, true, false, 1500, 1000, 1,
                "UTF-8", List.of(), 360, 30, 3, "EPUB", List.of()));

        assertThat(result.rule()).isNull();
        assertThat(result.ruleVersion()).isNull();
        assertThat(result.ruleCount()).isZero();
        assertThat(result.autoScan()).isFalse();
        assertThat(result.autoCrawl()).isFalse();
        assertThat(result.autoUpdate()).isFalse();
        assertThat(result.autoImportLibrary()).isFalse();
    }

    @Test
    void returnsCrawlerSpecificLogsForTheOwnedBook() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(3L).site(site).bookName("示例书").build();
        CrawlerTaskLog log = CrawlerTaskLog.builder().id(9L).user(user).crawlerBookId(3L)
                .taskId("task-1").description("章节采集完毕：示例书")
                .details("章节：第一章；预览：正文").createdAt(LocalDateTime.now()).build();
        when(books.findByIdAndSiteUser(3L, user)).thenReturn(Optional.of(book));
        when(crawlerLogs.findByUserAndCrawlerBookIdOrderByCreatedAtDescIdDesc(
                eq(user), eq(3L), any(Pageable.class))).thenReturn(List.of(log));

        var result = service.logs(user, 3L, 100);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().description()).contains("章节采集完毕");
        assertThat(result.getFirst().details()).contains("第一章");
    }

    @Test
    void generatesReadableUniqueSiteCodeWhenOmitted() {
        User user = user();
        CrawlerSite occupied = CrawlerSite.builder().id(99L).user(user).siteCode("example_com").build();
        when(sites.findByUserAndSiteCode(user, "example_com")).thenReturn(Optional.of(occupied));
        when(sites.findByUserAndSiteCode(user, "example_com_2")).thenReturn(Optional.empty());

        var result = service.createSite(user, new SitePayload("示例站", " ", "https://www.example.com/books",
                null, false, false, false, true, false, 1500, 1000, 1,
                "UTF-8", List.of(), 360, 30, 3, "EPUB", List.of()));

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
                null, true, false, false, true, false, 1500, 1000, 1,
                "UTF-8", List.of(), 360, 30, 3, "EPUB", List.of()));

        assertThat(result.siteName()).isEqualTo("新名称");
        assertThat(result.siteCode()).isEqualTo("example_com");
    }

    @Test
    void storesMultipleProxiesButActivatesOnlySelectedProxy() {
        User user = user();
        when(sites.findByUserAndSiteCode(user, "proxy-demo")).thenReturn(Optional.empty());

        var result = service.createSite(user, new SitePayload("代理站", "proxy-demo", "https://example.com",
                null, false, false, false, true, false, 1500, 1000, 1,
                "UTF-8", List.of(
                        new ProxyPayload("备用", "http://127.0.0.1:7890", false),
                        new ProxyPayload("当前", "http://192.168.1.2:8080", true)), 360, 30, 3, "EPUB", List.of()));

        assertThat(result.proxies()).hasSize(2);
        assertThat(result.proxy()).isEqualTo("http://192.168.1.2:8080");
    }

    @Test
    void rejectsMoreThanOneActiveProxy() {
        User user = user();
        when(sites.findByUserAndSiteCode(user, "proxy-conflict")).thenReturn(Optional.empty());
        SitePayload payload = new SitePayload("冲突站", "proxy-conflict", "https://example.com",
                null, false, false, false, true, false, 1500, 1000, 1,
                "UTF-8", List.of(
                        new ProxyPayload("代理一", "http://127.0.0.1:7890", true),
                        new ProxyPayload("代理二", "http://127.0.0.1:8080", true)), 360, 30, 3, "EPUB", List.of());

        assertThatThrownBy(() -> service.createSite(user, payload))
                .hasMessageContaining("只能启用一组代理");
    }

    @Test
    void storesNormalizedDistinctContentFailureMarkers() {
        User user = user();
        when(sites.findByUserAndSiteCode(user, "marker-demo")).thenReturn(Optional.empty());

        var result = service.createSite(user, new SitePayload("特征站", "marker-demo", "https://example.com",
                null, false, false, false, true, false, 1500, 1000, 1,
                "UTF-8", List.of(), 360, 30, 3, "EPUB",
                List.of(" VIP 专属 ", "VIP 专属", "请登录后阅读")));

        assertThat(result.contentFailureMarkers()).containsExactly("VIP 专属", "请登录后阅读");
    }

    @Test
    void treatsStoredChapterContentAsSuccessfullyParsed() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(15L).site(site).bookName("示例书籍")
                .externalBookId("book-1").bookUrl("https://example.com/book/1").build();
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 6, 10, 30);
        CrawlerChapter waiting = CrawlerChapter.builder().id(21L).crawlerBook(book).chapterIndex(0)
                .externalChapterId("1").chapterName("第一章").chapterUrl("https://example.com/1")
                .content("已经成功解析的第一章正文").crawlStatus(CrawlerChapter.CrawlStatus.WAITING)
                .createdAt(createdAt).build();
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
        assertThat(result.getFirst().createdAt()).isEqualTo(createdAt);
        assertThat(book.getCrawlStatus()).isEqualTo(CrawlerBook.CrawlStatus.COMPLETED);
        assertThat(book.getCrawledChapterCount()).isEqualTo(2);
        assertThat(book.getFailedChapterCount()).isZero();
        assertThat(book.getImportStatus()).isEqualTo(CrawlerBook.ImportStatus.READY);
    }

    @Test
    void pagesAndSortsBookChapters() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(15L).site(site).bookName("示例书籍")
                .externalBookId("book-1").bookUrl("https://example.com/book/1").build();
        CrawlerChapter chapter = CrawlerChapter.builder().id(22L).crawlerBook(book).chapterIndex(8)
                .externalChapterId("9").chapterName("第九章").chapterUrl("https://example.com/9")
                .crawlStatus(CrawlerChapter.CrawlStatus.COMPLETED).build();
        when(books.findByIdAndSiteUser(15L, user)).thenReturn(Optional.of(book));
        when(chapters.findByCrawlerBook(eq(book), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(chapter), invocation.getArgument(1), 61));

        var result = service.chapters(user, 15L, 2, 20, "INDEX_DESC");

        assertThat(result.getTotalElements()).isEqualTo(61);
        assertThat(result.getContent()).extracting(item -> item.chapterName()).containsExactly("第九章");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(chapters).findByCrawlerBook(eq(book), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("chapterIndex").isDescending()).isTrue();
    }

    @Test
    void locatesCurrentCrawlingChapterAndItsPage() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(15L).site(site).bookName("示例书籍")
                .externalBookId("book-1").bookUrl("https://example.com/book/1").build();
        CrawlerChapter chapter = CrawlerChapter.builder().id(88L).crawlerBook(book).chapterIndex(43)
                .externalChapterId("44").chapterName("第四十四章").chapterUrl("https://example.com/44")
                .crawlStatus(CrawlerChapter.CrawlStatus.CRAWLING).build();
        when(books.findByIdAndSiteUser(15L, user)).thenReturn(Optional.of(book));
        when(chapters.findFirstByCrawlerBookAndCrawlStatusOrderByUpdatedAtDesc(
                book, CrawlerChapter.CrawlStatus.CRAWLING)).thenReturn(Optional.of(chapter));
        when(chapters.countByCrawlerBookAndChapterIndexLessThan(book, 43)).thenReturn(43L);

        var result = service.currentChapter(user, 15L, 20);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().page()).isEqualTo(2);
        assertThat(result.orElseThrow().chapter().chapterName()).isEqualTo("第四十四章");
    }

    @Test
    void fallsBackToRunningTaskWhenCrawlingStatusChangesBetweenPolls() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(15L).site(site).bookName("示例书籍")
                .externalBookId("book-1").bookUrl("https://example.com/book/1").build();
        CrawlerChapter chapter = CrawlerChapter.builder().id(25L).crawlerBook(book).chapterIndex(24)
                .externalChapterId("25").chapterName("第二十五章").chapterUrl("https://example.com/25")
                .crawlStatus(CrawlerChapter.CrawlStatus.COMPLETED).build();
        CrawlerTask task = CrawlerTask.builder().id("task-1").user(user).site(site).crawlerBook(book)
                .status(CrawlerTask.TaskStatus.RUNNING).currentChapter("第二十五章").build();
        when(books.findByIdAndSiteUser(15L, user)).thenReturn(Optional.of(book));
        when(chapters.findFirstByCrawlerBookAndCrawlStatusOrderByUpdatedAtDesc(
                book, CrawlerChapter.CrawlStatus.CRAWLING)).thenReturn(Optional.empty());
        when(tasks.findFirstByCrawlerBookAndStatusOrderByUpdatedAtDesc(
                book, CrawlerTask.TaskStatus.RUNNING)).thenReturn(Optional.of(task));
        when(chapters.findFirstByCrawlerBookAndChapterNameOrderByChapterIndexAsc(
                book, "第二十五章")).thenReturn(Optional.of(chapter));
        when(chapters.countByCrawlerBookAndChapterIndexLessThan(book, 24)).thenReturn(24L);

        var result = service.currentChapter(user, 15L, 20);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().page()).isEqualTo(1);
        assertThat(result.orElseThrow().chapter().id()).isEqualTo(25L);
    }

    @Test
    void filtersSearchesAndSortsActiveDiscoveredBooks() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(15L).site(site).bookName("新发现书籍")
                .externalBookId("book-1").bookUrl("https://example.com/book/1")
                .discoveryStatus(CrawlerBook.DiscoveryStatus.ACTIVE)
                .crawlStatus(CrawlerBook.CrawlStatus.DISCOVERED).build();
        when(books.searchDiscoveredBooks(eq(user), eq(CrawlerBook.DiscoveryStatus.ACTIVE),
                eq(CrawlerBook.CrawlStatus.DISCOVERED), eq("三体"), eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book)));

        var result = service.discoveredBooks(user, 2, 50, "  三体  ", 7L, "BOOK_NAME_ASC");

        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(books).searchDiscoveredBooks(eq(user), eq(CrawlerBook.DiscoveryStatus.ACTIVE),
                eq(CrawlerBook.CrawlStatus.DISCOVERED), eq("三体"), eq(7L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("bookName").isAscending()).isTrue();
    }

    @Test
    void bindsBlankDiscoveryKeywordAsTextInsteadOfNull() {
        User user = user();
        when(books.searchDiscoveredBooks(eq(user), eq(CrawlerBook.DiscoveryStatus.ACTIVE),
                eq(CrawlerBook.CrawlStatus.DISCOVERED), eq(""), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.discoveredBooks(user, 0, 20, null, null, "DISCOVER_TIME_DESC");

        assertThat(result).isEmpty();
        verify(books).searchDiscoveredBooks(eq(user), eq(CrawlerBook.DiscoveryStatus.ACTIVE),
                eq(CrawlerBook.CrawlStatus.DISCOVERED), eq(""), isNull(), any(Pageable.class));
    }

    @Test
    void paginatesAndSearchesManagedBooksOnly() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(15L).site(site).bookName("三体")
                .externalBookId("book-1").bookUrl("https://example.com/book/1")
                .discoveryStatus(CrawlerBook.DiscoveryStatus.ACTIVE)
                .crawlStatus(CrawlerBook.CrawlStatus.COMPLETED)
                .importStatus(CrawlerBook.ImportStatus.READY).build();
        when(books.searchManagedBooks(eq(user), eq(CrawlerBook.DiscoveryStatus.ACTIVE),
                eq(CrawlerBook.CrawlStatus.DISCOVERED), eq("三体"), eq(7L),
                eq(CrawlerBook.CrawlStatus.COMPLETED), eq(CrawlerBook.ImportStatus.READY),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book)));

        var result = service.books(user, 2, 50, "  三体  ", 7L,
                "completed", "ready", "CRAWL_STARTED_DESC");

        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(books).searchManagedBooks(eq(user), eq(CrawlerBook.DiscoveryStatus.ACTIVE),
                eq(CrawlerBook.CrawlStatus.DISCOVERED), eq("三体"), eq(7L),
                eq(CrawlerBook.CrawlStatus.COMPLETED), eq(CrawlerBook.ImportStatus.READY),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("lastCrawlStartedAt").isDescending())
                .isTrue();
    }

    @Test
    void rejectsInvalidManagedBookFilterStatus() {
        assertThatThrownBy(() -> service.books(user(), 0, 20, null, null,
                "unknown", null, "CREATED_DESC"))
                .hasMessageContaining("采集状态无效");
    }

    @Test
    void paginatesFailedTasksWithServerSideStatusFilter() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("示例站").build();
        CrawlerTask task = CrawlerTask.builder().id("task-1").user(user).site(site)
                .type(CrawlerTask.TaskType.BOOK_CONTENT).status(CrawlerTask.TaskStatus.FAILED)
                .priority(CrawlerTask.Priority.NORMAL).build();
        when(tasks.findByUserAndStatusInOrderByCreatedAtDesc(eq(user), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        var result = service.tasks(user, 1, 25, true);

        assertThat(result.getContent()).extracting(item -> item.id()).containsExactly("task-1");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tasks).findByUserAndStatusInOrderByCreatedAtDesc(eq(user),
                eq(List.of(CrawlerTask.TaskStatus.FAILED, CrawlerTask.TaskStatus.PARTIAL_SUCCESS)),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
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
