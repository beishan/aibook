package com.aibook.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.recommendation.*;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RecommendationServiceTest {
    RecommendationRuleRepository rules = mock(RecommendationRuleRepository.class);
    RecommendationBatchRepository batches = mock(RecommendationBatchRepository.class);
    RecommendationCandidates candidates = mock(RecommendationCandidates.class);
    UserRepository users = mock(UserRepository.class);
    ObjectMapper mapper = new ObjectMapper();
    RecommendationService service = new RecommendationService(rules, batches, candidates, users, mapper);
    User user = User.builder().id(42L).username("reader").build();
    RecommendationRule rule = new RecommendationRule();
    LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    List<RecommendationBatch> saved = new ArrayList<>();

    RecommendationFilter filter(int count, int avoid, boolean strict) {
        return new RecommendationFilter(List.of(), true, List.of(), false, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null,
                count, avoid, strict);
    }

    @BeforeEach
    void setup() throws Exception {
        rule.setId(3L);
        rule.setUser(user);
        rule.setName("每日随缘");
        rule.setEnabled(true);
        rule.setFilterJson(mapper.writeValueAsString(filter(2, 7, false)));
        when(rules.ownedIds(42L)).thenReturn(List.of(3L));
        when(rules.lockOwned(3L, 42L)).thenReturn(Optional.of(rule));
        when(rules.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(batches.findFirstByRuleIdAndRecommendationDateOrderByIdDesc(3L, date))
                .thenAnswer(i -> saved.isEmpty() ? Optional.empty() : Optional.of(saved.get(saved.size() - 1)));
        when(batches.findByRuleIdAndRecommendationDateGreaterThanEqual(eq(3L), any()))
                .thenAnswer(i -> List.copyOf(saved));
        when(batches.saveAndFlush(any())).thenAnswer(i -> {
            RecommendationBatch b = i.getArgument(0);
            b.setId((long) saved.size() + 1);
            saved.add(b);
            return b;
        });
        when(candidates.ids(eq(user), any(), eq(date))).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(candidates.books(eq(user), anyList())).thenAnswer(i -> {
            List<Long> ids = i.getArgument(1);
            return ids.stream().map(id -> Map.<String, Object>of("id", id, "title", "书" + id)).toList();
        });
    }

    @Test
    void dailyResultsArePersistedAndReusedAcrossLoads() {
        var first = service.today(user);
        var second = service.today(user);
        assertThat(first).isEqualTo(second);
        assertThat(first.groups().get(0).books()).hasSize(2);
        assertThat(saved).hasSize(1);
        verify(candidates, times(1)).ids(eq(user), any(), eq(date));
        verify(rules, times(2)).lockOwned(3L, 42L);
    }

    @Test
    void rerollAvoidsEveryEarlierBatchIncludingTodays() {
        var first = service.today(user).groups().get(0);
        var next = service.reroll(user, 3L);
        assertThat(next.books()).doesNotContainAnyElementsOf(first.books());
        assertThat(saved).hasSize(2);
        assertThat(next.repeatedCount()).isZero();
        assertThat(service.today(user).groups().get(0)).isEqualTo(next);
    }

    @Test
    void fallbackAndStrictRulesNeverRelaxTheCandidateFilter() {
        var old = LocalDateTime.of(2026, 1, 1, 0, 0);
        var seen = Map.of(1L, old.plusDays(2), 2L, old, 3L, old.plusDays(1));
        assertThat(RecommendationService.select(List.of(1L, 2L, 3L, 4L), seen, 3, false))
                .containsExactly(4L, 2L, 3L);
        assertThat(RecommendationService.select(List.of(1L, 2L, 3L, 4L), seen, 3, true))
                .containsExactly(4L);
        assertThat(RecommendationService.select(List.of(1L, 1L), Map.of(), 6, false))
                .containsExactly(1L);
        assertThat(RecommendationService.select(List.of(), seen, 6, false)).isEmpty();
    }

    @Test
    void namesAndSortingKeepTodaysBooksButFilterEditsRegenerate() {
        service.today(user);
        service.save(user, 3L, new RecommendationRuleRequest("改名", true, 4, filter(2, 7, false)));
        assertThat(saved).hasSize(1);
        assertThat(rule.getName()).isEqualTo("改名");
        service.save(user, 3L, new RecommendationRuleRequest("改名", true, 4, filter(1, 7, false)));
        assertThat(saved).hasSize(2);
        assertThat(service.today(user).groups().get(0).books()).hasSize(1);
    }

    @Test
    void disabledGroupsAreHiddenAndEnablingRetainsCurrentBatch() {
        var first = service.today(user).groups().get(0).books();
        service.save(user, 3L, new RecommendationRuleRequest("暂停", false, 0, filter(2, 7, false)));
        assertThat(service.today(user).groups()).isEmpty();
        assertThatThrownBy(() -> service.reroll(user, 3L)).isInstanceOf(ResponseStatusException.class);
        service.save(user, 3L, new RecommendationRuleRequest("恢复", true, 0, filter(2, 7, false)));
        assertThat(service.today(user).groups().get(0).books()).isEqualTo(first);
        assertThat(saved).hasSize(1);
    }

    @Test
    void removedBooksAreHiddenWithoutSilentlyReplacingTheBatch() {
        service.today(user);
        when(candidates.books(eq(user), anyList())).thenReturn(List.of());
        var result = service.today(user).groups().get(0);
        assertThat(result.books()).isEmpty();
        assertThat(result.invalidCount()).isEqualTo(2);
        assertThat(saved).hasSize(1);
    }

    @Test
    void previewDoesNotGenerateAndForeignRulesCannotBeReadOrChanged() {
        assertThat(service.preview(user, new RecommendationRuleRequest("预览", true, 0, filter(6, 7, false))))
                .isEqualTo(4);
        verifyNoInteractions(batches);
        User other = User.builder().id(99L).build();
        when(rules.lockOwned(3L, 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reroll(other, 3L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(other, 3L)).isInstanceOf(ResponseStatusException.class);
        verify(rules, never()).delete(any());
    }

    @Test
    void zeroMatchesAreSavedSoReloadDoesNotDrawAgain() {
        when(candidates.ids(eq(user), any(), eq(date))).thenReturn(List.of());
        assertThat(service.today(user).groups().get(0).books()).isEmpty();
        service.today(user);
        assertThat(saved).hasSize(1);
        verify(candidates, times(1)).ids(eq(user), any(), eq(date));
    }

    @Test
    void validatesQuantitiesNamesAndIntervals() {
        for (var f : List.of(filter(0, 7, false), filter(31, 7, false), filter(6, -1, false), filter(6, 366, false))) {
            assertThatThrownBy(() -> RecommendationService.validate(
                    new RecommendationRuleRequest("组", true, 0, f))).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> RecommendationService.validate(
                new RecommendationRuleRequest(" ", true, 0, filter(6, 7, false))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatCode(() -> RecommendationService.validate(
                new RecommendationRuleRequest("组", true, 0, filter(1, 0, true)))).doesNotThrowAnyException();
    }
    @Test
    void editsWhilePausedTakeEffectWhenGroupIsEnabledAgain() {
        service.today(user);
        service.save(user, 3L, new RecommendationRuleRequest("组", false, 0, filter(1, 7, false)));
        assertThat(saved).hasSize(1);
        service.save(user, 3L, new RecommendationRuleRequest("组", true, 0, filter(1, 7, false)));
        assertThat(service.today(user).groups().get(0).books()).hasSize(1);
        assertThat(saved).hasSize(2);
    }

    @Test
    void newAccountCanCreateAndGroupLimitCannotBeBypassed() {
        when(users.findByIdForLogPolicyUpdate(42L)).thenReturn(Optional.of(user));
        when(rules.ownedIds(42L)).thenReturn(List.of());
        when(rules.saveAndFlush(any())).thenAnswer(i -> {
            RecommendationRule created = i.getArgument(0);
            created.setId(4L);
            return created;
        });
        var result = service.save(user, null, new RecommendationRuleRequest("新组", false, 0, filter(6, 7, false)));
        assertThat(result.id()).isEqualTo(4L);
        verify(users).findByIdForLogPolicyUpdate(42L);
        when(rules.ownedIds(42L)).thenReturn(java.util.stream.LongStream.range(1, 31).boxed().toList());
        assertThatThrownBy(() -> service.save(user, null,
                new RecommendationRuleRequest("超额", false, 0, filter(6, 7, false))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void groupsDrawIndependentlyAndMayShareTheSameBooks() {
        var second = new RecommendationRule();
        second.setId(4L);
        second.setUser(user);
        second.setName("另一组");
        second.setEnabled(true);
        second.setFilterJson(rule.getFilterJson());
        when(rules.ownedIds(42L)).thenReturn(List.of(3L, 4L));
        when(rules.lockOwned(4L, 42L)).thenReturn(Optional.of(second));
        when(candidates.ids(eq(user), any(), eq(date))).thenReturn(List.of(1L, 2L));
        var groups = service.today(user).groups();
        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).books()).containsExactlyInAnyOrderElementsOf(groups.get(1).books());
        verify(batches).findByRuleIdAndRecommendationDateGreaterThanEqual(3L, date.minusDays(7));
        verify(batches).findByRuleIdAndRecommendationDateGreaterThanEqual(4L, date.minusDays(7));
    }

}
