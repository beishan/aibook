package com.aibook.audiobook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ScanDirectoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AudioChapterTitleBatchTest {
    private final AudioStore store = mock(AudioStore.class);
    private final AudiobookService service = new AudiobookService(
            store, new ObjectMapper(), mock(BookRepository.class), mock(ScanDirectoryRepository.class));
    private final User user = User.builder().id(1L).username("reader").build();
    private final AudioTypes.ChapterTitleBatch batch = new AudioTypes.ChapterTitleBatch(
            4L, List.of(new AudioTypes.ChapterTitleChange(9L, "第一章广告", "第一章")));

    @Test
    void updatesOnlyMatchingTitleAndEndsSession() {
        when(store.edition(1L, 2L)).thenReturn(Map.of("id", 2L));
        when(store.update(startsWith("UPDATE audiobook_editions"), eq(2L), eq(4L))).thenReturn(1);
        when(store.update(startsWith("UPDATE audio_chapters"), eq("第一章"), eq(9L), eq(2L), eq("第一章广告")))
                .thenReturn(1);
        service.updateChapterTitles(user, 2L, batch);
        verify(store).update(startsWith("UPDATE listening_sessions"), eq(2L));
    }

    @Test
    void rejectsStaleVersionBeforeChangingChapter() {
        when(store.edition(1L, 2L)).thenReturn(Map.of("id", 2L));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateChapterTitles(user, 2L, batch));
        assertEquals(409, error.getStatusCode().value());
        verify(store, never()).update(startsWith("UPDATE audio_chapters"), any(Object[].class));
    }

    @Test
    void rejectsDuplicateChapterIds() {
        when(store.edition(1L, 2L)).thenReturn(Map.of("id", 2L));
        var duplicate = new AudioTypes.ChapterTitleBatch(4L, List.of(
                new AudioTypes.ChapterTitleChange(9L, "一广告", "一"),
                new AudioTypes.ChapterTitleChange(9L, "一广告", "一")));
        assertThrows(IllegalArgumentException.class, () -> service.updateChapterTitles(user, 2L, duplicate));
        verify(store, never()).update(anyString(), any(Object[].class));
    }
}
