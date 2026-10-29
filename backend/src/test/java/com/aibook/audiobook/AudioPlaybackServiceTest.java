package com.aibook.audiobook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AudioPlaybackServiceTest {
    private AudioStore store;
    private AudiobookService audio;
    private AudioPlaybackService service;
    private AudioOfflineService offline;
    private final User user = User.builder().id(1L).username("reader").build();

    @BeforeEach
    void setup() {
        store = mock(AudioStore.class); audio = mock(AudiobookService.class);
        offline = mock(AudioOfflineService.class);
        service = new AudioPlaybackService(store, audio, mock(UserRepository.class), offline);
        ReflectionTestUtils.setField(service,"timeZone","Asia/Shanghai");
        when(store.one(startsWith("SELECT * FROM listening_sessions"), eq("s"), eq(1L)))
                .thenReturn(Map.of("editionId",2L,"lastSequence",5L,"elapsedMs",10000L,"contentRevision",1L,
                        "lastEventAt",Instant.now().minusSeconds(2).toString()));
        when(store.edition(1L,2L)).thenReturn(Map.of("contentRevision",1L));
        when(audio.progress(1L,2L)).thenReturn(Map.of("activeSession","s","revision",3L,"positionMs",12000L));
        when(store.one(startsWith("SELECT duration_ms"), eq(7L), eq(2L))).thenReturn(Map.of("durationMs",60000L));
    }

    @Test
    void duplicateHeartbeatDoesNotCountTimeAgain() {
        var event = new AudioTypes.Heartbeat(5,3,1,7L,12000,10000,false);
        service.heartbeat(user,"s",event);
        verify(store,never()).update(anyString(), any(Object[].class));
    }

    @Test
    void obsoleteDeviceAndStaleContentCannotOverwriteProgress() {
        when(audio.progress(1L,2L)).thenReturn(Map.of("activeSession","new-device","revision",4L));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.heartbeat(user,"s",new AudioTypes.Heartbeat(6,3,1,7L,100,11000,false))).getStatusCode().value());
        when(audio.progress(1L,2L)).thenReturn(Map.of("activeSession","s","revision",3L));
        assertThrows(ResponseStatusException.class,
                () -> service.heartbeat(user,"s",new AudioTypes.Heartbeat(6,3,2,7L,100,11000,false)));
        verify(store,never()).update(anyString(), any(Object[].class));
    }

    @Test
    void intentionalRewindIsAcceptedAndListeningTimeIsCappedByWallClock() {
        service.heartbeat(user,"s",new AudioTypes.Heartbeat(6,3,1,7L,1000,9999999,false));
        verify(store).update(startsWith("UPDATE listening_progress SET track_id"), eq(7L), eq(1000L),
                eq("LISTENING"), any(java.sql.Timestamp.class), eq(1L), eq(2L));
        var start = org.mockito.ArgumentCaptor.forClass(Long.class);
        var end = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(offline).account(eq(1L), start.capture(), end.capture());
        assertTrue(end.getValue() - start.getValue() <= 3000);
    }
}
