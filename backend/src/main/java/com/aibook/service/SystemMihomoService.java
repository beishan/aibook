package com.aibook.service;

import com.aibook.dto.ProxySettingsDtos.SystemProxyRequest;
import com.aibook.dto.crawler.MihomoDtos.*;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.crawler.CrawlerMihomoService;
import com.aibook.service.crawler.MihomoApiClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SystemMihomoService {
    private final SystemProxyConfigRepository proxies;
    private final MihomoNodeGroupRepository groups;
    private final CrawlerMihomoPolicyRepository policies;
    private final MihomoApiClient api;
    private final ObjectMapper mapper;

    public record ConnectionPayload(Long id, SystemProxyRequest config) { }
    public record ResolvedConnection(String controllerUrl, String secret, String proxyUrl,
            String controlGroup, List<String> nodes) { }

    public CatalogView browse(ConnectionPayload request) throws Exception {
        SystemProxyConfig connection = draft(request);
        return catalog(api.proxies(connection.getControllerUrl(), connection.getControllerSecret()));
    }

    public ConnectionTestView test(ConnectionPayload request) throws Exception {
        SystemProxyConfig connection = draft(request);
        try {
            api.proxies(connection.getControllerUrl(), connection.getControllerSecret());
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) throw exception;
            return new ConnectionTestView(false, false, "控制 API 不可用，请检查地址、端口及密钥", null);
        }
        URI proxy = URI.create(connection.getUrl());
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort()))).build()) {
            long started = System.nanoTime();
            HttpResponse<Void> response = client.send(HttpRequest.newBuilder(
                    URI.create("https://www.gstatic.com/generate_204"))
                    .timeout(Duration.ofSeconds(8)).build(), HttpResponse.BodyHandlers.discarding());
            boolean valid = response.statusCode() >= 200 && response.statusCode() < 300;
            return new ConnectionTestView(true, valid, valid ? "控制 API 与 HTTP 代理入口均可用"
                    : "控制 API 可用，HTTP 代理测试返回非成功状态", (int) ((System.nanoTime() - started) / 1000000));
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) throw exception;
            return new ConnectionTestView(true, false, "控制 API 可用，HTTP 代理入口不可用，请检查地址与端口", null);
        }
    }

    public DelayView delay(Long proxyId, String node) throws Exception {
        SystemProxyConfig proxy = source(proxyId, false);
        JsonNode catalog = api.proxies(proxy.getControllerUrl(), proxy.getControllerSecret()).path("proxies");
        if (node == null || !leaf(catalog.path(node))) bad("请选择有效的具体节点");
        try {
            return new DelayView(node, true, api.delay(proxy.getControllerUrl(), proxy.getControllerSecret(), node), Instant.now());
        } catch (MihomoApiClient.ApiException exception) {
            if (exception.status() != 503 && exception.status() != 504) throw exception;
            return new DelayView(node, false, null, Instant.now());
        }
    }

    public List<NodeGroupView> list(Long proxyId, Long userId) {
        return groups.findBySystemProxyIdAndUserIdOrderByIdAsc(proxyId, userId).stream().map(this::view).toList();
    }

    public NodeGroupView save(Long proxyId, Long groupId, Long userId, NodeGroupPayload payload) throws Exception {
        SystemProxyConfig proxy = source(proxyId, false);
        MihomoNodeGroup group = groupId == null ? new MihomoNodeGroup() : ownedGroup(proxyId, groupId, userId);
        if (payload.name() == null || payload.name().isBlank() || payload.name().length() > 100) bad("节点组名称需填写且最多 100 字符");
        if (payload.controlGroup() == null || payload.controlGroup().isBlank() || payload.controlGroup().length() > 200) bad("请选择受控 Selector 代理组");
        String proxyUrl = CrawlerMihomoService.normalizeUrl(payload.proxyUrl(), true);
        if (groupId != null && policies.existsByNodeGroupId(groupId)
                && (!Objects.equals(group.getControlGroup(), payload.controlGroup())
                || !Objects.equals(group.getProxyUrl(), proxyUrl))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "节点组已绑定执行器，请先解除绑定再修改受控组或入口");
        }
        List<String> nodes = payload.nodes();
        if (nodes == null || nodes.isEmpty() || nodes.size() > 100 || new HashSet<>(nodes).size() != nodes.size()
                || nodes.stream().anyMatch(n -> n == null || n.isBlank() || n.length() > 200)) bad("请选择 1 至 100 个不重复具体节点");
        JsonNode all = api.proxies(proxy.getControllerUrl(), proxy.getControllerSecret()).path("proxies");
        JsonNode control = all.path(payload.controlGroup());
        if (!"Selector".equals(control.path("type").asText())) bad("受控代理组必须为 Selector 类型");
        Set<String> members = new HashSet<>();
        control.path("all").forEach(n -> members.add(n.asText()));
        for (String node : nodes) {
            if (!members.contains(node) || !leaf(all.path(node))) bad("节点不在受控组内或不是具体节点：" + node);
        }
        group.setUserId(userId);
        group.setSystemProxyId(proxyId);
        group.setName(payload.name().trim());
        group.setControlGroup(payload.controlGroup());
        group.setProxyUrl(proxyUrl);
        group.setNodesJson(mapper.writeValueAsString(nodes));
        return view(groups.save(group));
    }

    public void delete(Long proxyId, Long groupId, Long userId) {
        MihomoNodeGroup group = ownedGroup(proxyId, groupId, userId);
        if (policies.existsByNodeGroupId(groupId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "节点组正在被执行器使用，请先更换执行器配置");
        }
        groups.delete(group);
    }

    public ResolvedConnection resolve(Long proxyId, Long groupId, Long userId) {
        SystemProxyConfig proxy = source(proxyId, true);
        MihomoNodeGroup group = ownedGroup(proxyId, groupId, userId);
        return new ResolvedConnection(proxy.getControllerUrl(), proxy.getControllerSecret(),
                group.getProxyUrl(), group.getControlGroup(), nodeNames(group));
    }

    private SystemProxyConfig source(Long id, boolean requireEnabled) {
        SystemProxyConfig config = proxies.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Mihomo 系统代理不存在"));
        if (config.getProxyType() != SystemProxyConfig.ProxyType.MIHOMO
                || requireEnabled && !Boolean.TRUE.equals(config.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mihomo 系统代理已停用或类型已改变");
        }
        return config;
    }

    private SystemProxyConfig draft(ConnectionPayload request) {
        SystemProxyRequest payload = request.config();
        SystemProxyConfig saved = request.id() == null ? null : proxies.findById(request.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "系统代理不存在"));
        SystemProxyConfig config = new SystemProxyConfig();
        config.setControllerUrl(CrawlerMihomoService.normalizeUrl(payload.controllerUrl(), false));
        config.setUrl(CrawlerMihomoService.normalizeUrl(payload.url(), true));
        String secret = payload.clearSecret() ? null : payload.secret() != null && !payload.secret().isBlank()
                ? payload.secret() : saved == null ? null : saved.getControllerSecret();
        if (secret != null && (secret.length() > 500 || secret.contains("\n") || secret.contains("\r"))) bad("Mihomo 密钥格式无效");
        config.setControllerSecret(secret);
        return config;
    }

    private MihomoNodeGroup ownedGroup(Long proxyId, Long groupId, Long userId) {
        if (groupId == null) bad("请选择节点组");
        MihomoNodeGroup group = groups.findById(groupId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "节点组不存在"));
        if (!Objects.equals(group.getSystemProxyId(), proxyId) || !Objects.equals(group.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "节点组不存在");
        }
        return group;
    }

    private List<String> nodeNames(MihomoNodeGroup group) {
        try {
            return mapper.readValue(group.getNodesJson(), new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("节点组数据无法解析", exception);
        }
    }

    private NodeGroupView view(MihomoNodeGroup group) {
        return new NodeGroupView(group.getId(), group.getSystemProxyId(), group.getName(),
                group.getControlGroup(), group.getProxyUrl(), nodeNames(group));
    }

    public static CatalogView catalog(JsonNode root) {
        List<GroupView> groups = new ArrayList<>();
        List<NodeView> nodes = new ArrayList<>();
        root.path("proxies").fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if ("Selector".equals(value.path("type").asText())) {
                List<String> members = new ArrayList<>();
                value.path("all").forEach(n -> members.add(n.asText()));
                groups.add(new GroupView(entry.getKey(), value.path("now").asText(), members));
            } else if (leaf(value)) {
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

    private static boolean leaf(JsonNode node) {
        return !node.isMissingNode() && !node.has("all") && !node.path("type").asText().isBlank()
                && !Set.of("Direct", "Reject", "RejectDrop", "Pass", "Compatible").contains(node.path("type").asText());
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
