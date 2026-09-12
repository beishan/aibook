package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.DiscoveryPagePayload;
import com.aibook.dto.crawler.CrawlerDtos.DiscoveryPageView;
import com.aibook.model.entity.CrawlerDiscoveryPage;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerDiscoveryPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CrawlerDiscoveryPageService {
    private final CrawlerDiscoveryPageRepository repository;
    private final CrawlerManagementService managementService;
    private final CrawlerHttpClient httpClient;

    @Transactional(readOnly = true)
    public List<DiscoveryPageView> pages(User user) {
        return repository.findBySiteUserOrderByCreatedAtAsc(user).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<DiscoveryPageView> pages(User user, Long siteId) {
        CrawlerSite site = managementService.ownedSite(user, siteId);
        return repository.findBySiteOrderByCreatedAtAsc(site).stream().map(this::view).toList();
    }

    @Transactional
    public DiscoveryPageView create(User user, Long siteId, DiscoveryPagePayload payload) {
        CrawlerSite site = managementService.ownedSite(user, siteId);
        CrawlerDiscoveryPage page = CrawlerDiscoveryPage.builder().site(site).build();
        apply(page, payload);
        return view(repository.save(page));
    }

    @Transactional
    public DiscoveryPageView update(User user, Long pageId, DiscoveryPagePayload payload) {
        CrawlerDiscoveryPage page = owned(user, pageId);
        apply(page, payload);
        return view(repository.save(page));
    }

    @Transactional
    public void delete(User user, Long pageId) {
        repository.delete(owned(user, pageId));
    }

    public CrawlerDiscoveryPage owned(User user, Long pageId) {
        return repository.findByIdAndSiteUser(pageId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "发现页不存在"));
    }

    public DiscoveryPageView view(CrawlerDiscoveryPage page) {
        return new DiscoveryPageView(page.getId(), page.getSite().getId(), page.getPageName(),
                page.getPageUrl(), Boolean.TRUE.equals(page.getAutoScanEnabled()),
                page.getScanIntervalMinutes(), page.getMaxPages(), page.getLastScanAt(), page.getCreatedAt());
    }

    private void apply(CrawlerDiscoveryPage page, DiscoveryPagePayload payload) {
        String name = payload.pageName().trim();
        Long currentId = page.getId() == null ? -1L : page.getId();
        if (repository.existsBySiteAndPageNameIgnoreCaseAndIdNot(page.getSite(), name, currentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该网站下已存在同名发现页");
        }
        String url = httpClient.validateSiteUrl(page.getSite(), payload.pageUrl()).toString();
        page.setPageName(name);
        page.setPageUrl(url);
        page.setAutoScanEnabled(Boolean.TRUE.equals(payload.autoScanEnabled()));
        page.setScanIntervalMinutes(payload.scanIntervalMinutes() == null ? 360 : payload.scanIntervalMinutes());
        page.setMaxPages(payload.maxPages() == null ? 50 : payload.maxPages());
    }
}
