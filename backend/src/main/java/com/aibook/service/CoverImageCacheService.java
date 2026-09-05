package com.aibook.service;

import jakarta.annotation.PreDestroy;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 可重新生成的封面磁盘缓存，不修改书籍原图或数据库。 */
@Service
@Slf4j
public class CoverImageCacheService {
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final long REMOTE_TTL = Duration.ofDays(7).toMillis();
    private final Object[] locks = new Object[32];
    private final Semaphore imageWorkers = new Semaphore(2);
    private final Semaphore downloadWorkers = new Semaphore(4);
    private final Cache<String, Boolean> refreshAttempts = Caffeine.newBuilder()
            .maximumSize(4096).expireAfterWrite(Duration.ofMinutes(5)).build();
    private final Cache<String, Boolean> originalPreferred = Caffeine.newBuilder()
            .maximumSize(4096).expireAfterWrite(Duration.ofHours(1)).build();
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor refreshExecutor = new ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "cover-cache-refresh");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    @Value("${app.upload.dir:/app/uploads}") private String uploadDir;
    @Value("${app.cover.dir:covers}") private String coverDir;
    @Value("${app.cover.cache.max-bytes:536870912}") private long maxCacheBytes = 536870912L;

    public CoverImageCacheService() {
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
    }

    private Path cacheRoot() {
        return Path.of(uploadDir, coverDir, ".cache", "v1").toAbsolutePath().normalize();
    }

    private Object lock(String key) {
        return locks[Math.floorMod(key.hashCode(), locks.length)];
    }

    /** 固定尺寸避免任意尺寸请求制造无限缓存；原文件变化自动使用新的缓存键。 */
    public Path thumbnail(Path source, int width) throws IOException {
        if (width != 96 && width != 320) throw new IllegalArgumentException("无效封面尺寸");
        String key = hash(source.toAbsolutePath().normalize() + ":"
                + Files.getLastModifiedTime(source) + ":" + Files.size(source) + ":" + width);
        Path target = cacheRoot().resolve("thumbnails").resolve(key + ".jpg");
        if (Files.isRegularFile(target)) return target;
        if (originalPreferred.getIfPresent(key) != null) return source;
        synchronized (lock(key)) {
            if (Files.isRegularFile(target)) return target;
            // NAS 上限制并行解码数量，繁忙时使用原图，后续请求可再次生成。
            try {
                if (!imageWorkers.tryAcquire(2, TimeUnit.SECONDS)) return source;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return source;
            }
            try {
                Path result = createThumbnail(source, target, width);
                if (result.equals(source)) originalPreferred.put(key, true);
                return result;
            } catch (IOException | RuntimeException exception) {
                log.debug("封面缩略图生成失败，回退原图: {}", source, exception);
                return source;
            } finally {
                imageWorkers.release();
            }
        }
    }

    private Path createThumbnail(Path source, Path target, int width) throws IOException {
        try (var input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) return source;
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return source; // 例如默认 JDK 无法解码的 WebP。
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int originalWidth = reader.getWidth(0);
                int originalHeight = reader.getHeight(0);
                if (originalWidth <= 0 || originalHeight <= 0
                        || (long) originalWidth * originalHeight > 100_000_000L) return source;
                double scale = Math.min(1, Math.min((double) width / originalWidth,
                        (double) (width * 2) / originalHeight));
                int targetWidth = Math.max(1, (int) Math.round(originalWidth * scale));
                int targetHeight = Math.max(1, (int) Math.round(originalHeight * scale));
                var param = reader.getDefaultReadParam();
                int subsampling = Math.max(1, (int) (1 / scale) / 2);
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                BufferedImage decoded = reader.read(0, param);
                BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                var graphics = resized.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, targetWidth, targetHeight);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(decoded, 0, 0, targetWidth, targetHeight, null);
                } finally {
                    graphics.dispose();
                    decoded.flush();
                }
                Files.createDirectories(target.getParent());
                Path temporary = Files.createTempFile(target.getParent(), "thumb-", ".tmp");
                try {
                    if (!ImageIO.write(resized, "jpg", temporary.toFile())) return source;
                    // 对原本很小的图片不做体积反而更大的转换。
                    if (Files.size(temporary) >= Files.size(source)) return source;
                    publish(temporary, target);
                    return target;
                } finally {
                    resized.flush();
                    Files.deleteIfExists(temporary);
                }
            } finally {
                reader.dispose();
            }
        }
    }

    /** 冷缓存首次下载；已有缓存立即返回，过期内容由有界线程池刷新。 */
    public Path remote(String url) throws IOException {
        URI uri = parseRemoteUri(url);
        String key = hash(uri.toString());
        Path target = cacheRoot().resolve("remote").resolve(key + ".img");
        if (Files.isRegularFile(target)) {
            if (System.currentTimeMillis() - Files.getLastModifiedTime(target).toMillis() > REMOTE_TTL) {
                refresh(key, uri, target);
            }
            return target;
        }
        synchronized (lock(key)) {
            if (!Files.isRegularFile(target)) download(uri, target);
        }
        return target;
    }

    private void refresh(String key, URI uri, Path target) {
        if (refreshAttempts.getIfPresent(key) != null) return;
        if (!refreshing.add(key)) return;
        try {
            refreshExecutor.execute(() -> {
                refreshAttempts.put(key, true);
                try {
                    synchronized (lock(key)) {
                        download(uri, target);
                    }
                } catch (IOException exception) {
                    log.debug("远程封面刷新失败，保留本地缓存", exception);
                } finally {
                    refreshing.remove(key);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            refreshing.remove(key);
        }
    }

    private URI parseRemoteUri(String url) throws IOException {
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IOException("无效的远程封面地址");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IOException("无效的远程封面地址", exception);
        }
    }

    // 每一跳都检查地址，不允许公开封面代理读取内网服务。
    void validateRemoteAddress(URI uri) throws IOException {
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc)) {
                throw new IOException("远程封面地址不可访问");
            }
        }
    }

    void download(URI initialUri, Path target) throws IOException {
        try {
            if (!downloadWorkers.tryAcquire(2, TimeUnit.SECONDS)) throw new IOException("封面下载繁忙");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("封面下载已取消", exception);
        }
        try {
            downloadImage(initialUri, target);
        } finally {
            downloadWorkers.release();
        }
    }

    private void downloadImage(URI initialUri, Path target) throws IOException {
        URI uri = initialUri;
        for (int redirect = 0; redirect <= 3; redirect++) {
            validateRemoteAddress(uri);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setRequestProperty("Referer", "https://book.douban.com/");
                connection.setRequestProperty("Accept", "image/jpeg,image/png,image/gif,image/webp");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("封面重定向缺少地址");
                    uri = parseRemoteUri(uri.resolve(location).toString());
                    continue;
                }
                if (status != 200) throw new IOException("封面服务器返回 " + status);
                if (connection.getContentLengthLong() > MAX_IMAGE_BYTES) throw new IOException("远程封面超过10MB");
                byte[] bytes;
                try (var input = connection.getInputStream()) {
                    bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
                }
                if (bytes.length > MAX_IMAGE_BYTES || contentType(bytes) == null) {
                    throw new IOException("远程封面格式无效或超过10MB");
                }
                Files.createDirectories(target.getParent());
                Path temporary = Files.createTempFile(target.getParent(), "remote-", ".tmp");
                try {
                    Files.write(temporary, bytes);
                    publish(temporary, target);
                } finally {
                    Files.deleteIfExists(temporary);
                }
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("封面重定向次数过多");
    }

    public String contentType(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            String detected = contentType(input.readNBytes(12));
            return detected == null ? "application/octet-stream" : detected;
        }
    }

    private String contentType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) return "image/jpeg";
        if (bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "image/gif";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return null;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void publish(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 每小时回收衍生缓存，绝不遍历或删除用户原封面。允许两次清理间短暂超限。 */
    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void cleanCache() throws IOException {
        if (!Files.isDirectory(cacheRoot())) return;
        record Entry(Path path, long size, long modified) {}
        var entries = new java.util.ArrayList<Entry>();
        try (var paths = Files.walk(cacheRoot(), 2)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (path.toString().endsWith(".tmp")) continue;
                try {
                    entries.add(new Entry(path, Files.size(path), Files.getLastModifiedTime(path).toMillis()));
                } catch (java.nio.file.NoSuchFileException ignored) { }
            }
        }
        entries.sort(Comparator.comparingLong(Entry::modified));
        long total = entries.stream().mapToLong(Entry::size).sum();
        long cutoff = System.currentTimeMillis() - Duration.ofDays(30).toMillis();
        for (Entry entry : entries) {
            if (entry.modified() < cutoff || total > Math.max(0, maxCacheBytes)) {
                if (Files.deleteIfExists(entry.path())) total -= entry.size();
            }
        }
    }

    @PreDestroy
    public void close() {
        refreshExecutor.shutdownNow();
    }
}
