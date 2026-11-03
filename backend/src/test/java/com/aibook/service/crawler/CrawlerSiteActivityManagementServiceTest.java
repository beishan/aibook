package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerSiteActivityRetention;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerSiteActivityRepository;
import com.aibook.repository.CrawlerSiteActivityRetentionRepository;
import com.aibook.repository.CrawlerSiteRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

class CrawlerSiteActivityManagementServiceTest {
    private final User user = User.builder().id(1L).build();
    private final CrawlerSite site = CrawlerSite.builder().id(9L).user(user).build();
    private final CrawlerSiteRepository sites = mock(CrawlerSiteRepository.class);
    private final CrawlerSiteActivityRepository activities = mock(CrawlerSiteActivityRepository.class);
    private final CrawlerSiteActivityRetentionRepository policies = mock(CrawlerSiteActivityRetentionRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final CrawlerSiteActivityManagementService service =
            new CrawlerSiteActivityManagementService(sites, activities, policies, transactions);

    @BeforeEach
    void setup() {
        when(sites.findByIdAndUser(9L, user)).thenReturn(Optional.of(site));
        when(sites.findLockedByIdAndUser(9L, user)).thenReturn(Optional.of(site));
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }

    @Test
    void defaultIsForeverAndPoliciesAreSiteScopedAndValidated() {
        assertThat(service.policy(user, 9L)).isEqualTo(new CrawlerSiteActivityManagementService.Policy(0, false));
        assertThatThrownBy(() -> service.savePolicy(user, 9L,
                new CrawlerSiteActivityManagementService.Policy(-1, true)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.savePolicy(user, 9L,
                new CrawlerSiteActivityManagementService.Policy(4, null)))
                .isInstanceOf(ResponseStatusException.class);
        var requested = new CrawlerSiteActivityManagementService.Policy(30, true);
        assertThat(service.savePolicy(user, 9L, requested)).isEqualTo(requested);
        var argument = ArgumentCaptor.forClass(CrawlerSiteActivityRetention.class);
        verify(policies).save(argument.capture());
        assertThat(argument.getValue().getSite()).isSameAs(site);
        assertThat(argument.getValue().getRetentionDays()).isEqualTo(30);
        verify(sites).findLockedByIdAndUser(9L, user);
        assertThatThrownBy(() -> service.policy(User.builder().id(2L).build(), 9L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void selectedOperationsAndClearOnlyTargetOwnedSite() {
        when(activities.setRetained(site, List.of(1L, 2L), true)).thenReturn(2);
        when(activities.deleteSelected(site, List.of(1L))).thenReturn(1);
        assertThat(service.retain(user, 9L, List.of(1L, 2L, 1L), true)).isEqualTo(2);
        assertThat(service.deleteSelected(user, 9L, List.of(1L))).isEqualTo(1);
        service.clear(user, 9L);
        verify(activities).deleteUnretained(site);
        assertThatThrownBy(() -> service.retain(user, 9L, List.of(), true))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.deleteSelected(user, 9L, java.util.Collections.nCopies(101, 1L)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.clear(User.builder().id(2L).build(), 9L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void scheduledCleanupUsesCurrentPolicyAndIsolatesFailures() {
        CrawlerSite other = CrawlerSite.builder().id(10L).user(user).build();
        var policy = CrawlerSiteActivityRetention.builder().site(site).retentionDays(7).automatic(true).build();
        var otherPolicy = CrawlerSiteActivityRetention.builder().site(other).retentionDays(3).automatic(true).build();
        when(policies.findAutomaticSiteIds()).thenReturn(List.of(9L, 10L));
        when(sites.findById(9L)).thenReturn(Optional.of(site));
        when(sites.findById(10L)).thenReturn(Optional.of(other));
        when(sites.findLockedByIdAndUser(10L, user)).thenReturn(Optional.of(other));
        when(policies.findBySiteId(9L)).thenReturn(Optional.of(policy));
        when(policies.findBySiteId(10L)).thenReturn(Optional.of(otherPolicy));
        when(activities.deleteExpired(eq(site), any(LocalDateTime.class))).thenThrow(new IllegalStateException("错误"));
        service.scheduledCleanup();
        verify(activities).deleteExpired(eq(other), any(LocalDateTime.class));
        verify(transactions).rollback(any());
        verify(transactions).commit(any());
        reset(activities);
        otherPolicy.setAutomatic(false);
        policy.setRetentionDays(0);
        service.scheduledCleanup();
        verify(activities, never()).deleteExpired(any(), any());
    }
}
