package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerSiteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Account-owned source icons. A single atomic file stores the image and its origin. */
@Service
@RequiredArgsConstructor
public class CrawlerSiteIconService {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private final CrawlerSiteRepository sites;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    @Value("${upload.path:${app.upload.dir:./uploads}}")
    private String uploadPath;

    public record IconView(String dataUrl, String source, String sourceUrl,
                           long updatedAt, String error) {}

    public IconView get(User user, Long id) {
        CrawlerSite site = owned(user, id);
        synchronized (lock(id)) {
            IconView cached = read(id);
            if (cached != null) return cached;
            return collect(site, false);
        }
    }

    public IconView refresh(User user, Long id) {
        CrawlerSite site = owned(user, id);
        synchronized (lock(id)) {
            return collect(site, true);
        }
    }

    public IconView upload(User user, Long id, MultipartFile file) {
        owned(user, id);
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择不超过 2MB 的图标");
        }
        try {
            byte[] bytes = file.getBytes();
            String type = imageType(bytes);
            if (type == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "仅支持 PNG、JPG、GIF、WebP 或 ICO 图标");
            }
            synchronized (lock(id)) {
                IconView icon = icon(bytes, type, "CUSTOM", null);
                write(id, icon);
                return icon;
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "网站图标保存失败", exception);
        }
    }

    public void delete(Long id) {
        synchronized (lock(id)) {
            try {
                Files.deleteIfExists(path(id));
            } catch (IOException ignored) {
                // A leftover file cannot be accessed after its site is deleted.
            }
        }
        locks.remove(id);
    }

    private Object lock(Long id) {
        return locks.computeIfAbsent(id, key -> new Object());
    }

    private CrawlerSite owned(User user, Long id) {
        return sites.findByIdAndUser(id, user).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "网站不存在"));
    }

    private IconView collect(CrawlerSite site, boolean manual) {
        String home = site.getHomeUrl();
        if (home == null || home.isBlank()) home = site.getBaseUrl();
        try {
            long deadline = System.currentTimeMillis() + 20000;
            Set<String> candidates = new LinkedHashSet<>();
            URI root = URI.create(home);
            try {
                Connection.Response page = fetch(home, 1024 * 1024, deadline);
                Document doc = Jsoup.parse(page.body(), page.url().toString());
                candidates.addAll(discover(doc));
                root = page.url().toURI();
            } catch (Exception ignored) {
                // Some sites deny HTML access but still expose the conventional icon.
            }
            candidates.add(root.resolve("/favicon.ico").toString());
            for (String url : candidates.stream().limit(8).toList()) {
                try {
                    if (System.currentTimeMillis() >= deadline) break;
                    byte[] bytes = fetch(url, MAX_BYTES, deadline).bodyAsBytes();
                    String type = imageType(bytes);
                    if (type == null) continue;
                    IconView result = icon(bytes, type, "AUTO", url);
                    write(site.getId(), result);
                    return result;
                } catch (Exception ignored) {
                    // Try the next declared icon before reporting a collection failure.
                }
            }
        } catch (Exception ignored) {
            // Keep the existing icon when the website is temporarily unavailable.
        }
        String error = "未能采集网站图标，可重新采集或上传自定义图标";
        if (manual) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, error);
        IconView empty = new IconView(null, null, null, Instant.now().toEpochMilli(), error);
        write(site.getId(), empty); // Failed automatic attempts are not repeated on each render.
        return empty;
    }

    static Set<String> discover(Document doc) {
        Set<String> icons = new LinkedHashSet<>();
        for (var link : doc.select("link[href]")) {
            String rel = link.attr("rel").toLowerCase(Locale.ROOT);
            if (java.util.Arrays.asList(rel.split("\\s+")).contains("icon")) {
                String url = link.absUrl("href");
                if (!url.isBlank()) icons.add(url);
            }
        }
        for (var link : doc.select("link[rel~=(?i)apple-touch-icon][href]")) {
            String url = link.absUrl("href");
            if (!url.isBlank()) icons.add(url);
        }
        return icons;
    }

    protected Connection.Response fetch(String url, int limit, long deadline) throws Exception {
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (System.currentTimeMillis() >= deadline) throw new IOException("Icon request timed out");
            validateTarget(url);
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; AIBook favicon collector)")
                    .timeout((int) Math.max(1, Math.min(4000, deadline - System.currentTimeMillis())))
                    .maxBodySize(limit + 1)
                    .ignoreContentType(true).ignoreHttpErrors(true)
                    .followRedirects(false).execute();
            int status = response.statusCode();
            if (status >= 300 && status < 400 && response.hasHeader("Location")) {
                url = URI.create(url).resolve(response.header("Location")).toString();
                continue;
            }
            if (status < 200 || status >= 300 || response.bodyAsBytes().length > limit) {
                throw new IOException("Invalid or oversized favicon response");
            }
            return response;
        }
        throw new IOException("Too many redirects");
    }

    static void validateTarget(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid URL", exception);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IOException("Unsupported icon URL");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            byte[] raw = address.getAddress();
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || (raw.length == 16 && (raw[0] & 0xfe) == 0xfc)
                    || (raw.length == 4 && ((raw[0] & 255) == 0
                        || ((raw[0] & 255) == 100 && (raw[1] & 255) >= 64
                            && (raw[1] & 255) <= 127)))) {
                throw new IOException("Private network icon URLs are not allowed");
            }
        }
    }

    static String imageType(byte[] b) {
        if (b.length > MAX_BYTES) {
            return null;
        }
        if (b.length >= 8 && b[0] == (byte) 137 && b[1] == 80 && b[2] == 78
                && b[3] == 71 && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10) {
            return "image/png";
        }
        if (b.length >= 4 && b[0] == (byte) 255 && b[1] == (byte) 216
                && b[2] == (byte) 255) {
            return "image/jpeg";
        }
        if (b.length >= 6 && new String(b, 0, 6, java.nio.charset.StandardCharsets.US_ASCII)
                .matches("GIF8[79]a")) {
            return "image/gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 6 && b[0] == 0 && b[1] == 0 && b[2] == 1 && b[3] == 0
                && ((b[4] & 255) + (b[5] & 255) * 256) > 0) {
            return "image/x-icon";
        }
        return null;
    }

    private IconView icon(byte[] bytes, String type, String source, String url) {
        return new IconView("data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes),
                source, url, Instant.now().toEpochMilli(), null);
    }

    private Path path(Long id) {
        return Path.of(uploadPath).toAbsolutePath().resolve("crawler-site-icons").resolve(id + ".json");
    }

    private IconView read(Long id) {
        try {
            return mapper.readValue(path(id).toFile(), IconView.class);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void write(Long id, IconView view) {
        Path temporary = null;
        try {
            Path target = path(id);
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), id + "-", ".tmp");
            mapper.writeValue(temporary.toFile(), view);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "网站图标保存失败", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup of an incomplete write.
                }
            }
        }
    }
}
