package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.*;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CrawlerManagementService {
    private final CrawlerSiteRepository siteRepository;
    private final CrawlerBookRepository bookRepository;
    private final CrawlerChapterRepository chapterRepository;
    private final CrawlerTaskRepository taskRepository;
    private final CrawlerSiteRuleVersionRepository ruleVersionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<SiteView> sites(User user) { return siteRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::siteView).toList(); }

    @Transactional
    public SiteView createSite(User user, SitePayload payload) {
        validateBaseUrl(payload.baseUrl());
        if (siteRepository.findByUserAndSiteCode(user, payload.siteCode()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "网站编码已存在");
        CrawlerSite site = CrawlerSite.builder().user(user).build();
        apply(site, payload);
        site = siteRepository.save(site);
        snapshot(site, "创建规则");
        return siteView(site);
    }

    @Transactional
    public SiteView updateSite(User user, Long id, SitePayload payload) {
        CrawlerSite site = ownedSite(user, id);
        validateBaseUrl(payload.baseUrl());
        siteRepository.findByUserAndSiteCode(user, payload.siteCode())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "网站编码已存在"); });
        if (!ruleVersionRepository.existsBySite(site)) snapshot(site, "历史规则回填");
        String before = ruleJson(site.getRule());
        apply(site, payload);
        site = siteRepository.save(site);
        if (!before.equals(ruleJson(site.getRule()))) {
            site.getRule().setRuleVersion(value(site.getRule().getRuleVersion(), 1) + 1);
            site = siteRepository.save(site);
            snapshot(site, "更新规则");
        }
        return siteView(site);
    }

    @Transactional
    public void deleteSite(User user, Long id) {
        CrawlerSite site = ownedSite(user, id);
        if (bookRepository.existsBySite(site))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该网站已有采集数据，不能删除；可先禁用网站");
        ruleVersionRepository.deleteBySite(site);
        siteRepository.delete(site);
    }

    public CrawlerSite ownedSite(User user, Long id) {
        return siteRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集网站不存在"));
    }

    public CrawlerBook ownedBook(User user, Long id) {
        return bookRepository.findByIdAndSiteUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集书籍不存在"));
    }

    public CrawlerTask ownedTask(User user, String id) {
        return taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集任务不存在"));
    }

    @Transactional(readOnly = true)
    public List<RuleVersionView> ruleVersions(User user, Long siteId) {
        CrawlerSite site = ownedSite(user, siteId);
        return ruleVersionRepository.findBySiteOrderByVersionDesc(site).stream().map(this::ruleVersionView).toList();
    }

    @Transactional
    public SiteView restoreRuleVersion(User user, Long siteId, Long versionId) {
        CrawlerSite site = ownedSite(user, siteId);
        CrawlerSiteRuleVersion version = ruleVersionRepository.findByIdAndSite(versionId, site)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则版本不存在"));
        try {
            RulePayload payload = objectMapper.readValue(version.getConfigJson(), RulePayload.class);
            applyRule(site, payload);
            site.getRule().setRuleVersion(value(site.getRule().getRuleVersion(), 1) + 1);
            site = siteRepository.save(site);
            snapshot(site, "恢复自版本 " + version.getVersion());
            return siteView(site);
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "恢复规则版本失败", exception); }
    }

    @Transactional(readOnly = true)
    public Page<BookView> books(User user, int page, int size) {
        return bookRepository.findBySiteUser(user, PageRequest.of(page, Math.min(Math.max(size, 1), 100), Sort.by("createdAt").descending())).map(this::bookView);
    }

    @Transactional(readOnly = true)
    public BookView book(User user, Long id) { return bookView(ownedBook(user, id)); }

    @Transactional(readOnly = true)
    public List<ChapterView> chapters(User user, Long id) {
        return chapterRepository.findByCrawlerBookOrderByChapterIndexAsc(ownedBook(user, id)).stream().map(this::chapterView).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskView> tasks(User user, int limit) {
        return taskRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, Math.min(Math.max(limit, 1), 100))).stream().map(this::taskView).toList();
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(User user) {
        List<CrawlerSite> sites = siteRepository.findByUserOrderByCreatedAtDesc(user);
        LocalDateTime today = LocalDate.now().atStartOfDay();
        return new DashboardView(sites.size(), sites.stream().filter(s -> Boolean.TRUE.equals(s.getEnabled())).count(),
                bookRepository.countBySiteUser(user),
                bookRepository.countBySiteUserAndCrawlStatus(user, CrawlerBook.CrawlStatus.COMPLETED),
                bookRepository.countBySiteUserAndCrawlStatus(user, CrawlerBook.CrawlStatus.CRAWLING_CONTENT),
                bookRepository.countBySiteUserAndCrawlStatus(user, CrawlerBook.CrawlStatus.FAILED),
                bookRepository.countCreatedSince(user, today), chapterRepository.countByCrawlerBookSiteUserAndCreatedAtAfter(user, today),
                bookRepository.countBySiteUserAndImportStatus(user, CrawlerBook.ImportStatus.READY),
                bookRepository.countBySiteUserAndImportStatus(user, CrawlerBook.ImportStatus.IMPORTED), tasks(user, 8));
    }

    private void apply(CrawlerSite site, SitePayload p) {
        site.setSiteName(p.siteName().trim()); site.setSiteCode(p.siteCode().trim()); site.setBaseUrl(trimSlash(p.baseUrl()));
        site.setHomeUrl(blank(p.homeUrl()) ? trimSlash(p.baseUrl()) : p.homeUrl().trim());
        site.setEnabled(bool(p.enabled(), false)); site.setAutoScan(bool(p.autoScan(), false));
        site.setAutoCrawl(bool(p.autoCrawl(), false)); site.setAutoUpdate(bool(p.autoUpdate(), true));
        site.setAutoImportLibrary(bool(p.autoImportLibrary(), false));
        site.setScanIntervalMinutes(value(p.scanIntervalMinutes(), 360));
        site.setUpdateIntervalMinutes(value(p.updateIntervalMinutes(), 30));
        site.setMaxDiscoveryPages(value(p.maxDiscoveryPages(), 3));
        site.setAutoImportFormat(blank(p.autoImportFormat()) ? "EPUB" : p.autoImportFormat().toUpperCase(Locale.ROOT));
        site.setRequestIntervalMillis(value(p.requestIntervalMillis(), 1500)); site.setRandomDelayMillis(value(p.randomDelayMillis(), 1000));
        site.setMaxConcurrency(value(p.maxConcurrency(), 1)); site.setTimeoutMillis(value(p.timeoutMillis(), 15000));
        site.setRetryCount(value(p.retryCount(), 2)); site.setEncoding(blank(p.encoding()) ? "UTF-8" : p.encoding());
        site.setUserAgent(p.userAgent()); site.setCookie(p.cookie()); site.setHeadersJson(p.headersJson()); site.setProxy(p.proxy());
        applyRule(site, p.rule());
    }

    public void applyRule(CrawlerSite site, RulePayload r) {
        CrawlerSiteRule rule = site.getRule() == null ? new CrawlerSiteRule() : site.getRule();
        rule.setTitleSelector(r.titleSelector()); rule.setAuthorSelector(r.authorSelector()); rule.setCoverSelector(r.coverSelector());
        rule.setDescriptionSelector(r.descriptionSelector()); rule.setCategorySelector(r.categorySelector()); rule.setStatusSelector(r.statusSelector());
        rule.setLatestChapterSelector(r.latestChapterSelector()); rule.setChapterListUrlSelector(r.chapterListUrlSelector());
        rule.setChapterItemSelector(r.chapterItemSelector()); rule.setChapterTitleSelector(r.chapterTitleSelector());
        rule.setChapterUrlSelector(r.chapterUrlSelector()); rule.setContentTitleSelector(r.contentTitleSelector());
        rule.setContentSelector(r.contentSelector()); rule.setRemoveSelectors(r.removeSelectors());
        rule.setRegexReplacementsJson(r.regexReplacementsJson()); rule.setMinChapterLength(value(r.minChapterLength(), 100));
        rule.setDiscoveryItemSelector(r.discoveryItemSelector()); rule.setDiscoveryUrlSelector(r.discoveryUrlSelector());
        rule.setDiscoveryTitleSelector(r.discoveryTitleSelector()); rule.setDiscoveryAuthorSelector(r.discoveryAuthorSelector());
        rule.setDiscoveryCoverSelector(r.discoveryCoverSelector()); rule.setDiscoveryCategorySelector(r.discoveryCategorySelector());
        rule.setDiscoveryLatestChapterSelector(r.discoveryLatestChapterSelector()); rule.setDiscoveryNextPageSelector(r.discoveryNextPageSelector());
        rule.setXpathRemoveSelectors(r.xpathRemoveSelectors()); rule.setStringReplacementsJson(r.stringReplacementsJson());
        rule.setRemoveBlankLines(bool(r.removeBlankLines(), true)); rule.setSaveOriginalHtml(bool(r.saveOriginalHtml(), false));
        site.attachRule(rule);
    }

    public SiteView siteView(CrawlerSite s) {
        CrawlerSiteRule r = s.getRule();
        RulePayload rv = rulePayload(r);
        return new SiteView(s.getId(), s.getSiteName(), s.getSiteCode(), s.getBaseUrl(), s.getHomeUrl(), bool(s.getEnabled(), false),
                bool(s.getAutoScan(), false), bool(s.getAutoCrawl(), false), bool(s.getAutoUpdate(), true), bool(s.getAutoImportLibrary(), false),
                value(s.getRequestIntervalMillis(), 1500), value(s.getRandomDelayMillis(), 1000), value(s.getMaxConcurrency(), 1),
                value(s.getTimeoutMillis(), 15000), value(s.getRetryCount(), 2), s.getEncoding(), s.getUserAgent(),
                s.getCookie(), s.getHeadersJson(), s.getProxy(), value(s.getScanIntervalMinutes(), 360),
                value(s.getUpdateIntervalMinutes(), 30), value(s.getMaxDiscoveryPages(), 3),
                blank(s.getAutoImportFormat()) ? "EPUB" : s.getAutoImportFormat(), s.getStatus().name(),
                bookRepository.countBySite(s), rv, value(r.getRuleVersion(), 1), s.getLastScanAt(), s.getLastUpdateAt(),
                s.getLastHealthCheckAt(), s.getHealthMessage(), s.getCreatedAt());
    }

    public RulePayload rulePayload(CrawlerSiteRule r) {
        return new RulePayload(r.getTitleSelector(), r.getAuthorSelector(), r.getCoverSelector(), r.getDescriptionSelector(),
                r.getCategorySelector(), r.getStatusSelector(), r.getLatestChapterSelector(), r.getChapterListUrlSelector(),
                r.getChapterItemSelector(), r.getChapterTitleSelector(), r.getChapterUrlSelector(), r.getContentTitleSelector(),
                r.getContentSelector(), r.getRemoveSelectors(), r.getRegexReplacementsJson(), r.getMinChapterLength(),
                r.getDiscoveryItemSelector(), r.getDiscoveryUrlSelector(), r.getDiscoveryTitleSelector(), r.getDiscoveryAuthorSelector(),
                r.getDiscoveryCoverSelector(), r.getDiscoveryCategorySelector(), r.getDiscoveryLatestChapterSelector(), r.getDiscoveryNextPageSelector(),
                r.getXpathRemoveSelectors(), r.getStringReplacementsJson(), bool(r.getRemoveBlankLines(), true), bool(r.getSaveOriginalHtml(), false));
    }

    public BookView bookView(CrawlerBook b) { return new BookView(b.getId(), b.getSite().getId(), b.getSite().getSiteName(), b.getExternalBookId(), b.getBookUrl(), b.getBookName(), b.getAuthor(), b.getCoverUrl(), b.getDescription(), b.getCategory(), b.getBookStatus(), b.getLatestChapter(), value(b.getChapterCount(), 0), value(b.getCrawledChapterCount(), 0), value(b.getFailedChapterCount(), 0), b.getCrawlStatus().name(), (b.getDiscoveryStatus() == null ? CrawlerBook.DiscoveryStatus.ACTIVE : b.getDiscoveryStatus()).name(), b.getImportStatus().name(), b.getLibraryBook() == null ? null : b.getLibraryBook().getId(), b.getDiscoverTime(), b.getLastCrawlTime()); }
    public ChapterView chapterView(CrawlerChapter c) { return new ChapterView(c.getId(), c.getChapterIndex(), c.getChapterName(), c.getChapterUrl(), value(c.getWordCount(), 0), c.getCrawlStatus().name(), c.getAccessStatus().name(), value(c.getRetryCount(), 0), c.getErrorMessage(), c.getCrawlTime()); }
    public TaskView taskView(CrawlerTask t) { return new TaskView(t.getId(), t.getType().name(), t.getStatus().name(), t.getPriority().name(), t.getSite().getId(), t.getSite().getSiteName(), t.getCrawlerBook() == null ? null : t.getCrawlerBook().getId(), t.getCrawlerBook() == null ? null : t.getCrawlerBook().getBookName(), value(t.getTotalCount(), 0), value(t.getSuccessCount(), 0), value(t.getFailedCount(), 0), value(t.getWaitingCount(), 0), t.getCurrentChapter(), t.getAverageRequestMillis() == null ? 0 : t.getAverageRequestMillis(), t.getErrorMessage(), t.getStartedAt(), t.getFinishedAt(), t.getCreatedAt()); }

    private void validateBaseUrl(String value) { try { URI uri = URI.create(value); if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) throw new Exception(); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "网站根地址必须是有效的 HTTP(S) 地址"); } }
    private String trimSlash(String value) { return value.trim().replaceAll("/+$", ""); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean bool(Boolean value, boolean fallback) { return value == null ? fallback : value; }
    private int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private String ruleJson(CrawlerSiteRule rule) { try { return objectMapper.writeValueAsString(rulePayload(rule)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void snapshot(CrawlerSite site, String summary) { ruleVersionRepository.save(CrawlerSiteRuleVersion.builder().site(site).version(value(site.getRule().getRuleVersion(), 1)).configJson(ruleJson(site.getRule())).changeSummary(summary).build()); }
    private RuleVersionView ruleVersionView(CrawlerSiteRuleVersion v) { try { return new RuleVersionView(v.getId(), v.getVersion(), v.getChangeSummary(), objectMapper.readValue(v.getConfigJson(), RulePayload.class), v.getCreatedAt()); } catch (Exception e) { throw new IllegalStateException("规则版本数据损坏", e); } }
}
