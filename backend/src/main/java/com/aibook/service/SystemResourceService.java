package com.aibook.service;

import com.aibook.dto.SystemResourcesDTO;
import com.aibook.dto.SystemResourcesDTO.DiskVolumeDTO;
import com.aibook.dto.SystemResourcesDTO.ResourceAvailability;
import com.aibook.dto.SystemResourcesDTO.ResourceMetricDTO;
import com.aibook.dto.SystemResourcesDTO.ResourceScope;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.repository.ScanDirectoryRepository;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Collects only resources visible to this application. It never accesses the Docker socket and does
 * not require privileged containers. Linux containers are measured from cgroup files when available.
 */
@Service
public class SystemResourceService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(3);
    private static final long UNLIMITED_CGROUP_MEMORY = 1L << 60;

    private final ScanDirectoryRepository scanDirectoryRepository;
    private final Path cgroupRoot;
    private final Path procSelfCgroup;
    private final Path dockerEnvironment;
    private final Path workingDirectory;
    private final Supplier<OperatingSystemMXBean> operatingSystemBeanSupplier;

    private volatile CachedResources cached;
    private CpuSample previousCpuSample;

    @Autowired
    public SystemResourceService(ScanDirectoryRepository scanDirectoryRepository) {
        this(
                scanDirectoryRepository,
                Path.of("/sys/fs/cgroup"),
                Path.of("/proc/self/cgroup"),
                Path.of("/.dockerenv"),
                Path.of(System.getProperty("user.dir", ".")),
                ManagementFactory::getOperatingSystemMXBean);
    }

    SystemResourceService(
            ScanDirectoryRepository scanDirectoryRepository,
            Path cgroupRoot,
            Path procSelfCgroup,
            Path dockerEnvironment,
            Path workingDirectory,
            Supplier<OperatingSystemMXBean> operatingSystemBeanSupplier) {
        this.scanDirectoryRepository = scanDirectoryRepository;
        this.cgroupRoot = cgroupRoot;
        this.procSelfCgroup = procSelfCgroup;
        this.dockerEnvironment = dockerEnvironment;
        this.workingDirectory = workingDirectory;
        this.operatingSystemBeanSupplier = operatingSystemBeanSupplier;
    }

    public SystemResourcesDTO getResources() {
        CachedResources current = cached;
        Instant now = Instant.now();
        if (current != null && Duration.between(current.createdAt(), now).compareTo(CACHE_TTL) < 0) {
            return current.resources();
        }
        synchronized (this) {
            current = cached;
            now = Instant.now();
            if (current != null && Duration.between(current.createdAt(), now).compareTo(CACHE_TTL) < 0) {
                return current.resources();
            }
            SystemResourcesDTO resources = collect(now);
            cached = new CachedResources(now, resources);
            return resources;
        }
    }

    private SystemResourcesDTO collect(Instant collectedAt) {
        CgroupVersion cgroupVersion = cgroupVersion();
        ResourceMetricDTO cpu = unavailableMetric();
        ResourceMetricDTO memory = unavailableMetric();
        boolean cgroupUsed = false;

        if (cgroupVersion != CgroupVersion.NONE) {
            try {
                cpu = collectCgroupCpu(cgroupVersion, collectedAt);
                cgroupUsed = cpu.status() != ResourceAvailability.UNAVAILABLE;
            } catch (Exception ignored) {
                // A missing controller must not make the system settings endpoint unavailable.
            }
            try {
                memory = collectCgroupMemory(cgroupVersion);
                cgroupUsed = cgroupUsed || memory.status() != ResourceAvailability.UNAVAILABLE;
            } catch (Exception ignored) {
                // Fall through to JVM-visible metrics below.
            }
        }

        if (cpu.status() == ResourceAvailability.UNAVAILABLE) {
            cpu = collectOperatingSystemCpu();
        }
        if (memory.status() == ResourceAvailability.UNAVAILABLE) {
            memory = collectOperatingSystemMemory();
        }

        ResourceMetricDTO disk;
        List<DiskVolumeDTO> disks;
        try {
            DiskCollection diskCollection = collectDisks();
            disk = diskCollection.metric();
            disks = diskCollection.volumes();
        } catch (Exception ignored) {
            disk = unavailableMetric();
            disks = List.of();
        }

        ResourceScope scope = cgroupUsed
                ? ResourceScope.CONTAINER
                : isContainerEnvironment() ? ResourceScope.UNKNOWN : ResourceScope.HOST;
        return new SystemResourcesDTO(
                scope,
                combinedStatus(cpu, memory, disk),
                collectedAt,
                cpu,
                memory,
                disk,
                disks);
    }

    private CgroupVersion cgroupVersion() {
        if (Files.isReadable(cgroupRoot.resolve("cgroup.controllers"))) {
            return CgroupVersion.V2;
        }
        if (Files.isReadable(procSelfCgroup)) {
            return CgroupVersion.V1;
        }
        return CgroupVersion.NONE;
    }

    private ResourceMetricDTO collectCgroupCpu(CgroupVersion version, Instant collectedAt)
            throws IOException {
        long usageMicros;
        double availableCores;
        if (version == CgroupVersion.V2) {
            usageMicros = statValue(cgroupRoot.resolve("cpu.stat"), "usage_usec");
            String[] max = readRequired(cgroupRoot.resolve("cpu.max")).trim().split("\\s+");
            availableCores = max.length >= 2 && !"max".equals(max[0])
                    ? positiveCpuCapacity(
                            Double.parseDouble(max[0]),
                            Double.parseDouble(max[1]))
                    : Runtime.getRuntime().availableProcessors();
        } else {
            usageMicros = readLong(cgroupFile("cpuacct", "cpuacct.usage")) / 1_000L;
            long quota = readLong(cgroupFile("cpu", "cpu.cfs_quota_us"));
            long period = readLong(cgroupFile("cpu", "cpu.cfs_period_us"));
            availableCores = quota > 0 && period > 0
                    ? positiveCpuCapacity(quota, period)
                    : Runtime.getRuntime().availableProcessors();
        }

        CpuSample sample = new CpuSample(usageMicros, collectedAt);
        CpuSample previous = previousCpuSample;
        previousCpuSample = sample;
        if (previous == null) {
            return new ResourceMetricDTO(null, null, null, ResourceAvailability.AVAILABLE);
        }
        long elapsedMicros = Duration.between(previous.at(), collectedAt).toNanos() / 1_000L;
        long consumedMicros = usageMicros - previous.usageMicros();
        if (elapsedMicros <= 0 || consumedMicros < 0) {
            return new ResourceMetricDTO(null, null, null, ResourceAvailability.AVAILABLE);
        }
        double usagePercent = Math.min(100d, Math.max(0d, consumedMicros * 100d / elapsedMicros / availableCores));
        return new ResourceMetricDTO(usagePercent, null, null, ResourceAvailability.AVAILABLE);
    }

    private ResourceMetricDTO collectCgroupMemory(CgroupVersion version) throws IOException {
        Path usagePath = version == CgroupVersion.V2
                ? cgroupRoot.resolve("memory.current")
                : cgroupFile("memory", "memory.usage_in_bytes");
        Path limitPath = version == CgroupVersion.V2
                ? cgroupRoot.resolve("memory.max")
                : cgroupFile("memory", "memory.limit_in_bytes");
        String limitValue = readRequired(limitPath).trim();
        if ("max".equals(limitValue)) {
            return unavailableMetric();
        }
        long total = Long.parseLong(limitValue);
        if (total <= 0 || total >= UNLIMITED_CGROUP_MEMORY) {
            return unavailableMetric();
        }
        long used = Math.max(0, Math.min(total, readLong(usagePath)));
        return metric(used, total, ResourceAvailability.AVAILABLE);
    }

    private ResourceMetricDTO collectOperatingSystemCpu() {
        try {
            OperatingSystemMXBean bean = operatingSystemBeanSupplier.get();
            if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
                double load = extended.getCpuLoad();
                if (load >= 0d) {
                    return new ResourceMetricDTO(
                            Math.min(100d, load * 100d), null, null, ResourceAvailability.AVAILABLE);
                }
            }
        } catch (RuntimeException ignored) {
            // Return a partial response below.
        }
        return unavailableMetric();
    }

    private ResourceMetricDTO collectOperatingSystemMemory() {
        try {
            OperatingSystemMXBean bean = operatingSystemBeanSupplier.get();
            if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
                long total = extended.getTotalMemorySize();
                long free = extended.getFreeMemorySize();
                if (total > 0 && free >= 0) {
                    return metric(Math.max(0, total - free), total, ResourceAvailability.AVAILABLE);
                }
            }
        } catch (RuntimeException ignored) {
            // Return a partial response below.
        }
        return unavailableMetric();
    }

    private DiskCollection collectDisks() {
        Map<String, FileStore> stores = new LinkedHashMap<>();
        try {
            for (ScanDirectory directory : scanDirectoryRepository.findAll()) {
                if (directory.getPath() == null || directory.getPath().isBlank()) {
                    continue;
                }
                try {
                    Path path = Path.of(directory.getPath());
                    if (Files.isDirectory(path)) {
                        FileStore store = Files.getFileStore(path);
                        stores.putIfAbsent(store.name() + "\\u0000" + store.type(), store);
                    }
                } catch (Exception ignored) {
                    // Invalid or unavailable paths do not prevent other directories from being counted.
                }
            }
        } catch (RuntimeException ignored) {
            // A database issue should not prevent the application directory fallback.
        }
        boolean scanningStorage = !stores.isEmpty();
        if (!scanningStorage) {
            try {
                stores.put("application", Files.getFileStore(workingDirectory));
            } catch (IOException ignored) {
                return new DiskCollection(unavailableMetric(), List.of());
            }
        }

        long used = 0;
        long total = 0;
        List<DiskVolumeDTO> volumes = new ArrayList<>();
        int unavailable = 0;
        int index = 1;
        for (FileStore store : stores.values()) {
            String label = scanningStorage
                    ? stores.size() == 1 ? "书籍存储" : "书籍存储 " + index++
                    : "应用存储";
            try {
                long volumeTotal = store.getTotalSpace();
                long volumeUsed = Math.max(0, volumeTotal - store.getUsableSpace());
                if (volumeTotal <= 0) {
                    throw new IOException("invalid file store capacity");
                }
                used = Math.addExact(used, volumeUsed);
                total = Math.addExact(total, volumeTotal);
                volumes.add(new DiskVolumeDTO(label, volumeUsed, volumeTotal, ResourceAvailability.AVAILABLE));
            } catch (Exception ignored) {
                unavailable++;
                volumes.add(new DiskVolumeDTO(label, null, null, ResourceAvailability.UNAVAILABLE));
            }
        }
        ResourceAvailability status = unavailable == volumes.size()
                ? ResourceAvailability.UNAVAILABLE
                : unavailable == 0 ? ResourceAvailability.AVAILABLE : ResourceAvailability.PARTIAL;
        return new DiskCollection(
                total > 0 ? metric(used, total, status) : unavailableMetric(), List.copyOf(volumes));
    }

    private Path cgroupFile(String controller, String fileName) throws IOException {
        String relative = cgroupRelativePath(controller);
        Path mountedRoot = cgroupRoot.resolve(controller);
        Path controllerPath = relative.isEmpty() || "/".equals(relative)
                ? mountedRoot.resolve(fileName)
                : mountedRoot.resolve(relative.substring(1)).resolve(fileName);
        if (Files.isReadable(controllerPath)) {
            return controllerPath;
        }
        Path directPath = cgroupRoot.resolve(fileName);
        if (Files.isReadable(directPath)) {
            return directPath;
        }
        return controllerPath;
    }

    private String cgroupRelativePath(String requiredController) throws IOException {
        for (String line : Files.readAllLines(procSelfCgroup)) {
            String[] parts = line.split(":", 3);
            if (parts.length == 3) {
                for (String controller : parts[1].split(",")) {
                    if (requiredController.equals(controller)) {
                        return parts[2];
                    }
                }
            }
        }
        return "/";
    }

    private boolean isContainerEnvironment() {
        if (Files.exists(dockerEnvironment)) {
            return true;
        }
        try {
            return Files.readString(procSelfCgroup).toLowerCase(Locale.ROOT)
                    .matches("(?s).*?(docker|kubepods|containerd|podman).*?");
        } catch (IOException ignored) {
            return false;
        }
    }

    private static ResourceMetricDTO metric(long used, long total, ResourceAvailability status) {
        return new ResourceMetricDTO(total > 0 ? used * 100d / total : null, used, total, status);
    }

    static double positiveCpuCapacity(double quota, double period) {
        if (quota <= 0 || period <= 0) {
            throw new IllegalArgumentException("CPU quota and period must be positive");
        }
        return quota / period;
    }

    private static ResourceMetricDTO unavailableMetric() {
        return new ResourceMetricDTO(null, null, null, ResourceAvailability.UNAVAILABLE);
    }

    static ResourceAvailability combinedStatus(ResourceMetricDTO... metrics) {
        int available = 0;
        boolean partial = false;
        for (ResourceMetricDTO metric : metrics) {
            if (metric.status() != ResourceAvailability.UNAVAILABLE) {
                available++;
            }
            partial = partial || metric.status() == ResourceAvailability.PARTIAL;
        }
        if (available == 0) {
            return ResourceAvailability.UNAVAILABLE;
        }
        return available == metrics.length && !partial
                ? ResourceAvailability.AVAILABLE
                : ResourceAvailability.PARTIAL;
    }

    private static String readRequired(Path path) throws IOException {
        return Files.readString(path);
    }

    private static long readLong(Path path) throws IOException {
        return Long.parseLong(readRequired(path).trim());
    }

    private static long statValue(Path path, String name) throws IOException {
        for (String line : Files.readAllLines(path)) {
            String[] values = line.trim().split("\\s+", 2);
            if (values.length == 2 && name.equals(values[0])) {
                return Long.parseLong(values[1]);
            }
        }
        throw new IOException("cgroup stat value not found: " + name);
    }

    private enum CgroupVersion { V1, V2, NONE }

    private record CpuSample(long usageMicros, Instant at) {}

    private record CachedResources(Instant createdAt, SystemResourcesDTO resources) {}

    private record DiskCollection(ResourceMetricDTO metric, List<DiskVolumeDTO> volumes) {}
}
