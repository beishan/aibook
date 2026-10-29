package com.aibook.audiobook;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class AudioProbeServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AudioProbeService probe = new AudioProbeService(mapper);

    @Test
    void readsAacChaptersFromActualContainerMetadata() throws Exception {
        var result = probe.parse(mapper.readTree("""
            {"streams":[{"codec_type":"audio","codec_name":"aac"}],
             "format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"120.125","tags":{"title":"三体"}},
             "chapters":[{"start_time":"0.0","end_time":"60.5","tags":{"title":"序章"}},
                         {"start_time":"60.5","end_time":"120.125","tags":{"title":"第二章"}}]}
            """));
        assertEquals(120125, result.durationMs());
        assertEquals("audio/mp4", result.mimeType());
        assertEquals("序章", result.chapters().getFirst().title());
        assertEquals(60500, result.chapters().get(1).startMs());
    }

    @Test
    void refusesUnsupportedCodecEvenWhenFileIsNamedM4b() throws Exception {
        assertThrows(IOException.class, () -> probe.parse(mapper.readTree("""
            {"streams":[{"codec_type":"audio","codec_name":"alac"}],
             "format":{"format_name":"mov,mp4","duration":"120"}}
            """)));
        assertThrows(IOException.class, () -> probe.parse(mapper.readTree("{}")));
        assertThrows(IOException.class, () -> probe.parse(mapper.readTree("""
            {"streams":[{"codec_type":"audio","codec_name":"mp3"}],"format":{"format_name":"mp3","duration":"N/A"}}
            """)));
    }

    @Test
    void rejectsOverflowAndAcceptsSubMillisecondPrecision() throws Exception {
        assertThrows(IOException.class, () -> AudioProbeService.milliseconds("9999999999999999999999999"));
        assertEquals(1234,AudioProbeService.milliseconds("1.234567"));
        assertEquals(2,AudioProbeService.tagNumber("2/12"));
        assertNull(AudioProbeService.tagNumber("invalid"));
    }

    @Test
    void naturallyOrdersTrackNumbersWithoutIntegerOverflow() {
        assertTrue(AudioImportService.naturalCompare("Disc2/9.mp3", "Disc2/10.mp3") < 0);
        assertTrue(AudioImportService.naturalCompare("Disc2/10.mp3", "Disc10/1.mp3") < 0);
        assertTrue(AudioImportService.naturalCompare("99999999999999999.mp3", "100000000000000000.mp3") < 0);
    }
}
