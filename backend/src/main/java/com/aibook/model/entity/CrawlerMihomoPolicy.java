package com.aibook.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/** One executor owns one controller/group; policies are scoped through the queue owner. */
@Entity
@Table(name = "crawler_mihomo_policies", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"controller_url", "group_name"})})
@Getter
@Setter
@NoArgsConstructor
public class CrawlerMihomoPolicy {
    @Id
    private Long executorId;
    private Long systemProxyId;
    private Long nodeGroupId;
    @Column(length = 20)
    private String switchingMode;
    @Column(length = 200)
    private String manualNode;
    @Column(name = "controller_url", nullable = false, length = 500)
    private String controllerUrl;
    @Column(length = 500)
    private String secret;
    @Column(nullable = false, length = 500)
    private String proxyUrl;
    @Column(name = "group_name", nullable = false, length = 200)
    private String groupName;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String nodesJson = "[]";
    private boolean failover = true;
    private int failureThreshold = 2;
    private int cooldownSeconds = 300;
    private int rotationSeconds;
    private int rotationChapters;
    private int rotationTasks;
    private boolean randomOrder;
    private String currentNode;
    private int failures;
    private long activeMillis;
    private long chapters;
    private long tasks;
    private Instant lastActivityAt;
    private Instant lastSwitchAt;
    private Instant retryAt;
    @Column(length = 300)
    private String lastError;
    @Column(columnDefinition = "TEXT")
    private String cooldownsJson = "{}";
    @Column(columnDefinition = "TEXT")
    private String eventsJson = "[]";
}
