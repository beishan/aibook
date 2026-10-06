package com.aibook.dto.crawler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class MihomoDtos {
    public record NodeGroupPayload(String name, String controlGroup, String proxyUrl, List<String> nodes) { }
    public record NodeGroupView(Long id, Long systemProxyId, String name, String controlGroup,
            String proxyUrl, List<String> nodes) { }
    public record ReferencePayload(Long systemProxyId, Long nodeGroupId, String switchingMode,
            String manualNode, boolean failover, int failureThreshold, int cooldownSeconds,
            int rotationSeconds, int rotationChapters, int rotationTasks, boolean randomOrder) { }
    public record ConnectionTestView(boolean controllerAvailable, boolean proxyAvailable,
            String message, Integer delay) { }
    public record PolicyPayload(String controllerUrl, String secret, boolean clearSecret,
            String proxyUrl, String groupName, List<String> nodes, boolean failover,
            int failureThreshold, int cooldownSeconds, int rotationSeconds,
            int rotationChapters, int rotationTasks, boolean randomOrder) { }
    public record SwitchEvent(Instant time, String from, String to, String reason, boolean success) { }
    public record PolicyView(String controllerUrl, boolean secretConfigured, String proxyUrl,
            String groupName, List<String> nodes, boolean failover, int failureThreshold,
            int cooldownSeconds, int rotationSeconds, int rotationChapters, int rotationTasks,
            boolean randomOrder, String currentNode, long activeMillis, long chapters,
            long tasks, Instant lastSwitchAt, Instant retryAt, String lastError,
            Map<String, Instant> cooldowns, List<SwitchEvent> events, Long systemProxyId,
            Long nodeGroupId, String switchingMode, String manualNode,
            Map<String, Integer> nodeFailures, Map<String, String> cooldownReasons) { }
    public record NodeView(String name, String type, Boolean alive, Integer delay, Instant checkedAt) { }
    public record GroupView(String name, String currentNode, List<String> nodes) { }
    public record CatalogView(List<GroupView> groups, List<NodeView> nodes) { }
    public record NodeRequest(String name) { }
    public record DelayView(String name, boolean alive, Integer delay, Instant checkedAt) { }
}
