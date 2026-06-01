package com.aibook.controller;

import com.aibook.model.entity.User;
import com.aibook.service.KoReaderSyncService;
import com.aibook.service.UserService;
import com.aibook.service.WebDavService;
import com.aibook.util.MimeTypeUtil;
import com.aibook.util.WebDavXmlResponseBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * WebDAV 控制器
 * 提供 WebDAV 协议支持，用于 KOReader 等客户端同步
 */
@RestController
@RequestMapping("/webdav")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class WebDavController {

    private final WebDavService webDavService;
    private final UserService userService;
    private final KoReaderSyncService koReaderSyncService;
    private final ObjectMapper objectMapper;

    // ==================== PROPFIND ====================

    /**
     * PROPFIND - 列出目录属性
     */
    @RequestMapping(method = RequestMethod.POST, value = "/**",
            headers = "X-WebDAV-Method=PROPFIND")
    public ResponseEntity<String> propfind(
            Authentication authentication,
            HttpServletRequest request) {

        User user = getUserFromAuth(authentication);
        String path = extractPath(request);
        String depthHeader = request.getHeader("Depth");
        int depth = (depthHeader != null && "0".equals(depthHeader)) ? 0 : 1;

        log.debug("PROPFIND: path={}, depth={}", path, depth);

        List<WebDavXmlResponseBuilder.WebDavResource> resources = webDavService.listDirectory(user, path);
        String xml = WebDavXmlResponseBuilder.buildMultiStatus(path, resources);

        return ResponseEntity.ok()
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("DAV", "1, 2")
                .body(xml);
    }

    // ==================== MKCOL ====================

    /**
     * MKCOL - 创建集合（虚拟目录）
     */
    @RequestMapping(method = RequestMethod.POST, value = "/**",
            headers = "X-WebDAV-Method=MKCOL")
    public ResponseEntity<String> mkcol(
            Authentication authentication,
            HttpServletRequest request) {

        User user = getUserFromAuth(authentication);
        String path = extractPath(request);

        log.info("MKCOL: path={}", path);

        boolean created = webDavService.createCollection(user, path);
        if (created) {
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(WebDavXmlResponseBuilder.buildErrorResponse(405, "Method Not Allowed"));
    }

    // ==================== DELETE ====================

    /**
     * DELETE - 删除资源
     */
    @DeleteMapping("/**")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            HttpServletRequest request) {

        User user = getUserFromAuth(authentication);
        String path = extractPath(request);

        log.info("DELETE: path={}", path);

        boolean deleted = webDavService.deleteResource(user, path);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ==================== GET (下载/目录浏览) ====================

    /**
     * GET - 下载文件或列出目录
     */
    @GetMapping("/**")
    public ResponseEntity<?> get(
            Authentication authentication,
            HttpServletRequest request) throws IOException {

        User user = getUserFromAuth(authentication);
        String path = extractPath(request);

        // 如果是目录，返回 PROPFIND 格式的目录内容
        if (webDavService.isCollection(path)) {
            List<WebDavXmlResponseBuilder.WebDavResource> resources = webDavService.listDirectory(user, path);
            String xml = WebDavXmlResponseBuilder.buildMultiStatus(path, resources);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/xml; charset=utf-8")
                    .header("DAV", "1, 2")
                    .body(xml);
        }

        // 下载文件
        Path filePath = webDavService.getBookFilePath(user, path);
        if (filePath == null || !Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(filePath.toFile());
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header("Content-Disposition", "attachment")
                .header("DAV", "1, 2")
                .body(resource);
    }

    // ==================== PUT (上传/KOReader 同步) ====================

    /**
     * PUT - 上传文件或同步 KOReader 进度
     */
    @PutMapping("/**")
    public ResponseEntity<Void> put(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {

        User user = getUserFromAuth(authentication);
        String path = extractPath(request);

        log.info("WebDAV PUT: path={}, user={}, bodySize={}",
                path, user.getUsername(), body != null ? body.length : 0);

        // 处理 KOReader .sdr 文件同步（阅读进度）
        if (body != null && body.length > 0 && path.endsWith(".sdr")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> progressData = objectMapper.readValue(body, Map.class);
                String documentId = extractDocumentIdFromPath(path);
                if (documentId != null) {
                    koReaderSyncService.saveProgress(user, documentId, progressData);
                    log.info("KOReader progress synced via WebDAV: document={}", documentId);
                }
            } catch (Exception e) {
                log.warn("Failed to parse KOReader .sdr data: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok().build();
    }

    // ==================== HEAD ====================

    /**
     * HEAD - 检查资源是否存在
     */
    @RequestMapping(method = RequestMethod.HEAD, value = "/**")
    public ResponseEntity<Void> head(
            Authentication authentication,
            HttpServletRequest request) {

        User user = getUserFromAuth(authentication);
        String path = extractPath(request);

        if (webDavService.resourceExists(user, path)) {
            return ResponseEntity.ok()
                    .header("DAV", "1, 2")
                    .build();
        }

        return ResponseEntity.notFound().build();
    }

    // ==================== OPTIONS ====================

    /**
     * OPTIONS - 获取支持的方法
     */
    @RequestMapping(method = RequestMethod.OPTIONS, value = "/**")
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok()
                .header("Allow", "GET, HEAD, PUT, DELETE, PROPFIND, MKCOL, OPTIONS")
                .header("DAV", "1, 2")
                .header("MS-Author-Via", "DAV")
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 KOReader .sdr 路径提取 document ID
     */
    private String extractDocumentIdFromPath(String path) {
        // 路径格式: /webdav/some/path/filename.sdr
        // 提取 filename（去掉 .sdr 后缀）作为 document ID
        String filename = java.nio.file.Paths.get(path).getFileName().toString();
        if (filename.endsWith(".sdr")) {
            return filename.substring(0, filename.length() - 4);
        }
        return filename;
    }

    /**
     * 提取请求路径
     */
    private String extractPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/webdav")) {
            path = path.substring(7);
        }
        if (path.isEmpty()) {
            path = "/";
        }
        return path;
    }

    /**
     * 获取用户
     */
    private User getUserFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("未认证");
        }
        return userService.findByUsername(authentication.getName());
    }
}
