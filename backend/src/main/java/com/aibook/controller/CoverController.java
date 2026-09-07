package com.aibook.controller;

import com.aibook.service.CoverImageCacheService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** 原图与固定尺寸缩略图共用地址，未传 width 的客户端保持原图行为。 */
@RestController
@RequestMapping("/api/covers")
@CrossOrigin(origins = "*")
public class CoverController {
    private final CoverImageCacheService imageCache;

    @Value("${app.upload.dir:/app/uploads}") private String uploadDir;
    @Value("${app.cover.dir:covers}") private String coverDir;

    public CoverController(CoverImageCacheService imageCache) {
        this.imageCache = imageCache;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getCover(@PathVariable String filename,
            @RequestParam(required = false) Integer width, WebRequest request) throws IOException {
        if (!validWidth(width)) return ResponseEntity.badRequest().build();
        Path root = Path.of(uploadDir, coverDir).toAbsolutePath().normalize();
        Path source = root.resolve(filename).normalize();
        if (!source.getParent().equals(root) || !Files.isRegularFile(source)) {
            return ResponseEntity.notFound().build();
        }
        Path result = width == null ? source : imageCache.thumbnail(source, width);
        CacheControl cache = width == null
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.maxAge(Duration.ofDays(1)).cachePublic();
        // 生成繁忙或失败时的原图回退不能长期占用缩略图 URL 的浏览器缓存。
        if (width != null && result.equals(source)) cache = CacheControl.noCache();
        return respond(result, cache, request);
    }

    @GetMapping("/proxy")
    public ResponseEntity<Resource> proxyCover(@RequestParam String url,
            @RequestParam(required = false) Integer width, WebRequest request) {
        if (!validWidth(width)) return ResponseEntity.badRequest().build();
        try {
            Path source = imageCache.remote(url);
            Path result = width == null ? source : imageCache.thumbnail(source, width);
            CacheControl cache = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();
            if (width != null && result.equals(source)) cache = CacheControl.noCache();
            return respond(result, cache, request);
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).cacheControl(CacheControl.noStore()).build();
        }
    }

    private boolean validWidth(Integer width) {
        return width == null || width == 96 || width == 320;
    }

    private ResponseEntity<Resource> respond(Path path, CacheControl cache, WebRequest request)
            throws IOException {
        long lastModified = Files.getLastModifiedTime(path).toMillis();
        long size = Files.size(path);
        String etag = "\"" + Long.toHexString(size) + "-" + Long.toHexString(lastModified) + "\"";
        if (request.checkNotModified(etag, lastModified)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag)
                    .lastModified(lastModified).cacheControl(cache).build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(imageCache.contentType(path)))
                .contentLength(size).eTag(etag).lastModified(lastModified).cacheControl(cache)
                .body(new FileSystemResource(path));
    }
}
