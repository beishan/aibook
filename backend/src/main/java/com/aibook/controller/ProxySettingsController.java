package com.aibook.controller;

import com.aibook.dto.ProxySettingsDtos.*;
import com.aibook.service.ProxySettingsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proxy-settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ProxySettingsController {
    private final ProxySettingsService service;

    @GetMapping("/system") public List<SystemProxyView> systemProxies() { return service.systemProxies(); }
    @PostMapping("/system") public SystemProxyView createSystem(@RequestBody SystemProxyRequest request) { return service.createSystemProxy(request); }
    @PutMapping("/system/order") public List<SystemProxyView> reorderSystem(@RequestBody SystemProxyOrderRequest request) { return service.reorderSystemProxies(request); }
    @PutMapping("/system/{id}") public SystemProxyView updateSystem(@PathVariable Long id, @RequestBody SystemProxyRequest request) { return service.updateSystemProxy(id, request); }
    @DeleteMapping("/system/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSystem(@PathVariable Long id) { service.deleteSystemProxy(id); }

    @GetMapping("/crawler") public List<CrawlerProxyView> crawlerProxies() { return service.crawlerProxies(); }
    @PostMapping("/crawler") public CrawlerProxyView createCrawler(@RequestBody CrawlerProxyRequest request) { return service.createCrawlerProxy(request); }
    @PutMapping("/crawler/order") public List<CrawlerProxyView> reorderCrawler(@RequestBody CrawlerProxyOrderRequest request) { return service.reorderCrawlerProxies(request); }
    @PutMapping("/crawler/{id}") public CrawlerProxyView updateCrawler(@PathVariable Long id, @RequestBody CrawlerProxyRequest request) { return service.updateCrawlerProxy(id, request); }
    @DeleteMapping("/crawler/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCrawler(@PathVariable Long id) { service.deleteCrawlerProxy(id); }
}
