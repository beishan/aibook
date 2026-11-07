package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.CrawlerTaskLog;
import com.aibook.model.entity.CrawlerTaskLogRetention;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerTaskLogRepository;
import com.aibook.repository.CrawlerTaskLogRetentionRepository;
import com.aibook.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class CrawlerTaskLogManagementServiceTest {
    private final User user = User.builder().id(1L).username("owner").build();
    private final CrawlerTaskLogRepository logs = mock(CrawlerTaskLogRepository.class);
    private final CrawlerTaskLogRetentionRepository policies = mock(CrawlerTaskLogRetentionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final org.springframework.transaction.PlatformTransactionManager transactions =
            mock(org.springframework.transaction.PlatformTransactionManager.class);
    private final CrawlerTaskLogManagementService service =
            new CrawlerTaskLogManagementService(logs, policies, users, transactions);

    @BeforeEach
    void setup() {
        when(users.findByIdForLogPolicyUpdate(1L)).thenReturn(Optional.of(user));
        when(transactions.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        when(policies.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void listingIsAccountScopedAndIdentifiesLegacyTaskTypeAndRetainedStatus() {
        var entry = CrawlerTaskLog.builder().id(12L).user(user).taskId("id").description("事件")
                .details("任务ID：id；任务类型：BOOK_FULL_CRAWL；网站：站点")
                .retained(true).failed(false).build();
        var legacy = CrawlerTaskLog.builder().id(13L).user(user).taskId("old").description("事件")
                .details("旧日志正文；任务类型：BOOK_FULL_CRAWL；网站：站点").build();
        when(logs.searchForManagement(eq(user), eq("%a!%!_!!%"), eq("BOOK_FULL_CRAWL"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry, legacy)));
        var page = service.list(user, 0, 500, "a%_!", "BOOK_FULL_CRAWL");
        assertThat(page.getContent().getFirst().taskType()).isEqualTo("BOOK_FULL_CRAWL");
        assertThat(page.getContent().getFirst().retained()).isTrue();
        assertThat(page.getContent().get(1).taskType()).isEqualTo("UNKNOWN");
        var paging = ArgumentCaptor.forClass(Pageable.class);
        verify(logs).searchForManagement(eq(user), anyString(), eq("BOOK_FULL_CRAWL"), paging.capture());
        assertThat(paging.getValue().getPageSize()).isEqualTo(100);
        assertThatThrownBy(() -> service.list(user, 0, 20, "", "INVALID"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void selectedActionsUseOwnerAndRejectInvalidOrOversizedIds() {
        when(logs.setRetained(user, List.of(1L, 2L), true)).thenReturn(2);
        when(logs.deleteSelected(user, List.of(1L, 2L))).thenReturn(1);
        assertThat(service.retain(user, List.of(1L, 2L, 1L), true)).isEqualTo(2);
        assertThat(service.delete(user, List.of(1L, 2L))).isEqualTo(1);
        verify(logs).setRetained(user, List.of(1L, 2L), true);
        verify(logs).deleteSelected(user, List.of(1L, 2L));
        assertThatThrownBy(() -> service.delete(user, List.of())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.retain(user, java.util.Collections.nCopies(101, 1L), true))
                .isInstanceOf(ResponseStatusException.class);
        User stranger = User.builder().id(2L).build();
        assertThat(service.delete(stranger, List.of(1L))).isZero();
    }

    @Test
    void policyDefaultsToForeverAndValidatesBeforePersistence() {
        assertThat(service.rules(user)).allSatisfy(rule -> {
            assertThat(rule.retentionDays()).isZero();
            assertThat(rule.automatic()).isFalse();
        });
        assertThatThrownBy(() -> service.saveRules(user,
                List.of(new CrawlerTaskLogManagementService.Rule("BOOK_FULL_CRAWL", -1, true))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.saveRules(user,
                List.of(new CrawlerTaskLogManagementService.Rule("UNKNOWN", 10, true))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.saveRules(user,
                List.of(new CrawlerTaskLogManagementService.Rule("BOOK_CONTENT", 1, null))))
                .isInstanceOf(ResponseStatusException.class);
        verify(policies, never()).save(any());
    }

    @Test
    void savesPerTypePolicyForCurrentAccountUnderAccountLock() {
        service.saveRules(user, List.of(new CrawlerTaskLogManagementService.Rule("BOOK_FULL_CRAWL", 7, true)));
        var captured = ArgumentCaptor.forClass(CrawlerTaskLogRetention.class);
        verify(policies).save(captured.capture());
        assertThat(captured.getValue().getUser()).isSameAs(user);
        assertThat(captured.getValue().getTaskType()).isEqualTo("BOOK_FULL_CRAWL");
        assertThat(captured.getValue().getRetentionDays()).isEqualTo(7);
        assertThat(captured.getValue().getAutomatic()).isTrue();
        verify(users).findByIdForLogPolicyUpdate(1L);
    }

    @Test
    void scheduledCleanupIsolatesFailuresBetweenAccounts() {
        var other = User.builder().id(2L).build();
        var a = CrawlerTaskLogRetention.builder().user(user).taskType("BOOK_FULL_CRAWL")
                .retentionDays(7).automatic(true).build();
        var b = CrawlerTaskLogRetention.builder().user(other).taskType("BOOK_CONTENT")
                .retentionDays(3).automatic(true).build();
        when(users.findByIdForLogPolicyUpdate(2L)).thenReturn(Optional.of(other));
        when(policies.findAutomaticUserIds()).thenReturn(List.of(1L, 2L));
        when(policies.findByUserId(1L)).thenReturn(List.of(a));
        when(policies.findByUserId(2L)).thenReturn(List.of(b));
        when(logs.deleteExpired(eq(1L), anyString(), any())).thenThrow(new IllegalStateException("数据库错误"));
        service.scheduledCleanup();
        verify(logs).deleteExpired(eq(2L), eq("BOOK_CONTENT"), any());
        verify(transactions).rollback(any());
        verify(transactions).commit(any());
    }

    @Test
    void manualAndScheduledCleanupUseOnlyOwnRulesAndConfiguredType() {
        var active = CrawlerTaskLogRetention.builder().user(user).taskType("BOOK_FULL_CRAWL")
                .retentionDays(7).automatic(true).build();
        var forever = CrawlerTaskLogRetention.builder().user(user).taskType("BOOK_CONTENT")
                .retentionDays(0).automatic(true).build();
        var manual = CrawlerTaskLogRetention.builder().user(user).taskType("BOOK_METADATA")
                .retentionDays(30).automatic(false).build();
        when(policies.findByUserId(1L)).thenReturn(List.of(active, forever, manual));
        when(policies.findAutomaticUserIds()).thenReturn(List.of(1L));
        when(logs.deleteExpired(eq(1L), eq("BOOK_FULL_CRAWL"), any(LocalDateTime.class))).thenReturn(4);
        when(logs.deleteExpired(eq(1L), eq("BOOK_METADATA"), any(LocalDateTime.class))).thenReturn(3);
        assertThat(service.cleanup(user)).isEqualTo(7);
        service.scheduledCleanup();
        verify(logs, times(2)).deleteExpired(eq(1L), eq("BOOK_FULL_CRAWL"), any(LocalDateTime.class));
        verify(logs, times(1)).deleteExpired(eq(1L), eq("BOOK_METADATA"), any(LocalDateTime.class));
        verify(logs, never()).deleteExpired(eq(1L), eq("BOOK_CONTENT"), any());
    }
}
