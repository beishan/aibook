package com.aibook.audiobook;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.aibook.controller.GlobalExceptionHandler;
import com.aibook.service.UserService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AudioMediaControllerTest {
    @TempDir Path root;
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        var playback = mock(AudioPlaybackService.class);
        var audio = mock(AudiobookService.class);
        Path file = root.resolve("track.mp3");
        Files.writeString(file, "0123456789");
        var track = Map.<String,Object>of("fileSize",10L,"fileHash","sample", "modifiedMs",1000L,"mimeType","audio/mpeg");
        when(playback.media("session",7L,"scoped-key")).thenReturn(track);
        when(audio.checkedPath(track)).thenReturn(file);
        var controller = new AudioController(mock(UserService.class), audio, mock(AudioImportService.class), playback, mock(AudioOfflineService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(
                        mock(com.aibook.service.SystemErrorLogService.class)))
                .build();
    }

    @Test
    void servesRealPartialBodyAndSuffixWithoutBufferingWholeMedia() throws Exception {
        mvc.perform(get("/api/audio-media/session/7").param("key","scoped-key").header("Range","bytes=3-5"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 3-5/10"))
                .andExpect(header().string("Content-Length","3")).andExpect(content().string("345"));
        mvc.perform(get("/api/audio-media/session/7").param("key","scoped-key").header("Range","bytes=-2"))
                .andExpect(status().isPartialContent()).andExpect(content().string("89"));
    }

    @Test
    void headAndIfRangeAndUnsatisfiableRequestsFollowHttpSemantics() throws Exception {
        mvc.perform(head("/api/audio-media/session/7").param("key","scoped-key"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Length","10")).andExpect(content().string(""));
        mvc.perform(get("/api/audio-media/session/7").param("key","scoped-key").header("Range","bytes=30-"))
                .andExpect(status().isRequestedRangeNotSatisfiable()).andExpect(header().string("Content-Range","bytes */10"));
        mvc.perform(get("/api/audio-media/session/7").param("key","scoped-key").header("Range","bytes=3-5").header("If-Range","\"old\""))
                .andExpect(status().isOk()).andExpect(content().string("0123456789"));
    }
}
