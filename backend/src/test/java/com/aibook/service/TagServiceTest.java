package com.aibook.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Tag;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.TagRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TagServiceTest {
    private final TagRepository repository = mock(TagRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final TagService service = new TagService(repository, books);
    private final User user = User.builder().id(1L).build();

    @Test
    void largeListUsesOneGroupedCountAndKeepsUnusedAndPendingTagsCorrect() {
        List<Tag> tags = java.util.stream.LongStream.rangeClosed(1, 500)
                .mapToObj(id -> Tag.builder().id(id).name("标签" + id).user(user)
                        .pending(id == 500).build()).toList();
        when(repository.findByUserOrderByNameAsc(user)).thenReturn(tags);
        BookRepository.TagBookCount count = mock(BookRepository.TagBookCount.class);
        when(count.getTagId()).thenReturn(1L);
        when(count.getCnt()).thenReturn(12L);
        when(books.countBooksByTag(user)).thenReturn(List.of(count));
        var result = service.getTags(user);
        assertThat(result).hasSize(499);
        assertThat(result.getFirst().getBookCount()).isEqualTo(12L);
        assertThat(result.get(1).getBookCount()).isZero();
        verify(books).countBooksByTag(user);
        verify(books, never()).countByUserAndTagsContainingAndDeletedAtIsNull(any(), any());
        assertThat(service.getPendingTags(user)).hasSize(1);
        verifyNoMoreInteractions(books);
    }

    @Test
    void externalTagsWaitForApprovalAndExistingTagsRemainUsable() {
        Tag existing = Tag.builder().id(1L).name("已有").user(user).build();
        Tag pending = Tag.builder().id(2L).name("待加入").pending(true).user(user).build();
        when(repository.findByNameIgnoreCaseAndUser("已有", user)).thenReturn(existing);
        when(repository.findByNameIgnoreCaseAndUser("待加入", user)).thenReturn(pending);
        when(repository.save(any(Tag.class))).thenAnswer(call -> call.getArgument(0));
        assertThat(service.getOrCreateTags(List.of("已有", "待加入", " 新标签 "), user, "网站"))
                .containsExactly(existing);
        var captured = org.mockito.ArgumentCaptor.forClass(Tag.class);
        verify(repository).save(captured.capture());
        assertThat(captured.getValue().isPending()).isTrue();
        assertThat(captured.getValue().getName()).isEqualTo("新标签");
    }

    @Test
    void approvalMovesTagBetweenAccountLists() {
        Tag tag = Tag.builder().id(2L).name("标签").pending(true).user(user).build();
        when(repository.findById(2L)).thenReturn(Optional.of(tag));
        when(repository.findByUserOrderByNameAsc(user)).thenReturn(List.of(tag));
        assertThat(service.getTags(user)).isEmpty();
        assertThat(service.getPendingTags(user)).hasSize(1);
        service.managePendingTags(List.of(2L, 2L), user, true);
        assertThat(service.getTags(user)).hasSize(1);
        assertThat(service.getPendingTags(user)).isEmpty();
        assertThatThrownBy(() -> service.managePendingTags(List.of(2L), user, false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsOtherAccountsAndDeletesPendingTags() {
        Tag tag = Tag.builder().id(2L).name("标签").pending(true).user(user).build();
        when(repository.findById(2L)).thenReturn(Optional.of(tag));
        assertThatThrownBy(() -> service.managePendingTags(List.of(2L),
                User.builder().id(3L).build(), true)).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).saveAll(any());
        service.managePendingTags(List.of(2L), user, false);
        verify(repository).deleteAll(List.of(tag));
    }
}
