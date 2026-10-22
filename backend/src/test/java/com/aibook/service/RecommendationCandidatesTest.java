package com.aibook.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.recommendation.RecommendationFilter;
import com.aibook.model.entity.User;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.*;

class RecommendationCandidatesTest {
    @Test
    void filtersComposeWithOwnerTrashAndScanVisibilityWithoutDuplicateJoins() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var categories = mock(CategoryService.class);
        var user = User.builder().id(42L).build();
        when(categories.getCategoryAndDescendantIds(1L, user)).thenReturn(List.of(1L, 2L));
        var service = new RecommendationCandidates(jdbc, categories);
        var f = new RecommendationFilter(List.of(1L), true, List.of(3L, 4L), true, List.of(5L),
                List.of(6L), List.of(7L), List.of("UNREADING"), List.of("epub"), List.of("UPLOAD"),
                true, false, true, 90, 7, 30, 6, 7, false);
        service.ids(user, f, LocalDate.of(2026, 10, 22));
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForList(sql.capture(), args.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("b.user_id = :user", "b.deleted_at IS NULL",
                "d.library_visible = true", "COUNT(DISTINCT t.tag_id)", "NOT EXISTS",
                "p.user_id=:user", "p.last_read_at >= :readSince", "b.id FROM books");
        assertThat(args.getValue().getValue("categories")).isEqualTo(List.of(1L, 2L));
        assertThat(args.getValue().getValue("user")).isEqualTo(42L);
        assertThat(args.getValue().getValue("readSince")).isEqualTo(LocalDate.of(2026, 9, 22).atStartOfDay());
        assertThat(args.getValue().getValue("tagCount")).isEqualTo(2);
    }

    @Test
    void renderingRechecksVisibilityButDoesNotReapplyMutableReadingFilters() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var service = new RecommendationCandidates(jdbc, mock(CategoryService.class));
        var user = User.builder().id(42L).build();
        service.books(user, List.of(1L));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getValue()).contains(RecommendationCandidates.VISIBLE, "b.id IN (:ids)");
        assertThat(service.books(user, List.of())).isEmpty();
    }
    @Test
    void deletedCategoryNeverBroadensTheRuleOrBreaksOtherGroups() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var categories = mock(CategoryService.class);
        var user = User.builder().id(42L).build();
        when(categories.getCategoryAndDescendantIds(8L, user)).thenThrow(
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        var service = new RecommendationCandidates(jdbc, categories);
        var f = new RecommendationFilter(List.of(8L), true, List.of(), false, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null, 6, 7, false);
        service.ids(user, f, LocalDate.of(2026, 10, 22));
        var args = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForList(contains("b.category_id IN (:categories)"), args.capture(), eq(Long.class));
        assertThat(args.getValue().getValue("categories")).isEqualTo(List.of(8L));
    }

}
