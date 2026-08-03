package com.aibook.service;

import com.aibook.dto.SiteFaviconStatusDTO;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.SystemConfig;
import com.aibook.model.entity.User;
import com.aibook.repository.SystemConfigRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SiteFaviconService {

    private static final String CONFIG_KEY = "site.favicon.path";
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif",
            "image/x-icon", ".ico");

    private final SystemConfigRepository configRepository;
    private final OperationLogService operationLogService;

    @Value("${upload.path:${app.upload.dir:./uploads}}")
    private String uploadPath;

    @Transactional(readOnly = true)
    public SiteFaviconStatusDTO getStatus() {
        Path favicon = currentFaviconPath();
        if (favicon == null) {
            return new SiteFaviconStatusDTO(false, null, 0);
        }
        try {
            return new SiteFaviconStatusDTO(
                    true,
                    "/api/site/favicon",
                    Files.getLastModifiedTime(favicon).toMillis());
        } catch (Exception exception) {
            return new SiteFaviconStatusDTO(false, null, 0);
        }
    }

    @Transactional
    public SiteFaviconStatusDTO upload(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择网站图标");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "网站图标不能超过2MB");
        }

        Path newFile = null;
        try {
            byte[] bytes = file.getBytes();
            String contentType = detectContentType(bytes);
            String extension = contentType == null ? null : SUPPORTED_TYPES.get(contentType);
            if (extension == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG、WebP、GIF 或 ICO 图片");
            }

            Path directory = faviconDirectory();
            Files.createDirectories(directory);
            newFile = directory.resolve("favicon-" + UUID.randomUUID() + extension);
            Files.write(newFile, bytes);

            String oldPath = configuredPath();
            savePath(relativePath(newFile));
            operationLogService.record(
                    user,
                    OperationLog.Action.UPDATE_SITE_FAVICON,
                    null,
                    "更新网站标签页图标",
                    null);
            deleteManagedFile(oldPath);
            return getStatus();
        } catch (ResponseStatusException exception) {
            deleteQuietly(newFile);
            throw exception;
        } catch (Exception exception) {
            deleteQuietly(newFile);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "网站图标保存失败", exception);
        }
    }

    @Transactional
    public SiteFaviconStatusDTO restoreDefault(User user) {
        String oldPath = configuredPath();
        savePath(null);
        deleteManagedFile(oldPath);
        operationLogService.record(
                user,
                OperationLog.Action.UPDATE_SITE_FAVICON,
                null,
                "恢复默认网站标签页图标",
                null);
        return getStatus();
    }

    @Transactional(readOnly = true)
    public FaviconContent getContent() {
        Path favicon = currentFaviconPath();
        if (favicon == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未设置自定义网站图标");
        }
        return new FaviconContent(favicon, contentType(favicon.getFileName().toString()));
    }

    private String configuredPath() {
        return configRepository.findById(CONFIG_KEY)
                .map(SystemConfig::getConfigValue)
                .filter(value -> !value.isBlank())
                .orElse(null);
    }

    private Path currentFaviconPath() {
        String configuredPath = configuredPath();
        if (configuredPath == null) return null;
        Path directory = faviconDirectory().toAbsolutePath().normalize();
        Path favicon = Paths.get(uploadPath).resolve(configuredPath).toAbsolutePath().normalize();
        return favicon.startsWith(directory) && Files.isRegularFile(favicon) ? favicon : null;
    }

    private void savePath(String path) {
        SystemConfig config = configRepository.findById(CONFIG_KEY)
                .orElse(SystemConfig.builder().configKey(CONFIG_KEY).build());
        config.setConfigValue(path);
        config.setDescription("网站浏览器标签页图标文件路径");
        configRepository.save(config);
    }

    private Path faviconDirectory() {
        return Paths.get(uploadPath, "site");
    }

    private String relativePath(Path file) {
        return Paths.get("site", file.getFileName().toString()).toString();
    }

    private void deleteManagedFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        Path directory = faviconDirectory().toAbsolutePath().normalize();
        Path file = Paths.get(uploadPath).resolve(relativePath).toAbsolutePath().normalize();
        if (file.startsWith(directory)) deleteQuietly(file);
    }

    private void deleteQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 配置已经更新，旧图标清理失败不影响新图标使用。
        }
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 4
                && bytes[0] == 0
                && bytes[1] == 0
                && bytes[2] == 1
                && bytes[3] == 0) {
            return "image/x-icon";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "image/jpeg";
    }

    public record FaviconContent(Path path, String contentType) {}
}
