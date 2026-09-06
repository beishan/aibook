package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.*;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
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
        return siteView(site);
    }

    @Transactional
    public SiteView updateSite(User user, Long id, SitePayload payload) {
        CrawlerSite site = ownedSite(user, id);
        validateBaseUrl(payload.baseUrl());
        siteRepository.findByUserAndSiteCode(user, payload.siteCode())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "网站编码已存在"); });
        apply(site, payload);
        site = siteRepository.save(site);
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

    @Transactional
    public List<RuleVersionView> rules(User user, Long siteId) {
        CrawlerSite site = ownedSite(user, siteId);
        backfillActiveRule(site);
        return ruleVersionRepository.findBySiteOrderByVersionDesc(site).stream().map(this::ruleVersionView).toList();
    }

    @Transactional
    public RuleVersionView createRule(User user, Long siteId, RuleSaveRequest request) {
        CrawlerSite site = ownedSiteForUpdate(user, siteId);
        requireUniqueVersion(site, request.version(), null);
        CrawlerSiteRuleVersion rule = CrawlerSiteRuleVersion.builder().site(site).version(request.version())
                .changeSummary(request.changeSummary().trim()).configJson(ruleJson(request.rule())).enabled(false).build();
        rule = ruleVersionRepository.save(rule);
        if (bool(request.enabled(), false)) enableRule(site, rule);
        return ruleVersionView(rule);
    }

    @Transactional
    public RuleVersionView updateRule(User user, Long siteId, Long ruleId, RuleSaveRequest request) {
        CrawlerSite site = ownedSiteForUpdate(user, siteId);
        CrawlerSiteRuleVersion rule = ownedRule(site, ruleId);
        requireUniqueVersion(site, request.version(), ruleId);
        rule.setVersion(request.version());
        rule.setChangeSummary(request.changeSummary().trim());
        rule.setConfigJson(ruleJson(request.rule()));
        rule = ruleVersionRepository.save(rule);
        if (bool(request.enabled(), Boolean.TRUE.equals(rule.getEnabled()))) enableRule(site, rule);
        else if (Boolean.TRUE.equals(rule.getEnabled())) disableRule(site, rule);
        return ruleVersionView(rule);
    }

    @Transactional
    public RuleVersionView setRuleStatus(User user, Long siteId, Long ruleId, boolean enabled) {
        CrawlerSite site = ownedSiteForUpdate(user, siteId);
        CrawlerSiteRuleVersion rule = ownedRule(site, ruleId);
        if (enabled) enableRule(site, rule); else disableRule(site, rule);
        return ruleVersionView(rule);
    }

    @Transactional
    public void deleteRule(User user, Long siteId, Long ruleId) {
        CrawlerSite site = ownedSiteForUpdate(user, siteId);
        CrawlerSiteRuleVersion rule = ownedRule(site, ruleId);
        if (Boolean.TRUE.equals(rule.getEnabled())) disableRule(site, rule);
        ruleVersionRepository.delete(rule);
    }

    @Transactional(readOnly = true)
    public RuleExportView exportRule(User user, Long siteId, Long ruleId) {
        CrawlerSite site = ownedSite(user, siteId);
        CrawlerSiteRuleVersion rule = ownedRule(site, ruleId);
        return new RuleExportView(1, site.getSiteCode(), rule.getVersion(), rule.getChangeSummary(), readRule(rule));
    }

    @Transactional
    public RuleVersionView importRule(User user, Long siteId, RuleImportRequest request) {
        if (request.schemaVersion() != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的规则 JSON 版本");
        return createRule(user, siteId, new RuleSaveRequest(request.version(), request.changeSummary(),
                request.rule(), request.enabled()));
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
        site.setUserAgent(p.userAgent()); site.setCookie(p.cookie()); site.setHeadersJson(p.headersJson());
        applyProxies(site, p.proxies());
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
        RulePayload rv = r == null ? null : rulePayload(r);
        Optional<CrawlerSiteRuleVersion> active = ruleVersionRepository.findFirstBySiteAndEnabledTrue(s);
        return new SiteView(s.getId(), s.getSiteName(), s.getSiteCode(), s.getBaseUrl(), s.getHomeUrl(), bool(s.getEnabled(), false),
                bool(s.getAutoScan(), false), bool(s.getAutoCrawl(), false), bool(s.getAutoUpdate(), true), bool(s.getAutoImportLibrary(), false),
                value(s.getRequestIntervalMillis(), 1500), value(s.getRandomDelayMillis(), 1000), value(s.getMaxConcurrency(), 1),
                value(s.getTimeoutMillis(), 15000), value(s.getRetryCount(), 2), s.getEncoding(), s.getUserAgent(),
                s.getCookie(), s.getHeadersJson(), s.getProxy(), proxyPayloads(s), value(s.getScanIntervalMinutes(), 360),
                value(s.getUpdateIntervalMinutes(), 30), value(s.getMaxDiscoveryPages(), 3),
                blank(s.getAutoImportFormat()) ? "EPUB" : s.getAutoImportFormat(), s.getStatus().name(),
                bookRepository.countBySite(s), rv, r == null ? null : r.getRuleVersion(),
                active.map(CrawlerSiteRuleVersion::getId).orElse(null), ruleVersionRepository.countBySite(s),
                s.getLastScanAt(), s.getLastUpdateAt(),
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
    private void applyProxies(CrawlerSite site, List<ProxyPayload> values) {
        List<ProxyPayload> proxies = values == null ? List.of() : values.stream()
                .map(value -> new ProxyPayload(value.name().trim(), value.url().trim(), bool(value.enabled(), false))).toList();
        if (proxies.stream().filter(ProxyPayload::enabled).count() > 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同一时间只能启用一组代理");
        proxies.forEach(this::validateProxy);
        try { site.setProxyConfigsJson(objectMapper.writeValueAsString(proxies)); }
        catch (Exception exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代理配置无法保存", exception); }
        site.setProxy(proxies.stream().filter(ProxyPayload::enabled).map(ProxyPayload::url).findFirst().orElse(null));
    }
    private List<ProxyPayload> proxyPayloads(CrawlerSite site) {
        if (blank(site.getProxyConfigsJson())) return blank(site.getProxy()) ? List.of() :
                List.of(new ProxyPayload("默认代理", site.getProxy(), true));
        try { return objectMapper.readValue(site.getProxyConfigsJson(), new TypeReference<List<ProxyPayload>>() { }); }
        catch (Exception exception) { throw new IllegalStateException("代理配置数据损坏", exception); }
    }
    private void validateProxy(ProxyPayload proxy) {
        try {
            URI uri = URI.create(proxy.url().contains("://") ? proxy.url() : "http://" + proxy.url());
            if (uri.getHost() == null || uri.getPort() < 1 || !Set.of("http", "https").contains(uri.getScheme())) throw new Exception();
        } catch (Exception exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代理地址必须包含有效的主机和端口"); }
    }
    private String trimSlash(String value) { return value.trim().replaceAll("/+$", ""); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean bool(Boolean value, boolean fallback) { return value == null ? fallback : value; }
    private int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private String ruleJson(RulePayload rule) { try { return objectMapper.writeValueAsString(rule); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String ruleJson(CrawlerSiteRule rule) { return ruleJson(rulePayload(rule)); }
    private RulePayload readRule(CrawlerSiteRuleVersion version) { try { return objectMapper.readValue(version.getConfigJson(), RulePayload.class); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "规则版本数据损坏", e); } }
    private RuleVersionView ruleVersionView(CrawlerSiteRuleVersion v) { return new RuleVersionView(v.getId(), v.getVersion(), v.getChangeSummary(), Boolean.TRUE.equals(v.getEnabled()), readRule(v), v.getCreatedAt(), v.getUpdatedAt()); }

    private CrawlerSiteRuleVersion ownedRule(CrawlerSite site, Long ruleId) {
        return ruleVersionRepository.findByIdAndSite(ruleId, site)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则版本不存在"));
    }

    private CrawlerSite ownedSiteForUpdate(User user, Long id) {
        return siteRepository.findLockedByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集网站不存在"));
    }

    private void requireUniqueVersion(CrawlerSite site, int version, Long currentId) {
        ruleVersionRepository.findBySiteAndVersion(site, version)
                .filter(rule -> !Objects.equals(rule.getId(), currentId))
                .ifPresent(rule -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "规则版本号已存在"); });
    }

    private void enableRule(CrawlerSite site, CrawlerSiteRuleVersion selected) {
        ruleVersionRepository.findBySiteOrderByVersionDesc(site).forEach(rule -> {
            boolean enabled = Objects.equals(rule.getId(), selected.getId());
            if (!Objects.equals(rule.getEnabled(), enabled)) { rule.setEnabled(enabled); ruleVersionRepository.save(rule); }
        });
        selected.setEnabled(true);
        applyRule(site, readRule(selected));
        site.getRule().setRuleVersion(selected.getVersion());
        site.setStatus(CrawlerSite.SiteStatus.READY);
        site.setHealthMessage("规则 v" + selected.getVersion() + " 已启用，等待健康检查");
        siteRepository.save(site);
    }

    private void disableRule(CrawlerSite site, CrawlerSiteRuleVersion selected) {
        boolean wasEnabled = Boolean.TRUE.equals(selected.getEnabled());
        selected.setEnabled(false);
        ruleVersionRepository.save(selected);
        if (wasEnabled && site.getRule() != null) {
            site.attachRule(null);
            site.setStatus(CrawlerSite.SiteStatus.PAUSED);
            site.setHealthMessage("当前没有生效的采集规则");
            siteRepository.save(site);
        }
    }

    private void backfillActiveRule(CrawlerSite site) {
        if (site.getRule() == null || ruleVersionRepository.findFirstBySiteAndEnabledTrue(site).isPresent()) return;
        int version = value(site.getRule().getRuleVersion(), 1);
        CrawlerSiteRuleVersion active = ruleVersionRepository.findBySiteAndVersion(site, version).orElseGet(() ->
                ruleVersionRepository.save(CrawlerSiteRuleVersion.builder().site(site).version(version)
                        .configJson(ruleJson(site.getRule())).changeSummary("历史生效规则").enabled(false).build()));
        enableRule(site, active);
    }
}
