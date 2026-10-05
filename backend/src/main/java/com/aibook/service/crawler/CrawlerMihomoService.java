package com.aibook.service.crawler;

import com.aibook.dto.crawler.MihomoDtos.*;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CrawlerMihomoService {
    private final CrawlerMihomoPolicyRepository policies;
    private final CrawlerMihomoReceiptRepository receipts;
    private final CrawlerQueueExecutorRepository executors;
    private final CrawlerSiteRepository sites;
    private final ObjectMapper mapper;
    private final MihomoApiClient api;
    private final TransactionTemplate transactions;
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ThreadLocal<Long> executingSiteId = new ThreadLocal<>();

    public interface RequestAction<T> { T run() throws Exception; }

    private ReentrantLock lock(Long id) {
        return locks.computeIfAbsent(id, ignored -> new ReentrantLock(true));
    }

    public PolicyView get(User user, Long queueId, Long id) {
        owned(user, queueId, id);
        return policies.findById(id).map(this::view).orElse(null);
    }

    public CatalogView browse(User user, Long queueId, Long id, PolicyPayload draft) throws Exception {
        owned(user, queueId, id);
        CrawlerMihomoPolicy config = connection(id, draft);
        return catalog(api.proxies(config.getControllerUrl(), config.getSecret()));
    }

    public PolicyView save(User user, Long queueId, Long id, PolicyPayload payload) throws Exception {
        ReentrantLock gate = lock(id);
        gate.lockInterruptibly();
        try {
            CrawlerQueueExecutor executor = owned(user, queueId, id);
            String previousConnection = policies.findById(id)
                    .map(p -> p.getControllerUrl() + "|" + p.getGroupName() + "|" + p.getProxyUrl())
                    .orElse(null);
            CrawlerMihomoPolicy config = connection(id, payload);
            config.setProxyUrl(normalizeUrl(payload.proxyUrl(), true));
            if (payload.groupName() == null || payload.groupName().isBlank()
                    || payload.groupName().length() > 200) bad("请选择受控代理组");
            config.setGroupName(payload.groupName());
            if (policies.existsByControllerUrlAndGroupNameAndExecutorIdNot(
                    config.getControllerUrl(), config.getGroupName(), id)) {
                bad("此控制器的代理组已由其他执行器管理，请使用独立代理组");
            }
            List<String> selected = payload.nodes();
            if (selected == null || selected.isEmpty() || selected.size() > 100
                    || new HashSet<>(selected).size() != selected.size()
                    || selected.stream().anyMatch(node -> node == null || node.isBlank() || node.length() > 200)) {
                bad("请选择 1 至 100 个不重复节点，节点名称不能超过 200 个字符");
            }
            validateNodes(config, selected);
            range(payload.failureThreshold(), 1, 10, "连续失败阈值");
            range(payload.cooldownSeconds(), 10, 604800, "节点冷却秒数");
            range(payload.rotationSeconds(), 0, 604800, "轮换秒数");
            if (payload.rotationSeconds() > 0 && payload.rotationSeconds() < 30) bad("轮换间隔至少 30 秒");
            range(payload.rotationChapters(), 0, 1000000, "轮换章节数");
            range(payload.rotationTasks(), 0, 1000000, "轮换任务数");
            if (selected.size() < 2 && (payload.rotationSeconds() > 0
                    || payload.rotationChapters() > 0 || payload.rotationTasks() > 0)) {
                bad("启用定期轮换需要至少两个所选节点");
            }
            config.setNodesJson(write(selected));
            config.setFailover(payload.failover());
            config.setFailureThreshold(payload.failureThreshold());
            config.setCooldownSeconds(payload.cooldownSeconds());
            config.setRotationSeconds(payload.rotationSeconds());
            config.setRotationChapters(payload.rotationChapters());
            config.setRotationTasks(payload.rotationTasks());
            config.setRandomOrder(payload.randomOrder());
            String nextConnection = config.getControllerUrl() + "|" + config.getGroupName() + "|" + config.getProxyUrl();
            if (!Objects.equals(previousConnection, nextConnection)) {
                config.setCurrentNode(null);
                config.setCooldownsJson("{}");
                resetCounters(config);
            }
            config.setRetryAt(null);
            config.setLastError(null);
            config.setLastActivityAt(null);
            transactions.executeWithoutResult(status -> {
                policies.save(config);
                executor.setProxyMode(CrawlerQueueExecutor.ProxyMode.MIHOMO);
                executor.setSelectionStrategy(payload.randomOrder()
                        ? CrawlerQueueExecutor.SelectionStrategy.RANDOM : CrawlerQueueExecutor.SelectionStrategy.ORDERED);
                executors.save(executor);
            });
            return view(config);
        } finally {
            gate.unlock();
        }
    }

    public DelayView test(User user, Long queueId, Long id, String node) throws Exception {
        owned(user, queueId, id);
        CrawlerMihomoPolicy config = required(id);
        ensureNotFrozen(id);
        validateNodes(config, List.of(node));
        try {
            return new DelayView(node, true, api.delay(config.getControllerUrl(), config.getSecret(), node), Instant.now());
        } catch (MihomoApiClient.ApiException exception) {
            if (exception.status() != 503 && exception.status() != 504) throw exception;
            return new DelayView(node, false, null, Instant.now());
        }
    }

    public PolicyView switchManually(User user, Long queueId, Long id, String node) throws Exception {
        owned(user, queueId, id);
        ReentrantLock gate = lock(id);
        gate.lockInterruptibly();
        try {
            ensureNotFrozen(id);
            CrawlerMihomoPolicy config = required(id);
            if (!nodes(config).contains(node)) bad("只能切换到已选节点；请先保存节点配置");
            validateNodes(config, List.of(node));
            api.delay(config.getControllerUrl(), config.getSecret(), node);
            switchTo(config, node, "手动切换");
            return view(config);
        } finally {
            gate.unlock();
        }
    }

    public boolean canExecute(Long id) {
        return policies.findById(id).map(p -> p.getRetryAt() == null
                || !p.getRetryAt().isAfter(Instant.now())).orElse(false);
    }

    public String proxyUrl(Long id) {
        return required(id).getProxyUrl();
    }

    /** Serializes requests and switching: no in-flight request can observe a mid-request node change. */
    public <T> T execute(Long id, CrawlerSite site, RequestAction<T> action) throws Exception {
        ReentrantLock gate = lock(id);
        gate.lockInterruptibly();
        if (site != null) executingSiteId.set(site.getId());
        try {
            ensureNotFrozen(id);
            CrawlerMihomoPolicy config = required(id);
            if (!canExecute(id)) throw waiting(config.getLastError());
            // Never accrue a persisted in-flight timestamp after a process crash/restart.
            config.setLastActivityAt(null);
            try {
                reconcile(config);
                int maximum = nodes(config).size() + config.getFailureThreshold();
                for (int attempt = 0; attempt < maximum; attempt++) {
                    ensureNotFrozen(id);
                    accrueActiveTime(config);
                    Instant coolingUntil = cooldowns(config).get(config.getCurrentNode());
                    if (!nodes(config).contains(config.getCurrentNode())
                            || coolingUntil != null && coolingUntil.isAfter(Instant.now())) {
                        rotate(config, "初始化或当前节点未选中", true);
                    } else if (rotationDue(config)) {
                        rotate(config, "定期轮换", false);
                    }
                    try {
                        T result = action.run();
                        config.setFailures(0);
                        config.setLastError(null);
                        policies.save(config);
                        return result;
                    } catch (IOException exception) {
                        config.setFailures(config.getFailures() + 1);
                        policies.save(config);
                        if (!config.isFailover()) throw waitAndSave(config, "节点请求失败，自动故障切换已关闭", 30);
                        if (config.getFailures() < config.getFailureThreshold()) continue;
                        // Verify control-plane availability before interpreting a delay failure as node failure.
                        reconcile(config);
                        try {
                            api.delay(config.getControllerUrl(), config.getSecret(), config.getCurrentNode());
                            throw waitAndSave(config, "节点检测正常，目标网站请求失败，等待后重试", 30);
                        } catch (MihomoApiClient.ApiException failure) {
                            if (failure.status() != 503 && failure.status() != 504) throw failure;
                            Map<String, Instant> cooldowns = cooldowns(config);
                            cooldowns.put(config.getCurrentNode(), Instant.now().plusSeconds(config.getCooldownSeconds()));
                            config.setCooldownsJson(write(cooldowns));
                            policies.save(config);
                            rotate(config, "网络故障切换", true);
                        }
                    }
                }
                throw waitAndSave(config, "本轮节点重试已耗尽", 30);
            } catch (CrawlerHttpClient.NoAvailableQueueProxyException exception) {
                throw waitAndSave(config, exception.getMessage(), 30);
            } catch (IOException exception) {
                throw waitAndSave(config, "Mihomo 控制或检测失败：" + exception.getMessage(), 30);
            } finally {
                accrueActiveTime(config);
                // Explicitly stop timing between requests: freeze/idle/restart gaps are not counted.
                config.setLastActivityAt(null);
                policies.save(config);
            }
        } finally {
            executingSiteId.remove();
            gate.unlock();
        }
    }

    public void account(Long id, String eventKey, boolean chapter) {
        if (id == null || !policies.existsById(id)) return;
        ReentrantLock gate = lock(id);
        gate.lock();
        try {
            transactions.executeWithoutResult(status -> {
                if (receipts.existsByExecutorIdAndEventKey(id, eventKey)) return;
                CrawlerMihomoPolicy config = required(id);
                CrawlerMihomoReceipt receipt = new CrawlerMihomoReceipt();
                receipt.setExecutorId(id);
                receipt.setEventKey(eventKey);
                receipts.save(receipt);
                if (chapter) config.setChapters(config.getChapters() + 1);
                else config.setTasks(config.getTasks() + 1);
                policies.save(config);
            });
        } finally {
            gate.unlock();
        }
    }

    public void delete(Long id) {
        receipts.deleteByExecutorId(id);
        policies.deleteById(id);
    }

    private void rotate(CrawlerMihomoPolicy config, String reason, boolean required) throws Exception {
        List<String> selected = nodes(config);
        List<String> choices = new ArrayList<>();
        int current = selected.indexOf(config.getCurrentNode());
        for (int step = 1; step <= selected.size(); step++) {
            String candidate = selected.get(Math.floorMod(current + step, selected.size()));
            Instant until = cooldowns(config).get(candidate);
            if (!candidate.equals(config.getCurrentNode()) && (until == null || !until.isAfter(Instant.now()))) {
                choices.add(candidate);
            }
        }
        if (config.isRandomOrder()) Collections.shuffle(choices);
        JsonNode proxies = api.proxies(config.getControllerUrl(), config.getSecret()).path("proxies");
        for (String candidate : choices) {
            ensureNotFrozen(config.getExecutorId());
            if (!isLeaf(proxies.path(candidate)) || !groupContains(proxies, config.getGroupName(), candidate)) continue;
            try {
                api.delay(config.getControllerUrl(), config.getSecret(), candidate);
            } catch (MihomoApiClient.ApiException exception) {
                if (exception.status() != 503 && exception.status() != 504) throw exception;
                Map<String, Instant> cooldowns = cooldowns(config);
                cooldowns.put(candidate, Instant.now().plusSeconds(config.getCooldownSeconds()));
                config.setCooldownsJson(write(cooldowns));
                event(config, config.getCurrentNode(), candidate, "候选节点检测失败", false);
                policies.save(config);
                continue;
            }
            switchTo(config, candidate, reason);
            return;
        }
        if (required) throw waitAndSave(config, "所有所选节点均不可用、已移除或处于冷却期", 30);
        // A scheduled rotation cannot silently continue forever on an exhausted pool.
        if (selected.size() > 1) throw waitAndSave(config, "没有可用于轮换的备用节点", 30);
        resetCounters(config);
        policies.save(config);
    }

    private void switchTo(CrawlerMihomoPolicy config, String node, String reason) throws Exception {
        String previous = config.getCurrentNode();
        ensureNotFrozen(config.getExecutorId());
        try {
            api.select(config.getControllerUrl(), config.getSecret(), config.getGroupName(), node);
        } catch (Exception exception) {
            event(config, previous, node, "切换失败或无法确认结果", false);
            policies.save(config);
            throw exception;
        }
        config.setCurrentNode(node);
        config.setLastSwitchAt(Instant.now());
        config.setRetryAt(null);
        config.setLastError(null);
        resetCounters(config);
        event(config, previous, node, reason, true);
        policies.save(config);
    }

    private void resetCounters(CrawlerMihomoPolicy config) {
        config.setActiveMillis(0);
        config.setChapters(0);
        config.setTasks(0);
        config.setFailures(0);
        config.setLastActivityAt(Instant.now());
    }

    private void accrueActiveTime(CrawlerMihomoPolicy config) {
        Instant now = Instant.now();
        if (config.getLastActivityAt() != null) {
            config.setActiveMillis(config.getActiveMillis()
                    + Math.max(0, Duration.between(config.getLastActivityAt(), now).toMillis()));
        }
        config.setLastActivityAt(now);
    }

    static boolean rotationDue(CrawlerMihomoPolicy config) {
        return config.getRotationSeconds() > 0 && config.getActiveMillis() >= config.getRotationSeconds() * 1000L
                || config.getRotationChapters() > 0 && config.getChapters() >= config.getRotationChapters()
                || config.getRotationTasks() > 0 && config.getTasks() >= config.getRotationTasks();
    }

    private void reconcile(CrawlerMihomoPolicy config) throws Exception {
        JsonNode proxies = api.proxies(config.getControllerUrl(), config.getSecret()).path("proxies");
        JsonNode group = proxies.path(config.getGroupName());
        if (!"Selector".equals(group.path("type").asText())) throw new IOException("受控 Selector 代理组已不存在");
        String actual = group.path("now").asText();
        if (!isLeaf(proxies.path(actual)) || !groupContains(proxies, config.getGroupName(), actual)) actual = null;
        if (!Objects.equals(actual, config.getCurrentNode())) {
            event(config, config.getCurrentNode(), actual, "同步 Mihomo 实际节点", true);
            config.setCurrentNode(actual);
            resetCounters(config);
            policies.save(config);
        }
    }

    private void validateNodes(CrawlerMihomoPolicy config, List<String> selected) throws Exception {
        JsonNode proxies = api.proxies(config.getControllerUrl(), config.getSecret()).path("proxies");
        if (!"Selector".equals(proxies.path(config.getGroupName()).path("type").asText())) bad("受控代理组必须为 Selector 类型");
        for (String node : selected) {
            if (!isLeaf(proxies.path(node)) || !groupContains(proxies, config.getGroupName(), node)) {
                bad("节点不在所选代理组内或不是有效的具体节点：" + node);
            }
        }
    }

    private boolean groupContains(JsonNode proxies, String group, String node) {
        for (JsonNode item : proxies.path(group).path("all")) if (node.equals(item.asText())) return true;
        return false;
    }

    private boolean isLeaf(JsonNode node) {
        return !node.isMissingNode() && !node.has("all")
                && !Set.of("Direct", "Reject", "RejectDrop", "Pass", "Compatible")
                        .contains(node.path("type").asText());
    }

    private CatalogView catalog(JsonNode root) {
        List<GroupView> groups = new ArrayList<>();
        List<NodeView> nodes = new ArrayList<>();
        root.path("proxies").fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if ("Selector".equals(value.path("type").asText())) {
                List<String> members = new ArrayList<>();
                value.path("all").forEach(item -> members.add(item.asText()));
                groups.add(new GroupView(entry.getKey(), value.path("now").asText(), members));
            } else if (isLeaf(value)) {
                JsonNode history = value.path("history");
                JsonNode last = history.isArray() && !history.isEmpty() ? history.get(history.size() - 1) : null;
                Instant checked = null;
                try {
                    if (last != null) checked = Instant.parse(last.path("time").asText());
                } catch (DateTimeException ignored) { }
                nodes.add(new NodeView(entry.getKey(), value.path("type").asText(),
                        value.has("alive") ? value.get("alive").asBoolean() : null,
                        last != null && last.path("delay").asInt() > 0 ? last.path("delay").asInt() : null, checked));
            }
        });
        return new CatalogView(groups, nodes);
    }

    private CrawlerMihomoPolicy connection(Long id, PolicyPayload draft) {
        CrawlerMihomoPolicy config = policies.findById(id).orElseGet(CrawlerMihomoPolicy::new);
        config.setExecutorId(id);
        config.setControllerUrl(normalizeUrl(draft.controllerUrl(), false));
        if (draft.clearSecret()) config.setSecret(null);
        else if (draft.secret() != null && !draft.secret().isBlank()) {
            if (draft.secret().length() > 500 || draft.secret().contains("\n") || draft.secret().contains("\r")) bad("API 密钥格式无效");
            config.setSecret(draft.secret());
        }
        return config;
    }

    static String normalizeUrl(String input, boolean proxy) {
        try {
            URI uri = URI.create(input == null ? "" : input.trim());
            if (!(proxy ? Set.of("http") : Set.of("http", "https")).contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null
                    || input.length() > 500 || proxy && uri.getPort() < 1
                    || uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) {
                throw new IllegalArgumentException();
            }
            String authority = uri.getRawAuthority().toLowerCase(Locale.ROOT);
            if (!proxy && ("http".equals(uri.getScheme()) && uri.getPort() == 80
                    || "https".equals(uri.getScheme()) && uri.getPort() == 443)) {
                authority = authority.substring(0, authority.lastIndexOf(':'));
            }
            return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + authority;
        } catch (Exception exception) {
            bad(proxy ? "代理地址需为带端口的 HTTP 地址" : "控制 API 地址需为 HTTP/HTTPS 地址，不能包含路径或凭据");
            return null;
        }
    }

    private CrawlerQueueExecutor owned(User user, Long queueId, Long id) {
        CrawlerQueueExecutor executor = executors.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "执行器不存在"));
        CrawlerTaskQueue queue = executor.getQueue();
        Long owner = queue.getUser() != null ? queue.getUser().getId() : queue.getSite().getUser().getId();
        if (!Objects.equals(queueId, queue.getId()) || !Objects.equals(user.getId(), owner)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "执行器不存在");
        }
        return executor;
    }

    private void ensureNotFrozen(Long id) throws CrawlerHttpClient.SiteManuallyFrozenException {
        CrawlerQueueExecutor executor = executors.findById(id).orElse(null);
        CrawlerSite site = executor == null ? null : executor.getQueue().getSite();
        Long siteId = executingSiteId.get() != null ? executingSiteId.get() : site == null ? null : site.getId();
        if (siteId != null && sites.isManuallyFrozen(siteId, Instant.now())) {
            throw new CrawlerHttpClient.SiteManuallyFrozenException();
        }
    }

    private CrawlerMihomoPolicy required(Long id) {
        return policies.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "请先配置 Mihomo 连接和节点"));
    }

    private CrawlerHttpClient.NoAvailableQueueProxyException waitAndSave(
            CrawlerMihomoPolicy config, String message, int seconds) {
        config.setRetryAt(Instant.now().plusSeconds(seconds));
        config.setLastError(message.substring(0, Math.min(message.length(), 300)));
        policies.save(config);
        return waiting(message);
    }

    private CrawlerHttpClient.NoAvailableQueueProxyException waiting(String message) {
        return new CrawlerHttpClient.NoAvailableQueueProxyException(message == null ? "Mihomo 代理等待恢复" : message);
    }

    private void event(CrawlerMihomoPolicy config, String from, String to, String reason, boolean success) {
        List<SwitchEvent> events = new ArrayList<>(read(config.getEventsJson(), new TypeReference<List<SwitchEvent>>() { }));
        events.add(0, new SwitchEvent(Instant.now(), from, to, reason, success));
        config.setEventsJson(write(events.subList(0, Math.min(events.size(), 50))));
    }

    private List<String> nodes(CrawlerMihomoPolicy config) {
        return read(config.getNodesJson(), new TypeReference<List<String>>() { });
    }

    private Map<String, Instant> cooldowns(CrawlerMihomoPolicy config) {
        return read(config.getCooldownsJson(), new TypeReference<Map<String, Instant>>() { });
    }

    private PolicyView view(CrawlerMihomoPolicy p) {
        return new PolicyView(p.getControllerUrl(), p.getSecret() != null && !p.getSecret().isBlank(),
                p.getProxyUrl(), p.getGroupName(), nodes(p), p.isFailover(), p.getFailureThreshold(),
                p.getCooldownSeconds(), p.getRotationSeconds(), p.getRotationChapters(), p.getRotationTasks(),
                p.isRandomOrder(), p.getCurrentNode(), p.getActiveMillis(), p.getChapters(), p.getTasks(),
                p.getLastSwitchAt(), p.getRetryAt(), p.getLastError(), cooldowns(p),
                read(p.getEventsJson(), new TypeReference<List<SwitchEvent>>() { }));
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception exception) { throw new IllegalStateException("Mihomo 配置数据无法解析", exception); }
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Mihomo 配置无法保存", exception); }
    }

    private void range(int value, int min, int max, String label) {
        if (value < min || value > max) bad(label + "需在 " + min + " 至 " + max + " 之间");
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
