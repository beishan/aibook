package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.SystemResourcesDTO;
import com.aibook.dto.SystemResourcesDTO.ResourceAvailability;
import com.aibook.dto.SystemResourcesDTO.ResourceMetricDTO;
import com.aibook.dto.SystemResourcesDTO.ResourceScope;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.repository.ScanDirectoryRepository;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemResourceServiceTest {

    @TempDir Path temporaryDirectory;

    @Test
    void prefersCgroupV2LimitsAndCachesCollection() throws Exception {
        Path cgroup = Files.createDirectories(temporaryDirectory.resolve("cgroup"));
        Files.writeString(cgroup.resolve("cgroup.controllers"), "cpu memory");
        Files.writeString(cgroup.resolve("cpu.stat"), "usage_usec 4200\n");
        Files.writeString(cgroup.resolve("cpu.max"), "200000 100000\n");
        Files.writeString(cgroup.resolve("memory.current"), "512\n");
        Files.writeString(cgroup.resolve("memory.max"), "1024\n");

        ScanDirectoryRepository repository = mock(ScanDirectoryRepository.class);
        ScanDirectory first = ScanDirectory.builder()
                .path(temporaryDirectory.resolve("books-a").toString()).build();
        ScanDirectory second = ScanDirectory.builder()
                .path(temporaryDirectory.resolve("books-b").toString()).build();
        Files.createDirectories(Path.of(first.getPath()));
        Files.createDirectories(Path.of(second.getPath()));
        when(repository.findAll()).thenReturn(List.of(first, second));

        SystemResourceService service = service(repository, cgroup, temporaryDirectory.resolve("proc"));

        SystemResourcesDTO firstResult = service.getResources();
        SystemResourcesDTO cachedResult = service.getResources();

        assertThat(firstResult.scope()).isEqualTo(ResourceScope.CONTAINER);
        assertThat(firstResult.cpu().status()).isEqualTo(ResourceAvailability.AVAILABLE);
        assertThat(firstResult.cpu().usagePercent()).isNull();
        assertThat(firstResult.memory().usedBytes()).isEqualTo(512L);
        assertThat(firstResult.memory().totalBytes()).isEqualTo(1024L);
        assertThat(firstResult.memory().usagePercent()).isEqualTo(50d);
        assertThat(firstResult.disk().status()).isEqualTo(ResourceAvailability.AVAILABLE);
        assertThat(firstResult.disks()).hasSize(1);
        assertThat(firstResult.disks().getFirst().label()).isEqualTo("书籍存储");
        assertThat(cachedResult).isSameAs(firstResult);
        verify(repository).findAll();
    }

    @Test
    void preservesFractionalContainerCpuCapacity() {
        assertThat(SystemResourceService.positiveCpuCapacity(50_000, 100_000)).isEqualTo(0.5d);
    }

    @Test
    void keepsPartialMetricVisibleInOverallStatus() {
        ResourceMetricDTO available = new ResourceMetricDTO(
                25d, 1L, 4L, ResourceAvailability.AVAILABLE);
        ResourceMetricDTO partial = new ResourceMetricDTO(
                50d, 2L, 4L, ResourceAvailability.PARTIAL);

        assertThat(SystemResourceService.combinedStatus(available, available, partial))
                .isEqualTo(ResourceAvailability.PARTIAL);
    }

    @Test
    void supportsCgroupV1ControllerLayout() throws Exception {
        Path cgroup = Files.createDirectories(temporaryDirectory.resolve("cgroup-v1"));
        Path cpu = Files.createDirectories(cgroup.resolve("cpu/slice/test"));
        Path cpuacct = Files.createDirectories(cgroup.resolve("cpuacct/slice/test"));
        Path memory = Files.createDirectories(cgroup.resolve("memory/slice/test"));
        Files.writeString(cpu.resolve("cpu.cfs_quota_us"), "100000\n");
        Files.writeString(cpu.resolve("cpu.cfs_period_us"), "100000\n");
        Files.writeString(cpuacct.resolve("cpuacct.usage"), "1000000\n");
        Files.writeString(memory.resolve("memory.usage_in_bytes"), "256\n");
        Files.writeString(memory.resolve("memory.limit_in_bytes"), "1024\n");
        Path proc = temporaryDirectory.resolve("proc-v1");
        Files.writeString(proc, "2:cpu,cpuacct:/slice/test\n3:memory:/slice/test\n");

        ScanDirectoryRepository repository = mock(ScanDirectoryRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        SystemResourceService service = service(repository, cgroup, proc);

        SystemResourcesDTO result = service.getResources();

        assertThat(result.scope()).isEqualTo(ResourceScope.CONTAINER);
        assertThat(result.cpu().usagePercent()).isNull();
        assertThat(result.memory().usedBytes()).isEqualTo(256L);
        assertThat(result.memory().totalBytes()).isEqualTo(1024L);
    }

    @Test
    void returnsPartialResponseWhenCgroupAndOperatingSystemMetricsCannotBeRead() throws Exception {
        ScanDirectoryRepository repository = mock(ScanDirectoryRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        Path absent = temporaryDirectory.resolve("absent");
        SystemResourceService service = new SystemResourceService(
                repository,
                absent,
                absent,
                absent,
                temporaryDirectory,
                () -> mock(OperatingSystemMXBean.class));

        SystemResourcesDTO result = service.getResources();

        assertThat(result.scope()).isEqualTo(ResourceScope.HOST);
        assertThat(result.cpu().status()).isEqualTo(ResourceAvailability.UNAVAILABLE);
        assertThat(result.memory().status()).isEqualTo(ResourceAvailability.UNAVAILABLE);
        assertThat(result.disk().status()).isEqualTo(ResourceAvailability.AVAILABLE);
        assertThat(result.status()).isEqualTo(ResourceAvailability.PARTIAL);
    }

    private SystemResourceService service(
            ScanDirectoryRepository repository, Path cgroup, Path procCgroup) {
        return new SystemResourceService(
                repository,
                cgroup,
                procCgroup,
                temporaryDirectory.resolve("no-docker-marker"),
                temporaryDirectory,
                () -> mock(OperatingSystemMXBean.class));
    }
}
