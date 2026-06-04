package com.aibook.controller;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;

/**
 * 封面图片控制器
 */
@RestController
@RequestMapping("/api/covers")
@CrossOrigin(origins = "*")
@Slf4j
public class CoverController {

    @Value("${app.upload.dir:/app/uploads}")
    private String uploadDir;

    @Value("${app.cover.dir:covers}")
    private String coverDir;

    /**
     * 获取本地封面图片
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getCover(@PathVariable String filename) {
        Path coverPath = Paths.get(uploadDir, coverDir, filename);

        if (!Files.exists(coverPath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(coverPath.toFile());

        // 确定 Content-Type
        String contentType = "image/jpeg";
        if (filename.endsWith(".png")) {
            contentType = "image/png";
        } else if (filename.endsWith(".gif")) {
            contentType = "image/gif";
        } else if (filename.endsWith(".webp")) {
            contentType = "image/webp";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(resource);
    }

    /**
     * 代理获取远程封面图片（解决防盗链问题）
     */
    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxyCover(@RequestParam String url) {
        try {
            log.info("代理获取封面: {}", url);

            // 使用 Java 原生 HttpURLConnection，更灵活控制请求头
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Referer", "https://book.douban.com/");
            connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                byte[] imageBytes = inputStream.readAllBytes();
                inputStream.close();

                // 确定 Content-Type
                String contentType = connection.getContentType();
                if (contentType == null || contentType.isEmpty()) {
                    contentType = "image/jpeg";
                }

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                        .body(imageBytes);
            } else if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                // 处理重定向
                String newUrl = connection.getHeaderField("Location");
                connection.disconnect();
                if (newUrl != null) {
                    return proxyCover(newUrl);
                }
            }

            connection.disconnect();
            return ResponseEntity.status(responseCode).build();
        } catch (Exception e) {
            log.error("代理获取封面失败: {}", url, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
