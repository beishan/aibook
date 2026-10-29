package com.aibook.audiobook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aibook.model.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AudioOfflineServiceTest {
    @Test
    void overlappingListeningIntervalsAreNotCountedTwice() {
        var store=mock(AudioStore.class);
        var service=new AudioOfflineService(store,mock(AudiobookService.class));
        ReflectionTestUtils.setField(service,"zone","UTC");
        when(store.rows(startsWith("SELECT start_ms"),eq(1L),eq(1000L),eq(10000L)))
                .thenReturn(List.of(Map.of("startMs",2000L,"endMs",5000L),Map.of("startMs",4000L,"endMs",9000L)));
        service.account(1L,1000,10000);
        verify(store,times(2)).update(startsWith("INSERT INTO listening_daily_activity"),eq(1L),any(java.sql.Date.class),eq(1000L));
    }

    @Test
    void offlineConflictDoesNotDiscardLocalOrServerPosition() {
        var store=mock(AudioStore.class);
        var audio=mock(AudiobookService.class);
        var service=new AudioOfflineService(store,audio);
        var user=User.builder().id(1L).build();
        when(store.edition(1L,2L)).thenReturn(Map.of("contentRevision",1L));
        when(store.rows(startsWith("SELECT applied_revision"),eq(1L),eq("event"))).thenReturn(List.of());
        when(audio.progress(1L,2L)).thenReturn(Map.of("revision",9L,"positionMs",10000L));
        var request=new AudioOfflineService.OfflineProgress("event",8,1,7L,4000,false,false,List.of());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.apply(user,2,request)).getStatusCode().value());
        verify(store,never()).update(anyString(),any(Object[].class));
    }
}
