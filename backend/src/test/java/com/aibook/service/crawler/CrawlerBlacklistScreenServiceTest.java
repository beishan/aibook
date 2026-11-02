package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.crawler.CrawlerBlacklistScreenDtos.*;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.util.CrawlerAutoBlacklist;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

class CrawlerBlacklistScreenServiceTest {
    private final User user = User.builder().id(1L).build();
    private final CrawlerSite site = CrawlerSite.builder().id(7L).user(user).siteName("来源站")
            .autoBlacklistJson(CrawlerAutoBlacklist.encode(new CrawlerAutoBlacklist.Configuration(
                    true, List.of("禁用词"), List.of("屏蔽标签")))).build();
    private CrawlerBlacklistScreenJobRepository jobs;
    private CrawlerBlacklistScreenResultRepository results;
    private CrawlerBookRepository books;
    private CrawlerSiteRepository sites;
    private PlatformTransactionManager transactions;

    @BeforeEach
    void setUp() {
        jobs = mock(CrawlerBlacklistScreenJobRepository.class);
        results = mock(CrawlerBlacklistScreenResultRepository.class);
        books = mock(CrawlerBookRepository.class);
        sites = mock(CrawlerSiteRepository.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jobs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sites.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(site));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void asyncScanUsesSelectedSiteBoundedBatchesAndPersistsMatchesWithoutBlacklisting(
            boolean discoveryOnly) throws Exception {
        CrawlerBook first = book(10L, "含禁用词的书");
        CrawlerBook normal = book(11L, "普通书");
        CrawlerBook tagged = book(12L, "标签书");
        tagged.setTags("其他，屏蔽标签");
        when(books.blacklistScreenUpperId(user)).thenReturn(12L);
        when(books.countBlacklistScreenCandidates(eq(user), anyCollection(), eq(12L), eq(discoveryOnly))).thenReturn(3L);
        when(books.blacklistScreenCandidates(eq(user), anyCollection(), eq(0L), eq(12L), eq(discoveryOnly), any()))
                .thenReturn(List.of(first, normal));
        when(books.blacklistScreenCandidates(eq(user), anyCollection(), eq(11L), eq(12L), eq(discoveryOnly), any()))
                .thenReturn(List.of(tagged));
        CountDownLatch finished = new CountDownLatch(1);
        var capturedJob = ArgumentCaptor.forClass(CrawlerBlacklistScreenJob.class);
        doAnswer(inv -> {
            CrawlerBlacklistScreenJob job = inv.getArgument(0);
            if ("COMPLETED".equals(job.getStatus())) finished.countDown();
            return job;
        }).when(jobs).save(any());
        try (var service = service()) {
            var submitted = service.submit(user, new Request(7L, discoveryOnly ? "DISCOVERED" : "MANAGED"));
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            verify(jobs, atLeastOnce()).save(capturedJob.capture());
            var job = capturedJob.getAllValues().getLast();
            assertThat(job.getId()).isEqualTo(submitted.id());
            assertThat(job.getScope()).isEqualTo(discoveryOnly ? "DISCOVERED" : "MANAGED");
            assertThat(job.getProcessed()).isEqualTo(3);
            assertThat(job.getMatched()).isEqualTo(2);
            assertThat(job.getFinishedAt()).isNotNull();
            verify(books, times(3)).blacklistScreenCandidates(eq(user), eq(Set.of(7L)),
                    anyLong(), eq(12L), eq(discoveryOnly), eq(PageRequest.of(0, 200)));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CrawlerBlacklistScreenResult>> saved = ArgumentCaptor.forClass(List.class);
            verify(results, times(2)).saveAll(saved.capture());
            assertThat(saved.getAllValues().stream().flatMap(List::stream).toList())
                    .extracting(CrawlerBlacklistScreenResult::getBookId).containsExactly(10L, 12L);
            var firstResult = saved.getAllValues().getFirst().getFirst();
            assertThat(firstResult.getReason()).contains("书名", "禁用词");
            assertThat(CrawlerAutoBlacklist.decodeDetails(firstResult.getMatchDetailsJson()).getFirst().evidence())
                    .isEqualTo("含禁用词的书");
            verify(books, never()).updateDiscoveryStatus(any(), any(), any(), anyBoolean(), any());
            verify(books, never()).blacklistAllScreenResults(any(), anyString(), any());
        }
    }

    @Test
    void rejectsForeignSitesMissingRulesAndDuplicateActiveJobs() {
        try (var service = service()) {
            assertThatThrownBy(() -> service.submit(user, new Request(99L)))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
            site.setAutoBlacklistJson(null);
            assertThatThrownBy(() -> service.submit(user, new Request(null)))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("规则");
            when(jobs.findFirstByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(job("RUNNING")));
            assertThatThrownBy(() -> service.submit(user, new Request(null)))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
            verify(jobs, never()).save(any());
        }
    }

    @Test
    void allApplyUsesEntireSnapshotRatherThanClientOrCurrentPageIds() {
        var job = job("COMPLETED");
        when(jobs.findByIdAndUser(job.getId(), user)).thenReturn(Optional.of(job));
        when(books.blacklistAllScreenResults(eq(user), eq(job.getId()), any())).thenReturn(203);
        try (var service = service()) {
            assertThat(service.apply(user, job.getId(), new ApplyRequest(true, List.of(10L))).affectedCount())
                    .isEqualTo(203);
            verify(results, never()).matchedIds(anyString(), any());
            verify(books, never()).updateDiscoveryStatus(any(), any(), any(), anyBoolean(), any());
        }
    }

    @Test
    void selectedApplyRestrictsIdsToOwnedCompletedSnapshotAndKeepsTimestampSemantics() {
        var job = job("COMPLETED");
        when(jobs.findByIdAndUser(job.getId(), user)).thenReturn(Optional.of(job));
        when(results.matchedIds(job.getId(), List.of(10L, 11L))).thenReturn(List.of(10L, 11L));
        when(books.updateDiscoveryStatus(any(), eq(user), any(), eq(true), any())).thenReturn(2);
        try (var service = service()) {
            assertThat(service.apply(user, job.getId(), new ApplyRequest(false, List.of(10L, 11L, 10L)))
                    .affectedCount()).isEqualTo(2);
            verify(books).updateDiscoveryStatus(eq(List.of(10L, 11L)), eq(user),
                    eq(CrawlerBook.DiscoveryStatus.BLACKLISTED), eq(true), any());
            assertThatThrownBy(() -> service.apply(user, job.getId(), new ApplyRequest(false, List.of(99L))))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("本次筛选");
            assertThatThrownBy(() -> service.apply(user, job.getId(), new ApplyRequest(false, List.of())))
                    .isInstanceOf(ResponseStatusException.class);
            job.setStatus("RUNNING");
            assertThatThrownBy(() -> service.apply(user, job.getId(), new ApplyRequest(true, List.of())))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
            User stranger = User.builder().id(2L).build();
            assertThatThrownBy(() -> service.apply(stranger, job.getId(), new ApplyRequest(true, List.of())))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        }
    }

    @Test
    void resultPagesReflectLiveBlacklistAndDeletionAndRemainOwnerScoped() {
        var job = job("COMPLETED");
        when(jobs.findByIdAndUser(job.getId(), user)).thenReturn(Optional.of(job));
        var evidence = new CrawlerAutoBlacklist.MatchDetail("KEYWORD", "简介", "词", "筛选时的词与简介");
        var a = CrawlerBlacklistScreenResult.builder().job(job).bookId(10L).bookName("甲")
                .matchDetailsJson(CrawlerAutoBlacklist.detailsJson(List.of(evidence))).build();
        var b = CrawlerBlacklistScreenResult.builder().job(job).bookId(11L).bookName("乙").build();
        when(results.findByJobIdOrderByIdAsc(job.getId(), PageRequest.of(1, 20)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(1, 20), 42));
        CrawlerBook current = book(10L, "甲");
        current.setDiscoveryStatus(CrawlerBook.DiscoveryStatus.BLACKLISTED);
        current.setDescription("后来修改的简介，不再命中");
        when(books.findAllById(List.of(10L, 11L))).thenReturn(List.of(current));
        try (var service = service()) {
            var page = service.page(user, job.getId(), 1, 20);
            assertThat(page.getTotalElements()).isEqualTo(42);
            assertThat(page.getContent().getFirst().blacklisted()).isTrue();
            assertThat(page.getContent().getFirst().matchDetails()).containsExactly(evidence);
            assertThat(page.getContent().getLast().matchDetails()).isEmpty();
            assertThat(page.getContent().getLast().available()).isFalse();
            User stranger = User.builder().id(2L).build();
            assertThatThrownBy(() -> service.page(stranger, job.getId(), 0, 20))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        }
    }

    @Test
    void failureIsRecordedAndRestartRecoveryDoesNotPretendToBeRunning() {
        var job = job("RUNNING");
        when(books.blacklistScreenCandidates(any(), anyCollection(), anyLong(), anyLong(), anyBoolean(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        try (var service = service()) {
            service.process(job, Map.of(7L, CrawlerAutoBlacklist.decode(site.getAutoBlacklistJson())), 100L);
            assertThat(job.getStatus()).isEqualTo("FAILED");
            assertThat(job.getErrorMessage()).contains("筛选失败");
            assertThat(job.getFinishedAt()).isNotNull();
            service.recoverInterruptedJobs();
            verify(jobs).interruptUnfinished();
        }
    }

    @Test
    void failedBatchDoesNotCountRolledBackMatchesAsPersistedResults() {
        var job = job("RUNNING");
        when(books.blacklistScreenCandidates(any(), anyCollection(), anyLong(), anyLong(), anyBoolean(), any()))
                .thenReturn(List.of(book(10L, "禁用词")));
        when(results.saveAll(anyList())).thenThrow(new IllegalStateException("disk full"));
        try (var service = service()) {
            service.process(job, Map.of(7L, CrawlerAutoBlacklist.decode(site.getAutoBlacklistJson())), 100L);
            assertThat(job.getStatus()).isEqualTo("FAILED");
            assertThat(job.getProcessed()).isZero();
            assertThat(job.getMatched()).isZero();
            verify(transactions).rollback(any());
        }
    }

    @Test
    void latestResultsAreScopedAndLegacyJobsRemainManaged() {
        var legacy = job("COMPLETED");
        var discovered = job("COMPLETED");
        discovered.setScope("DISCOVERED");
        when(jobs.latestForScope(user, "MANAGED", PageRequest.of(0, 1))).thenReturn(List.of(legacy));
        when(jobs.latestForScope(user, "DISCOVERED", PageRequest.of(0, 1))).thenReturn(List.of(discovered));
        try (var service = service()) {
            assertThat(service.latest(user, null).scope()).isEqualTo("MANAGED");
            assertThat(service.latest(user, "DISCOVERED").id()).isEqualTo(discovered.getId());
            assertThat(service.latest(user, "MANAGED").id()).isEqualTo(legacy.getId());
            assertThatThrownBy(() -> service.latest(user, "ALL"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
            assertThatThrownBy(() -> service.submit(user, new Request(null, "ALL")))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
            verify(jobs, never()).save(any());
        }
    }

    private CrawlerBook book(Long id, String name) {
        return CrawlerBook.builder().id(id).site(site).bookName(name)
                .crawlStatus(CrawlerBook.CrawlStatus.COMPLETED).build();
    }

    private CrawlerBlacklistScreenJob job(String status) {
        return CrawlerBlacklistScreenJob.builder().user(user).status(status).build();
    }

    private CrawlerBlacklistScreenService service() {
        return new CrawlerBlacklistScreenService(jobs, results, books, sites, transactions);
    }
}
