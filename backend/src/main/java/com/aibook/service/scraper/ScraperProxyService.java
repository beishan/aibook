package com.aibook.service.scraper;

import com.aibook.model.entity.SystemProxyConfig;
import com.aibook.repository.CrawlerMihomoPolicyRepository;
import com.aibook.repository.SystemProxyConfigRepository;
import com.aibook.service.SystemMihomoService;
import com.aibook.service.crawler.MihomoApiClient;
import com.aibook.service.scraper.ScraperProxyConfigStore.Settings;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ScraperProxyService {
    private final ScraperProxyConfigStore store;
    private final SystemProxyConfigRepository proxies;
    private final SystemMihomoService mihomo;
    private final MihomoApiClient api;
    private final CrawlerMihomoPolicyRepository policies;
    private final Map<String, Runtime> runtimes = new ConcurrentHashMap<>();

    public record Outcome<T>(T value, boolean success) {}
    public interface Request<T> { Outcome<T> run(Proxy proxy) throws Exception; }
    public record View(Settings settings, String currentNode, long activeMillis, long requests,
            long tasks, Map<String, Instant> cooldowns, String lastEvent) {}

    private Runtime runtime(String source) {
        return runtimes.computeIfAbsent(source, ignored -> new Runtime());
    }

    public View get(String source) {
        var saved = store.read(source);
        Runtime state = runtime(source);
        if (!state.lock.tryLock()) {
            return new View(saved.settings(), state.node, state.activeMillis, state.requests,
                    state.tasks, Map.of(), "来源请求正在执行，可稍后刷新状态");
        }
        try {
            return new View(saved.settings(), state.node, state.activeMillis, state.requests,
                    state.tasks, Map.copyOf(state.cooldowns), state.lastEvent);
        } finally {
            state.lock.unlock();
        }
    }

    public View save(String source, Long owner, Settings settings) throws Exception {
        store.read(source);
        validate(settings);
        Runtime state = runtime(source);
        if (!state.lock.tryLock(5, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此来源请求执行中，请完成后重试保存");
        }
        try {
            synchronized (store) {
                if ("FIXED".equals(settings.mode())) toProxy(fixed(settings.systemProxyId()).getUrl());
                if ("MIHOMO".equals(settings.mode())) {
                    var connection = mihomo.resolve(settings.systemProxyId(), settings.nodeGroupId(), owner);
                    checkExclusive(source, connection);
                    if ("MANUAL".equals(settings.switchingMode()) && !connection.nodes().contains(settings.manualNode())) {
                        bad("请选择节点组内的手动节点");
                    }
                    if (List.of("TIME", "REQUEST", "TASK").contains(settings.switchingMode())
                            && connection.nodes().size() < 2) bad("定期轮换需要至少两个节点");
                    validateCatalog(connection);
                }
                store.save(source, owner, settings);
                state.definition = null;
                state.reset();
            }
        } finally {
            state.lock.unlock();
        }
        return get(source);
    }

    public void taskSucceeded(String source) {
        Runtime state = runtime(source);
        state.lock.lock();
        try {
            var saved = store.read(source);
            if ("MIHOMO".equals(saved.settings().mode()) && state.definition != null
                    && state.definition.startsWith(saved.toString())) state.tasks++;
        } finally {
            state.lock.unlock();
        }
    }

    /** 不更改全局代理；每次请求均显式指定直连或选定代理。 */
    public <T> Outcome<T> execute(String source, Request<T> request) throws Exception {
        var saved = store.read(source);
        Settings settings = saved.settings();
        if ("NONE".equals(settings.mode())) {
            ScrapeDiagnostics.note("代理：无代理（直连）");
            return request.run(Proxy.NO_PROXY);
        }
        if ("FIXED".equals(settings.mode())) {
            var proxy = fixed(settings.systemProxyId());
            ScrapeDiagnostics.note("代理：固定代理 · " + proxy.getName());
            return request.run(toProxy(proxy.getUrl()));
        }
        if (!"MIHOMO".equals(settings.mode())) throw unavailable("未知代理方式，请重新保存配置");
        Runtime state = runtime(source);
        state.lock.lockInterruptibly();
        try {
            var connection = mihomo.resolve(settings.systemProxyId(), settings.nodeGroupId(), saved.ownerId());
            checkExclusive(source, connection);
            String definition = saved.toString() + connection.controllerUrl() + connection.controlGroup()
                    + connection.proxyUrl() + connection.nodes();
            if (!Objects.equals(state.definition, definition)) {
                state.reset();
                state.definition = definition;
            }
            // 校验实际 Selector 和叶节点，节点组被修改或节点失效时禁止静默直连。
            com.fasterxml.jackson.databind.JsonNode catalog;
            try {
                catalog = validateCatalog(connection);
            } catch (IOException exception) {
                throw unavailable("Mihomo 控制 API 不可用，请检查连接和密钥");
            }
            String actual = catalog.path("proxies").path(connection.controlGroup()).path("now").asText();
            int attempts = settings.failover() || "FAILOVER".equals(settings.switchingMode())
                    ? connection.nodes().size() : 1;
            for (int attempt = 0; attempt < attempts; attempt++) {
                String node = choose(settings, connection.nodes(), state);
                if (!Objects.equals(node, actual)) {
                    try {
                        api.select(connection.controllerUrl(), connection.secret(), connection.controlGroup(), node);
                    } catch (IOException exception) {
                        throw unavailable("Mihomo 节点切换失败或结果无法确认，请检查控制 API");
                    }
                    actual = node;
                }
                if (!Objects.equals(state.node, node)) {
                    state.node = node;
                    state.activeMillis = 0;
                    state.requests = 0;
                    state.tasks = 0;
                    state.lastEvent = "已切换到节点：" + node;
                }
                ScrapeDiagnostics.note("代理：Mihomo · " + connection.controlGroup() + " · " + node);
                long started = System.nanoTime();
                try {
                    Outcome<T> result = request.run(toProxy(connection.proxyUrl()));
                    state.failures.remove(node);
                    if (result.success()) state.requests++;
                    return result;
                } catch (Exception exception) {
                    if (!networkFailure(exception)) {
                        state.failures.remove(node);
                        throw exception;
                    }
                    int count = state.failures.merge(node, 1, Integer::sum);
                    state.lastEvent = "节点网络异常：" + node + "（连续 " + count + " 次）";
                    ScrapeDiagnostics.note(state.lastEvent);
                    if (count >= settings.failureThreshold()) {
                        state.cooldowns.put(node, Instant.now().plusSeconds(settings.cooldownSeconds()));
                        if (!"MANUAL".equals(settings.switchingMode())
                                && (settings.failover() || "FAILOVER".equals(settings.switchingMode()))
                                && attempt + 1 < attempts) continue;
                    }
                    throw exception;
                } finally {
                    state.activeMillis += (System.nanoTime() - started) / 1_000_000;
                }
            }
            throw unavailable("节点重试已耗尽");
        } finally {
            state.lock.unlock();
        }
    }

    public String getJson(String source, String url) throws Exception {
        return execute(source, proxy -> {
            var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setProxy(proxy);
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(20000);
            var response = new RestTemplate(factory).getForEntity(url, String.class);
            boolean accepted = ScrapeDiagnostics.response(response.getStatusCode().value(), url, response.getBody());
            return new Outcome<>(accepted ? response.getBody() : null, accepted);
        }).value();
    }

    private String choose(Settings settings, List<String> nodes, Runtime state) {
        Instant now = Instant.now();
        state.cooldowns.entrySet().removeIf(entry -> {
            if (entry.getValue().isAfter(now)) return false;
            state.failures.remove(entry.getKey());
            return true;
        });
        List<String> available = nodes.stream().filter(node -> !state.cooldowns.containsKey(node)
                || !state.cooldowns.get(node).isAfter(now)).toList();
        if ("MANUAL".equals(settings.switchingMode())) {
            if (!available.contains(settings.manualNode())) throw unavailable("手动节点已冻结或已从节点组移除");
            return settings.manualNode();
        }
        if (available.isEmpty()) throw unavailable("所有节点均在冻结期，请稍后重试");
        if (state.node != null && !available.contains(state.node) && nodes.contains(state.node)
                && !settings.failover() && !"FAILOVER".equals(settings.switchingMode())) {
            throw unavailable("当前节点已冻结，故障自动切换未启用，请等待恢复");
        }
        boolean due = "TIME".equals(settings.switchingMode()) && state.activeMillis >= settings.rotationSeconds() * 1000L
                || "REQUEST".equals(settings.switchingMode()) && state.requests >= settings.rotationRequests()
                || "TASK".equals(settings.switchingMode()) && state.tasks >= settings.rotationTasks();
        if (state.node != null && available.contains(state.node) && !due) return state.node;
        List<String> next = available.stream().filter(node -> !Objects.equals(node, state.node)).toList();
        if (next.isEmpty()) return available.get(0);
        if (settings.randomOrder()) return next.get(ThreadLocalRandom.current().nextInt(next.size()));
        int index = state.node == null ? -1 : nodes.indexOf(state.node);
        for (int offset = 1; offset <= nodes.size(); offset++) {
            String node = nodes.get(Math.floorMod(index + offset, nodes.size()));
            if (next.contains(node)) return node;
        }
        return next.get(0);
    }

    private com.fasterxml.jackson.databind.JsonNode validateCatalog(SystemMihomoService.ResolvedConnection connection)
            throws Exception {
        var catalog = api.proxies(connection.controllerUrl(), connection.secret());
        var entries = catalog.path("proxies");
        var group = entries.path(connection.controlGroup());
        if (!"Selector".equals(group.path("type").asText())) bad("受控代理组不是有效的 Selector");
        List<String> members = new ArrayList<>();
        group.path("all").forEach(item -> members.add(item.asText()));
        if (connection.nodes().isEmpty()) bad("节点组为空");
        for (String node : connection.nodes()) {
            var entry = entries.path(node);
            if (!members.contains(node) || entry.isMissingNode() || entry.has("all")
                    || List.of("Direct", "Reject", "RejectDrop", "Pass").contains(entry.path("type").asText())) {
                bad("节点组包含无效或非具体代理节点，请重新配置节点组");
            }
        }
        return catalog;
    }

    private void checkExclusive(String source, SystemMihomoService.ResolvedConnection connection) {
        if (policies.existsByControllerUrlAndGroupNameAndExecutorIdNot(
                connection.controllerUrl(), connection.controlGroup(), -1L)
                || store.controls(connection.controllerUrl(), connection.controlGroup(), source)) {
            bad("此受控组已被执行器或其他刮削来源使用，请配置独立的 Selector 组");
        }
    }

    private SystemProxyConfig fixed(Long id) {
        if (id == null) throw unavailable("请选择固定系统代理");
        var proxy = proxies.findById(id).orElseThrow(() -> unavailable("固定代理已删除"));
        if (!Boolean.TRUE.equals(proxy.getEnabled()) || proxy.getProxyType() != SystemProxyConfig.ProxyType.SIMPLE) {
            throw unavailable("固定代理已停用或类型不匹配");
        }
        return proxy;
    }

    static Proxy toProxy(String url) {
        URI uri = URI.create(com.aibook.service.crawler.CrawlerMihomoService.normalizeUrl(url, true));
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(uri.getHost(), uri.getPort()));
    }

    static boolean networkFailure(Throwable error) {
        if (error instanceof RestClientResponseException || error instanceof MihomoApiClient.ApiException
                || error instanceof org.jsoup.HttpStatusException) return false;
        return error instanceof IOException || error instanceof ResourceAccessException;
    }

    private void validate(Settings s) {
        if (s == null || !List.of("NONE", "FIXED", "MIHOMO").contains(Objects.toString(s.mode(), ""))) bad("请选择代理方式");
        if ("NONE".equals(s.mode())) return;
        if (s.systemProxyId() == null) bad("请选择系统代理");
        if (!"MIHOMO".equals(s.mode())) return;
        if (s.nodeGroupId() == null || !List.of("MANUAL", "FAILOVER", "TIME", "REQUEST", "TASK")
                .contains(Objects.toString(s.switchingMode(), ""))) bad("请选择节点组及切换方式");
        if (s.failureThreshold() < 1 || s.failureThreshold() > 10 || s.cooldownSeconds() < 10 || s.cooldownSeconds() > 604800
                || s.rotationSeconds() < 30 || s.rotationSeconds() > 604800
                || s.rotationRequests() < 1 || s.rotationRequests() > 1000000
                || s.rotationTasks() < 1 || s.rotationTasks() > 1000000) bad("节点轮换或故障阈值超出允许范围");
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
    private static final class Runtime {
        final ReentrantLock lock = new ReentrantLock();
        String definition;
        volatile String node;
        volatile long activeMillis;
        volatile long requests;
        volatile long tasks;
        volatile String lastEvent = "尚未请求";
        final Map<String, Integer> failures = new HashMap<>();
        final Map<String, Instant> cooldowns = new HashMap<>();
        void reset() {
            node = null;
            activeMillis = 0;
            requests = 0;
            tasks = 0;
            failures.clear();
            cooldowns.clear();
            lastEvent = "配置已更新，等待下一次请求";
        }
    }
}
