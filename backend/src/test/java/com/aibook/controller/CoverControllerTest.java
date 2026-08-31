package com.aibook.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CoverControllerTest {

    @TempDir Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(tempDir.resolve("covers"));
        CoverController controller = new CoverController();
        ReflectionTestUtils.setField(controller, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(controller, "coverDir", "covers");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void localCoverUsesLongLivedImmutableCacheAndConditionalRequests() throws Exception {
        byte[] imageBytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        Files.write(tempDir.resolve("covers/test.jpg"), imageBytes);

        MvcResult initialResponse = mockMvc.perform(get("/api/covers/test.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(imageBytes))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
                .andReturn();

        String etag = initialResponse.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get("/api/covers/test.jpg").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(content().bytes(new byte[0]))
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void missingLocalCoverReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/covers/missing.jpg"))
                .andExpect(status().isNotFound());
    }
}
