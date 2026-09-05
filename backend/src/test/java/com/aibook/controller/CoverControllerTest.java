package com.aibook.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import com.aibook.service.CoverImageCacheService;
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
        CoverImageCacheService cache = new CoverImageCacheService();
        ReflectionTestUtils.setField(cache, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(cache, "coverDir", "covers");
        CoverController controller = new CoverController(cache);
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

    @Test
    void thumbnailResponseHasCorrectDimensionsTypeAndConditionalCache() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                800, 1200, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(1);
        for (int y = 0; y < 1200; y++) {
            for (int x = 0; x < 800; x++) image.setRGB(x, y, random.nextInt());
        }
        javax.imageio.ImageIO.write(image, "png", tempDir.resolve("covers/large.png").toFile());
        MvcResult result = mockMvc.perform(get("/api/covers/large.png").param("width", "96"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andReturn();
        var thumbnail = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(
                result.getResponse().getContentAsByteArray()));
        org.junit.jupiter.api.Assertions.assertEquals(96, thumbnail.getWidth());
        org.junit.jupiter.api.Assertions.assertEquals(144, thumbnail.getHeight());
        mockMvc.perform(get("/api/covers/large.png").param("width", "96")
                        .header(HttpHeaders.IF_NONE_MATCH, result.getResponse().getHeader(HttpHeaders.ETAG)))
                .andExpect(status().isNotModified());
        mockMvc.perform(get("/api/covers/large.png"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));
    }

    @Test
    void unsupportedSizeReturnsBadRequestAndFallbackIsNotLongCached() throws Exception {
        Files.writeString(tempDir.resolve("covers/test.webp"), "RIFFxxxxWEBPunsupported");
        mockMvc.perform(get("/api/covers/test.webp").param("width", "123"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/covers/proxy").param("url", "https://example.org/cover.jpg")
                        .param("width", "123"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/covers/test.webp").param("width", "320"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/webp"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
    }
}
