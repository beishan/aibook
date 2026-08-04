package com.aibook.dto;

import java.time.Instant;
import java.util.List;

/** Resource usage visible to the application process. */
public record SystemResourcesDTO(
        ResourceScope scope,
        ResourceAvailability status,
        Instant collectedAt,
        ResourceMetricDTO cpu,
        ResourceMetricDTO memory,
        ResourceMetricDTO disk,
        List<DiskVolumeDTO> disks) {

    public enum ResourceScope {
        CONTAINER,
        HOST,
        UNKNOWN
    }

    public enum ResourceAvailability {
        AVAILABLE,
        PARTIAL,
        UNAVAILABLE
    }

    public record ResourceMetricDTO(
            Double usagePercent,
            Long usedBytes,
            Long totalBytes,
            ResourceAvailability status) {}

    /** The label is deliberately generic so configured host paths are never exposed. */
    public record DiskVolumeDTO(
            String label, Long usedBytes, Long totalBytes, ResourceAvailability status) {}
}
