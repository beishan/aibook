package com.aibook.audiobook;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AudioRangeTest {
    @Test
    void supportsOpenEndedSuffixAndClippedRanges() {
        assertEquals(new AudioRange(0, 99, false), AudioRange.parse(null, 100));
        assertEquals(new AudioRange(10, 99, true), AudioRange.parse("bytes=10-", 100));
        assertEquals(new AudioRange(90, 99, true), AudioRange.parse("bytes=-10", 100));
        assertEquals(new AudioRange(0, 99, true), AudioRange.parse("bytes=-500", 100));
        assertEquals(new AudioRange(90, 99, true), AudioRange.parse("bytes=90-500", 100));
    }

    @Test
    void rejectsMalformedMultipleOverflowAndUnsatisfiableRanges() {
        for (String header : new String[]{"bytes=-", "bytes=-0", "bytes=100-", "bytes=30-10",
                "bytes=0-2,4-6", "items=0-2", "bytes=999999999999999999999-"}) {
            assertThrows(IllegalArgumentException.class, () -> AudioRange.parse(header, 100), header);
        }
    }
}
