package com.aibook.service.crawler;

import com.aibook.dto.ProxySettingsDtos.CrawlerProxyView;
import com.aibook.dto.crawler.CrawlerDtos.QueueExecutorPayload;
import com.aibook.dto.crawler.CrawlerDtos.QueueExecutorProxyPayload;
import com.aibook.dto.crawler.CrawlerDtos.QueueExecutorProxyView;
import com.aibook.dto.crawler.CrawlerDtos.QueueExecutorProxyStateView;
import com.aibook.dto.crawler.CrawlerDtos.QueueExecutorView;
import com.aibook.dto.crawler.CrawlerDtos.QueueProxyOptionView;
import com.aibook.dto.crawler.CrawlerDtos.TaskExecutionView;
import com.aibook.model.entity.CrawlerQueueExecutor;
import com.aibook.model.entity.CrawlerQueueExecutorProxy;
import com.aibook.model.entity.CrawlerQueueProxyCooldown;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerTaskQueue;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerQueueExecutorProxyRepository;
import com.aibook.repository.CrawlerQueueExecutorRepository;
import com.aibook.repository.CrawlerQueueProxyCooldownRepository;
import com.aibook.repository.CrawlerTaskQueueRepository;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.ProxySettingsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CrawlerQueueExecutorService {
    private final CrawlerTaskQueueRepository queueRepository;
    private final CrawlerQueueExecutorRepository executorRepository;
    private final CrawlerQueueExecutorProxyRepository bindingRepository;
    private final CrawlerQueueProxyCooldownRepository cooldownRepository;
    private final ProxySettingsService proxySettingsService;
    private final CrawlerSettingsService crawlerSettingsService;
    private final CrawlerMihomoService mihomoService;
    private final ThreadLocal<ExecutionRoute> executionRoute = new ThreadLocal<>();
    private final ThreadLocal<ProxyCandidate> requestProxy = new ThreadLocal<>();

    public record RequestProxySnapshot(String name, String node) { }

    public void clearRequestTrace() {
        requestProxy.remove();
        mihomoService.restoreRequestTrace(null);
    }

    public void recordRequestProxy(ProxyCandidate candidate) {
        requestProxy.set(candidate);
    }

    public RequestProxySnapshot requestProxySnapshot() {
        var mihomo = mihomoService.lastRequestTrace();
        if (mihomo != null) return new RequestProxySnapshot(mihomo.groupName(), mihomo.currentNode());
        var proxy = requestProxy.get();
        return proxy == null ? null : new RequestProxySnapshot(proxy.name(), null);
    }

    public void validateOwnership(User user, Long queueId, Long executorId) {
        ownedQueue(user, queueId);
        ownedExecutor(queueId, executorId);
    }

    public record ProxyCandidate(String key, String name, String url,
            Integer cooldownSeconds) { }

    private record ExecutionRoute(Long queueId, Long executorId) { }

    public interface RouteScope extends AutoCloseable {
        @Override
        void close();
    }

    public RouteScope bind(Long queueId, Long executorId) {
        ExecutionRoute previous = executionRoute.get();
        ProxyCandidate previousProxy = requestProxy.get();
        var previousTrace = mihomoService.lastRequestTrace();
        clearRequestTrace();
        executionRoute.set(new ExecutionRoute(queueId, executorId));
        return () -> {
            if (previousProxy == null) requestProxy.remove();
            else requestProxy.set(previousProxy);
            mihomoService.restoreRequestTrace(previousTrace);
            if (previous == null) executionRoute.remove();
            else executionRoute.set(previous);
        };
    }

    public boolean hasBoundExecutor() {
        return executionRoute.get() != null;
    }

    public Long boundQueueId() {
        ExecutionRoute route = executionRoute.get();
        return route == null ? null : route.queueId();
    }

    @Transactional
    public void ensureQueueExecutors(CrawlerTaskQueue queue) {
        List<CrawlerQueueExecutor> existing =
                executorRepository.findByQueueIdOrderBySortOrderAscIdAsc(queue.getId());
        if (!existing.isEmpty()) {
            syncQueueConcurrency(queue, existing);
            return;
        }
        int count = Math.max(1, Math.min(16,
                queue.getMaxConcurrentTasks() == null ? 1 : queue.getMaxConcurrentTasks()));
        List<CrawlerQueueExecutor> executors = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            executors.add(CrawlerQueueExecutor.builder()
                    .queue(queue)
                    .name(index == 0 ? "默认执行器" : "执行器 " + (index + 1))
                    .defaultExecutor(index == 0)
                    .sortOrder(index)
                    .build());
        }
        executorRepository.saveAll(executors);
        syncQueueConcurrency(queue, executors);
    }

    public Long boundExecutorId() {
        ExecutionRoute route = executionRoute.get();
        return route == null ? null : route.executorId();
    }

    public boolean isMihomoBound() {
        ExecutionRoute route = executionRoute.get();
        return route != null && ownedExecutor(route.queueId(), route.executorId()).getProxyMode()
                == CrawlerQueueExecutor.ProxyMode.MIHOMO;
    }

    public void recordChapter(String taskId, Long chapterId) {
        if (isMihomoBound()) mihomoService.account(boundExecutorId(), "chapter:" + taskId + ":" + chapterId, true);
    }

    public void recordBookTask(String taskId) {
        if (isMihomoBound()) mihomoService.account(boundExecutorId(), "task:" + taskId, false);
    }

    @Transactional(readOnly = true)
    public List<CrawlerQueueExecutor> executors(Long queueId) {
        return executorRepository.findByQueueIdOrderBySortOrderAscIdAsc(queueId);
    }

    @Transactional(readOnly = true)
    public List<CrawlerQueueExecutor> enabledExecutors(Long queueId) {
        return executors(queueId).stream().filter(this::isExecutorEnabled).toList();
    }

    public TaskExecutionView taskExecution(Long executorId) {
        CrawlerQueueExecutor executor = executorRepository.findById(executorId).orElse(null);
        if (executor == null) return null;

        String proxyMode = executor.getProxyMode().name();
        if (executor.getProxyMode() != CrawlerQueueExecutor.ProxyMode.MIHOMO) {
            return new TaskExecutionView(executor.getName(), proxyMode, null, null, null);
        }

        CrawlerMihomoService.ExecutionSummary mihomo =
                mihomoService.executionSummary(executorId);
        return new TaskExecutionView(executor.getName(), proxyMode,
                mihomo == null ? null : mihomo.proxyUrl(),
                mihomo == null ? null : mihomo.groupName(),
                mihomo == null ? null : mihomo.currentNode());
    }

    @Transactional(readOnly = true)
    public List<QueueExecutorView> list(User user, Long queueId) {
        CrawlerTaskQueue queue = ownedQueue(user, queueId);
        Map<Long, CrawlerProxyView> proxies = proxyMap();
        Map<String, Instant> cooldowns = cooldowns(queueId);
        return executors(queueId).stream()
                .map(executor -> view(executor, queue.getSite(), proxies, cooldowns))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueueProxyOptionView> proxyOptions() {
        return proxySettingsService.crawlerProxies().stream()
                .map(proxy -> new QueueProxyOptionView(proxy.id(),
                        Objects.toString(proxy.name(), "未命名代理"),
                        proxy.effectiveEnabled()))
                .toList();
    }

    @Transactional
    public QueueExecutorView create(User user, Long queueId, QueueExecutorPayload payload) {
        CrawlerTaskQueue queue = ownedQueue(user, queueId);
        List<CrawlerQueueExecutor> existing = executors(queueId);
        if (existing.size() >= 16) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "每个队列最多配置 16 个执行器");
        }
        validate(payload);
        CrawlerQueueExecutor executor = executorRepository.save(CrawlerQueueExecutor.builder()
                .queue(queue)
                .name(payload.name().trim())
                .description(normalizeDescription(payload.description()))
                .defaultExecutor(false)
                .enabled(payload.enabled() == null || payload.enabled())
                .sortOrder(existing.size())
                .proxyMode(payload.proxyMode())
                .selectionStrategy(payload.selectionStrategy())
                .defaultProxyCooldownSeconds(payload.defaultProxyCooldownSeconds())
                .build());
        saveBindings(executor, payload);
        existing.add(executor);
        syncQueueConcurrency(queue, existing);
        return view(executor, queue.getSite(), proxyMap(), cooldowns(queueId));
    }

    @Transactional
    public QueueExecutorView update(User user, Long queueId, Long executorId,
            QueueExecutorPayload payload) {
        CrawlerTaskQueue queue = ownedQueue(user, queueId);
        CrawlerQueueExecutor executor = ownedExecutor(queueId, executorId);
        validate(payload);
        executor.setName(payload.name().trim());
        executor.setDescription(normalizeDescription(payload.description()));
        if (payload.enabled() != null) {
            executor.setEnabled(payload.enabled());
        }
        executor.setProxyMode(payload.proxyMode());
        executor.setSelectionStrategy(payload.selectionStrategy());
        executor.setDefaultProxyCooldownSeconds(payload.defaultProxyCooldownSeconds());
        executorRepository.save(executor);
        bindingRepository.deleteByExecutorId(executorId);
        bindingRepository.flush();
        saveBindings(executor, payload);
        syncQueueConcurrency(queue, executors(queueId));
        return view(executor, queue.getSite(), proxyMap(), cooldowns(queueId));
    }

    @Transactional
    public void delete(User user, Long queueId, Long executorId) {
        CrawlerTaskQueue queue = ownedQueue(user, queueId);
        CrawlerQueueExecutor executor = ownedExecutor(queueId, executorId);
        if (executor.isDefaultExecutor()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "默认执行器不能删除");
        }
        bindingRepository.deleteByExecutorId(executorId);
        mihomoService.delete(executorId);
        executorRepository.delete(executor);
        List<CrawlerQueueExecutor> remaining = executors(queueId).stream()
                .filter(item -> !Objects.equals(item.getId(), executorId))
                .toList();
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setSortOrder(index);
        }
        executorRepository.saveAll(remaining);
        syncQueueConcurrency(queue, remaining);
    }

    @Transactional
    public void deleteQueueData(Long queueId) {
        for (CrawlerQueueExecutor executor : executors(queueId)) {
            mihomoService.delete(executor.getId());
            bindingRepository.deleteByExecutorId(executor.getId());
            executorRepository.delete(executor);
        }
        bindingRepository.flush();
        executorRepository.flush();
        cooldownRepository.deleteByQueueId(queueId);
        cooldownRepository.flush();
    }

    @Transactional(readOnly = true)
    public boolean canExecute(CrawlerSite site, CrawlerQueueExecutor executor) {
        if (!isExecutorEnabled(executor)) return false;
        if (executor.getProxyMode() == CrawlerQueueExecutor.ProxyMode.MIHOMO) {
            return mihomoService.canExecute(executor.getId());
        }
        Map<String, Instant> blocked = cooldowns(executor.getQueue().getId());
        Instant now = Instant.now();
        return candidates(executor, site, proxyMap()).stream()
                .anyMatch(candidate -> !isCooling(candidate, blocked, now));
    }

    @Transactional(readOnly = true)
    public List<ProxyCandidate> availableCandidates(CrawlerSite site) {
        ExecutionRoute route = executionRoute.get();
        if (route == null) return List.of();
        CrawlerQueueExecutor executor = ownedExecutor(route.queueId(), route.executorId());
        if (executor.getProxyMode() == CrawlerQueueExecutor.ProxyMode.MIHOMO) {
            return List.of(new ProxyCandidate("mihomo:" + executor.getId(), "Mihomo 托管代理",
                    mihomoService.proxyUrl(executor.getId()), executor.getDefaultProxyCooldownSeconds()));
        }
        Map<String, Instant> blocked = cooldowns(route.queueId());
        Instant now = Instant.now();
        List<ProxyCandidate> available = new ArrayList<>(candidates(executor, site, proxyMap()).stream()
                .filter(candidate -> !isCooling(candidate, blocked, now))
                .toList());
        if (executor.getSelectionStrategy() == CrawlerQueueExecutor.SelectionStrategy.RANDOM) {
            Collections.shuffle(available);
        }
        return available;
    }

    @Transactional
    public void coolBoundProxy(ProxyCandidate candidate, CrawlerSite site,
            String reason, long minimumSeconds) {
        ExecutionRoute route = executionRoute.get();
        if (route == null) return;
        int fallback = crawlerSettingsService.settings().accessDeniedCooldownSeconds();
        int configured = candidate.cooldownSeconds() == null ? fallback : candidate.cooldownSeconds();
        long seconds = Math.max(Math.max(10L, configured), minimumSeconds);
        Instant until = Instant.now().plusSeconds(seconds);
        cooldownRepository.extend(route.queueId(), candidate.key(), until,
                reason == null ? "代理暂不可用" : reason.substring(0, Math.min(300, reason.length())));
    }

    @Transactional
    public void coolBoundProxyForNetworkFailure(ProxyCandidate candidate, String reason) {
        ExecutionRoute route = executionRoute.get();
        if (route == null) return;
        cooldownRepository.extend(route.queueId(), candidate.key(),
                Instant.now().plusSeconds(30),
                reason == null ? "代理连接失败" : reason.substring(0, Math.min(300, reason.length())));
    }

    private QueueExecutorView view(CrawlerQueueExecutor executor, CrawlerSite site,
            Map<Long, CrawlerProxyView> proxies, Map<String, Instant> cooldowns) {
        List<CrawlerQueueExecutorProxy> bindings = bindingRepository
                .findByExecutorIdOrderBySortOrderAscIdAsc(executor.getId());
        List<QueueExecutorProxyView> bindingViews = new ArrayList<>();
        Map<String, CrawlerQueueProxyCooldown> states = new HashMap<>();
        cooldownRepository.findByQueueId(executor.getQueue().getId())
                .forEach(state -> states.put(state.getProxyKey(), state));
        for (CrawlerQueueExecutorProxy binding : bindings) {
            CrawlerProxyView proxy = proxies.get(binding.getProxyConfigId());
            String key = "crawler:" + binding.getProxyConfigId();
            Instant until = cooldowns.get(key);
            CrawlerQueueProxyCooldown state = states.get(key);
            bindingViews.add(new QueueExecutorProxyView(binding.getProxyConfigId(),
                    proxy == null ? "已删除的代理" : Objects.toString(proxy.name(), "未命名代理"),
                    binding.getSortOrder(), binding.getCooldownSeconds(), until,
                    proxy != null && proxy.effectiveEnabled()
                            && (until == null || !until.isAfter(Instant.now())),
                    state == null ? 0 : state.getConsecutiveFailures(),
                    state == null ? null : state.getReason()));
        }
        List<ProxyCandidate> candidates = candidates(executor, site, proxies);
        List<QueueExecutorProxyStateView> proxyStates = executor.getProxyMode()
                == CrawlerQueueExecutor.ProxyMode.MIHOMO ? List.of() : candidates.stream().map(candidate -> {
                    CrawlerQueueProxyCooldown state = states.get(candidate.key());
                    return new QueueExecutorProxyStateView(candidate.key(), candidate.name(),
                            !isCooling(candidate, cooldowns, Instant.now()),
                            state == null ? 0 : state.getConsecutiveFailures(),
                            state == null ? null : state.getBlockedUntil(),
                            state == null ? null : state.getReason());
                }).toList();
        Instant now = Instant.now();
        int availableCount = (int) candidates.stream()
                .filter(candidate -> !isCooling(candidate, cooldowns, now)).count();
        Instant nextAvailable = candidates.stream()
                .map(candidate -> cooldowns.get(candidate.key()))
                .filter(Objects::nonNull)
                .filter(until -> until.isAfter(now))
                .min(Comparator.naturalOrder()).orElse(null);
        return new QueueExecutorView(executor.getId(), executor.getQueue().getId(),
                executor.getName(), executor.getDescription(), executor.isDefaultExecutor(),
                isExecutorEnabled(executor),
                executor.getProxyMode().name(), executor.getSelectionStrategy().name(),
                executor.getDefaultProxyCooldownSeconds(), bindingViews,
                availableCount, nextAvailable, proxyStates);
    }

    public void recordBoundProxySuccess(ProxyCandidate candidate) {
        ExecutionRoute route = executionRoute.get();
        if (route != null && !candidate.key().startsWith("mihomo:")) {
            cooldownRepository.resetFailures(route.queueId(), candidate.key());
        }
    }

    public void recordBoundProxyFailure(ProxyCandidate candidate, CrawlerSite site,
            String reason, long minimumSeconds) {
        ExecutionRoute route = executionRoute.get();
        if (route == null) return;
        var settings = crawlerSettingsService.settings();
        int threshold = site.getCooldownFailureThreshold() == null
                ? settings.maxConsecutiveFailures() : site.getCooldownFailureThreshold();
        int seconds = candidate.cooldownSeconds() == null
                ? settings.accessDeniedCooldownSeconds() : candidate.cooldownSeconds();
        Instant now = Instant.now();
        cooldownRepository.recordFailure(route.queueId(), candidate.key(), Math.max(1, threshold),
                now, now.plusSeconds(Math.max(Math.max(10, seconds), minimumSeconds)),
                reason.substring(0, Math.min(300, reason.length())));
    }

    private boolean isExecutorEnabled(CrawlerQueueExecutor executor) {
        // Existing rows without the new column retain their previous active behavior.
        return !Boolean.FALSE.equals(executor.getEnabled());
    }

    private void syncQueueConcurrency(CrawlerTaskQueue queue,
            List<CrawlerQueueExecutor> executors) {
        int enabledCount = (int) executors.stream().filter(this::isExecutorEnabled).count();
        if (!Objects.equals(queue.getMaxConcurrentTasks(), enabledCount)) {
            queue.setMaxConcurrentTasks(enabledCount);
            queueRepository.save(queue);
        }
    }

    private List<ProxyCandidate> candidates(CrawlerQueueExecutor executor, CrawlerSite site,
            Map<Long, CrawlerProxyView> proxies) {
        if (executor.getProxyMode() == CrawlerQueueExecutor.ProxyMode.MIHOMO) {
            return mihomoService.canExecute(executor.getId())
                    ? List.of(new ProxyCandidate("mihomo:" + executor.getId(), "Mihomo 托管代理",
                            mihomoService.proxyUrl(executor.getId()), executor.getDefaultProxyCooldownSeconds()))
                    : List.of();
        }
        if (executor.getProxyMode() == CrawlerQueueExecutor.ProxyMode.SELECTED) {
            return bindingRepository.findByExecutorIdOrderBySortOrderAscIdAsc(executor.getId())
                    .stream()
                    .map(binding -> selectedCandidate(binding, proxies.get(binding.getProxyConfigId())))
                    .filter(Objects::nonNull)
                    .toList();
        }
        Integer cooldown = executor.getDefaultProxyCooldownSeconds();
        if (site != null && site.getProxy() != null && !site.getProxy().isBlank()) {
            return List.of(new ProxyCandidate("site:" + site.getId(),
                    "网站默认代理", site.getProxy(), cooldown));
        }
        List<ProxyCandidate> global = proxySettingsService.crawlerProxies().stream()
                .filter(CrawlerProxyView::effectiveEnabled)
                .map(proxy -> new ProxyCandidate("crawler:" + proxy.id(),
                        Objects.toString(proxy.name(), "未命名代理"), proxy.url(), cooldown))
                .toList();
        return global.isEmpty()
                ? List.of(new ProxyCandidate("direct", "直接连接", null, cooldown))
                : global;
    }

    private ProxyCandidate selectedCandidate(CrawlerQueueExecutorProxy binding,
            CrawlerProxyView proxy) {
        if (proxy == null || !proxy.effectiveEnabled() || proxy.url() == null) return null;
        return new ProxyCandidate("crawler:" + proxy.id(),
                Objects.toString(proxy.name(), "未命名代理"), proxy.url(),
                binding.getCooldownSeconds());
    }

    private boolean isCooling(ProxyCandidate candidate, Map<String, Instant> cooldowns,
            Instant now) {
        Instant until = cooldowns.get(candidate.key());
        return until != null && until.isAfter(now);
    }

    private Map<String, Instant> cooldowns(Long queueId) {
        Map<String, Instant> values = new HashMap<>();
        for (CrawlerQueueProxyCooldown cooldown : cooldownRepository.findByQueueId(queueId)) {
            values.put(cooldown.getProxyKey(), cooldown.getBlockedUntil());
        }
        return values;
    }

    private Map<Long, CrawlerProxyView> proxyMap() {
        Map<Long, CrawlerProxyView> proxies = new HashMap<>();
        proxySettingsService.crawlerProxies().forEach(proxy -> proxies.put(proxy.id(), proxy));
        return proxies;
    }

    private void validate(QueueExecutorPayload payload) {
        if (payload == null || payload.name() == null || payload.name().isBlank()
                || payload.name().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "执行器名称需要填写且不能超过 100 个字符");
        }
        if (payload.description() != null && payload.description().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "执行器描述不能超过 500 个字符");
        }
        if (payload.proxyMode() == null || payload.selectionStrategy() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择代理配置和选用顺序");
        }
        Integer defaultCooldown = payload.defaultProxyCooldownSeconds();
        if (defaultCooldown != null && (defaultCooldown < 10 || defaultCooldown > 604800)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "代理冷却时间需在 10 至 604800 秒之间");
        }
        List<QueueExecutorProxyPayload> bindings = payload.proxies() == null
                ? List.of() : payload.proxies();
        if (bindings.size() > 20 || payload.proxyMode() == CrawlerQueueExecutor.ProxyMode.SELECTED
                && bindings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "自选代理需要配置 1 至 20 个代理");
        }
        Set<Long> ids = new HashSet<>();
        Set<Long> availableIds = proxyMap().keySet();
        for (QueueExecutorProxyPayload binding : bindings) {
            if (binding == null || binding.proxyConfigId() == null
                    || !ids.add(binding.proxyConfigId())
                    || !availableIds.contains(binding.proxyConfigId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "执行器代理配置包含重复或不存在的代理");
            }
            Integer cooldown = binding.cooldownSeconds();
            if (cooldown != null && (cooldown < 10 || cooldown > 604800)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "代理冷却时间需在 10 至 604800 秒之间");
            }
        }
    }

    private void saveBindings(CrawlerQueueExecutor executor, QueueExecutorPayload payload) {
        if (payload.proxyMode() != CrawlerQueueExecutor.ProxyMode.SELECTED) return;
        List<CrawlerQueueExecutorProxy> bindings = new ArrayList<>();
        for (int index = 0; index < payload.proxies().size(); index++) {
            QueueExecutorProxyPayload configured = payload.proxies().get(index);
            bindings.add(CrawlerQueueExecutorProxy.builder()
                    .executor(executor)
                    .proxyConfigId(configured.proxyConfigId())
                    .sortOrder(index)
                    .cooldownSeconds(configured.cooldownSeconds())
                    .build());
        }
        bindingRepository.saveAll(bindings);
    }

    private String normalizeDescription(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CrawlerTaskQueue ownedQueue(User user, Long queueId) {
        CrawlerTaskQueue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "队列不存在"));
        Long ownerId = queue.getUser() != null ? queue.getUser().getId()
                : queue.getSite() == null ? null : queue.getSite().getUser().getId();
        if (!Objects.equals(ownerId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "队列不存在");
        }
        return queue;
    }

    private CrawlerQueueExecutor ownedExecutor(Long queueId, Long executorId) {
        CrawlerQueueExecutor executor = executorRepository.findById(executorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "执行器不存在"));
        if (!Objects.equals(executor.getQueue().getId(), queueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "执行器不存在");
        }
        return executor;
    }
}
