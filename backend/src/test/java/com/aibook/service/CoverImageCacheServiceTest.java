package com.aibook.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class CoverImageCacheServiceTest {
    @TempDir Path tempDir;
    private CoverImageCacheService cache;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        cache = configured(new CoverImageCacheService());
    }

    private <T extends CoverImageCacheService> T configured(T service) {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "coverDir", "covers");
        return service;
    }

    @AfterEach
    void tearDown() {
        cache.close();
        if (server != null) server.stop(0);
    }

    private Path image() throws IOException {
        Path path = tempDir.resolve("original.png");
        BufferedImage image = new BufferedImage(1000, 1500, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(5);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, random.nextInt());
        }
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    @Test
    void shrinksAndReusesDiskCacheAcrossServiceRestartAndInvalidatesChangedSource() throws Exception {
        Path original = image();
        byte[] before = Files.readAllBytes(original);
        Path thumbnail = cache.thumbnail(original, 320);
        BufferedImage decoded = ImageIO.read(thumbnail.toFile());
        assertEquals(320, decoded.getWidth());
        assertEquals(480, decoded.getHeight());
        assertTrue(Files.size(thumbnail) < Files.size(original) / 10);
        assertArrayEquals(before, Files.readAllBytes(original));
        FileTime generated = Files.getLastModifiedTime(thumbnail);
        CoverImageCacheService restarted = configured(new CoverImageCacheService());
        try {
            assertEquals(thumbnail, restarted.thumbnail(original, 320));
            assertEquals(generated, Files.getLastModifiedTime(thumbnail));
        } finally {
            restarted.close();
        }
        Files.setLastModifiedTime(original, FileTime.fromMillis(System.currentTimeMillis() + 2000));
        assertNotEquals(thumbnail, cache.thumbnail(original, 320));
        assertEquals(96, ImageIO.read(cache.thumbnail(original, 96).toFile()).getWidth());
    }

    @Test
    void concurrentRequestsPublishOneCompleteThumbnail() throws Exception {
        Path original = image();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<java.util.concurrent.Future<Path>>();
            for (int i = 0; i < 8; i++) tasks.add(executor.submit(() -> cache.thumbnail(original, 320)));
            Path expected = tasks.getFirst().get(10, TimeUnit.SECONDS);
            for (var task : tasks) assertEquals(expected, task.get(10, TimeUnit.SECONDS));
            assertNotNull(ImageIO.read(expected.toFile()));
        }
    }

    @Test
    void unsupportedImageFallsBackAndArbitrarySizesAreRejected() throws Exception {
        Path unsupported = tempDir.resolve("unsupported.webp");
        Files.writeString(unsupported, "RIFFxxxxWEBPunsupported");
        assertEquals(unsupported, cache.thumbnail(unsupported, 320));
        assertThrows(IllegalArgumentException.class, () -> cache.thumbnail(unsupported, 999));
    }

    @Test
    void cleanupOnlyDeletesDerivedCacheAndHonorsSizeBudget() throws Exception {
        Path original = image();
        Path thumbnail = cache.thumbnail(original, 320);
        ReflectionTestUtils.setField(cache, "maxCacheBytes", 0L);
        cache.cleanCache();
        assertTrue(Files.exists(original));
        assertFalse(Files.exists(thumbnail));
    }

    @Test
    void remoteDownloadIsPersistentAndStaleRefreshDoesNotBlockOrLoseOldImage() throws Exception {
        byte[] bytes = Files.readAllBytes(image());
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cover", exchange -> {
            int attempt = requests.incrementAndGet();
            try {
                if (attempt > 1) {
                    refreshStarted.countDown();
                    try { releaseRefresh.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                    exchange.sendResponseHeaders(503, -1);
                } else {
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } finally { exchange.close(); }
        });
        server.start();
        cache.close();
        // 仅测试夹具允许 loopback，生产服务仍必须拒绝。
        cache = configured(new CoverImageCacheService() {
            @Override void validateRemoteAddress(URI uri) { }
        });
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/cover";
        Path result = cache.remote(url);
        assertArrayEquals(bytes, Files.readAllBytes(result));
        assertEquals(result, cache.remote(url));
        assertEquals(1, requests.get());
        Files.setLastModifiedTime(result, FileTime.fromMillis(
                System.currentTimeMillis() - Duration.ofDays(8).toMillis()));
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertEquals(result, cache.remote(url)));
            assertTrue(refreshStarted.await(3, TimeUnit.SECONDS));
            assertArrayEquals(bytes, Files.readAllBytes(cache.remote(url)));
        } finally { releaseRefresh.countDown(); }
        cache.close();
        assertArrayEquals(bytes, Files.readAllBytes(result));
    }

    @Test
    void rejectsPrivateAndNonHttpRemoteAddresses() {
        assertThrows(IOException.class, () -> cache.remote("file:///etc/passwd"));
        assertThrows(IOException.class, () -> cache.remote("http://127.0.0.1/secret"));
        assertThrows(IOException.class, () -> cache.remote("http://[::1]/secret"));
        assertThrows(IOException.class, () -> cache.remote("http://192.168.1.1/secret"));
    }

    @Test
    void remoteHtmlIsNotSavedAsCover() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/html", exchange -> {
            try {
                byte[] html = "<html>not an image</html>".getBytes();
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);
            } finally { exchange.close(); }
        });
        server.start();
        cache.close();
        cache = configured(new CoverImageCacheService() {
            @Override void validateRemoteAddress(URI uri) { }
        });
        assertThrows(IOException.class, () -> cache.remote(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/html"));
    }
}
