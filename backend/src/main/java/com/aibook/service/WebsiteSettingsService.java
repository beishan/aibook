package com.aibook.service;

import com.aibook.dto.WebsiteSettingsDTO;
import com.aibook.dto.WebsiteSettingsUpdateRequest;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.SystemConfig;
import com.aibook.model.entity.User;
import com.aibook.repository.SystemConfigRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
public class WebsiteSettingsService {

    private static final String SITE_NAME_KEY = "site.name";
    private static final String BROWSER_TITLE_KEY = "site.browser.title";
    private static final String LOGIN_DESCRIPTION_KEY = "site.login.description";
    private static final String REGISTRATION_ENABLED_KEY = "site.registration.enabled";
    private static final String LOGIN_ICON_PATH_KEY = "site.login.icon.path";
    private static final String LOGIN_STYLE_PREFIX = "site.login.style.";
    private static final String DEFAULT_SITE_NAME = "汗牛充栋";
    private static final String DEFAULT_BROWSER_TITLE = "汗牛充栋 - 私人书库";
    private static final String DEFAULT_LOGIN_DESCRIPTION = "您的私人书库管理系统";
    private static final String DEFAULT_LOGIN_STYLE = "glass";
    private static final Set<String> THEMES = Set.of("modern", "warm", "natural", "macos26");
    private static final Set<String> LOGIN_STYLES = Set.of("glass", "split", "minimal");
    private static final long MAX_ICON_SIZE = 5L * 1024 * 1024;
    private static final Map<String, String> ICON_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final SystemConfigRepository configRepository;
    private final OperationLogService operationLogService;

    @Value("${upload.path:${app.upload.dir:./uploads}}")
    private String uploadPath;

    @Transactional(readOnly = true)
    public WebsiteSettingsDTO getSettings() {
        Path icon = currentLoginIconPath();
        long iconVersion = 0;
        if (icon != null) {
            try {
                iconVersion = Files.getLastModifiedTime(icon).toMillis();
            } catch (Exception ignored) {
                icon = null;
            }
        }
        Map<String, String> styles = new LinkedHashMap<>();
        THEMES.stream().sorted().forEach(theme -> styles.put(
                theme,
                validStyle(config(LOGIN_STYLE_PREFIX + theme, DEFAULT_LOGIN_STYLE))));
        return new WebsiteSettingsDTO(
                config(SITE_NAME_KEY, DEFAULT_SITE_NAME),
                config(BROWSER_TITLE_KEY, DEFAULT_BROWSER_TITLE),
                config(LOGIN_DESCRIPTION_KEY, DEFAULT_LOGIN_DESCRIPTION),
                isRegistrationEnabled(),
                styles,
                icon != null,
                icon == null ? null : "/api/site/login-icon",
                iconVersion);
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationEnabled() {
        return Boolean.parseBoolean(config(REGISTRATION_ENABLED_KEY, "true"));
    }

    @Transactional
    public WebsiteSettingsDTO update(User user, WebsiteSettingsUpdateRequest request) {
        if (request == null) {
            throw badRequest("网站设置不能为空");
        }
        String siteName = requiredText(request.siteName(), "网站名称", 40);
        String browserTitle = requiredText(request.browserTitle(), "浏览器标签页名称", 80);
        String description = optionalText(request.loginDescription(), "登录页网站简介", 200);
        boolean registrationEnabled = request.registrationEnabled() == null
                || request.registrationEnabled();
        Map<String, String> styles = normalizeStyles(request.loginStyles());

        save(SITE_NAME_KEY, siteName, "登录页网站名称");
        save(BROWSER_TITLE_KEY, browserTitle, "浏览器标签页名称");
        save(LOGIN_DESCRIPTION_KEY, description, "登录页网站简介");
        save(REGISTRATION_ENABLED_KEY, Boolean.toString(registrationEnabled), "是否允许公开注册");
        styles.forEach((theme, style) -> save(
                LOGIN_STYLE_PREFIX + theme,
                style,
                theme + " 主题的登录页样式"));
        operationLogService.record(
                user,
                OperationLog.Action.UPDATE_SITE_SETTINGS,
                null,
                "更新网站基本信息",
                null);
        return getSettings();
    }

    @Transactional
    public WebsiteSettingsDTO uploadLoginIcon(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("请选择登录页图标");
        if (file.getSize() > MAX_ICON_SIZE) throw badRequest("登录页图标不能超过5MB");

        Path newFile = null;
        try {
            byte[] bytes = file.getBytes();
            String contentType = detectImageType(bytes);
            String extension = ICON_TYPES.get(contentType);
            if (extension == null) throw badRequest("仅支持 JPG、PNG 或 WebP 图片");
            Path directory = loginIconDirectory();
            Files.createDirectories(directory);
            newFile = directory.resolve("login-icon-" + UUID.randomUUID() + extension);
            Files.write(newFile, bytes);
            String oldPath = configuredLoginIconPath();
            save(LOGIN_ICON_PATH_KEY, relativePath(newFile), "登录页图标文件路径");
            deleteManagedIcon(oldPath);
            operationLogService.record(
                    user,
                    OperationLog.Action.UPDATE_SITE_SETTINGS,
                    null,
                    "更新登录页图标",
                    null);
            return getSettings();
        } catch (ResponseStatusException exception) {
            deleteQuietly(newFile);
            throw exception;
        } catch (Exception exception) {
            deleteQuietly(newFile);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "登录页图标保存失败", exception);
        }
    }

    @Transactional
    public WebsiteSettingsDTO restoreDefaultLoginIcon(User user) {
        String oldPath = configuredLoginIconPath();
        save(LOGIN_ICON_PATH_KEY, "", "登录页图标文件路径");
        deleteManagedIcon(oldPath);
        operationLogService.record(
                user,
                OperationLog.Action.UPDATE_SITE_SETTINGS,
                null,
                "恢复默认登录页图标",
                null);
        return getSettings();
    }

    @Transactional(readOnly = true)
    public LoginIconContent getLoginIconContent() {
        Path icon = currentLoginIconPath();
        if (icon == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未设置自定义登录页图标");
        }
        return new LoginIconContent(icon, contentType(icon.getFileName().toString()));
    }

    private Map<String, String> normalizeStyles(Map<String, String> requested) {
        Map<String, String> styles = new LinkedHashMap<>();
        for (String theme : THEMES) {
            String style = requested == null ? null : requested.get(theme);
            if (style == null) style = config(LOGIN_STYLE_PREFIX + theme, DEFAULT_LOGIN_STYLE);
            if (!LOGIN_STYLES.contains(style)) {
                throw badRequest("登录页样式无效：" + theme);
            }
            styles.put(theme, style);
        }
        return styles;
    }

    private String validStyle(String style) {
        return LOGIN_STYLES.contains(style) ? style : DEFAULT_LOGIN_STYLE;
    }

    private String requiredText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw badRequest(label + "不能为空");
        if (normalized.length() > maxLength) throw badRequest(label + "不能超过" + maxLength + "个字符");
        return normalized;
    }

    private String optionalText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) throw badRequest(label + "不能超过" + maxLength + "个字符");
        return normalized;
    }

    private String config(String key, String fallback) {
        return configRepository.findById(key)
                .map(SystemConfig::getConfigValue)
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    private void save(String key, String value, String description) {
        SystemConfig config = configRepository.findById(key)
                .orElse(SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        config.setDescription(description);
        configRepository.save(config);
    }

    private String configuredLoginIconPath() {
        return config(LOGIN_ICON_PATH_KEY, null);
    }

    private Path currentLoginIconPath() {
        String configuredPath = configuredLoginIconPath();
        if (configuredPath == null) return null;
        Path directory = loginIconDirectory().toAbsolutePath().normalize();
        Path icon = Paths.get(uploadPath).resolve(configuredPath).toAbsolutePath().normalize();
        return icon.startsWith(directory) && Files.isRegularFile(icon) ? icon : null;
    }

    private Path loginIconDirectory() {
        return Paths.get(uploadPath, "site", "login");
    }

    private String relativePath(Path file) {
        return Paths.get("site", "login", file.getFileName().toString()).toString();
    }

    private void deleteManagedIcon(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        Path directory = loginIconDirectory().toAbsolutePath().normalize();
        Path file = Paths.get(uploadPath).resolve(relativePath).toAbsolutePath().normalize();
        if (file.startsWith(directory)) deleteQuietly(file);
    }

    private void deleteQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 配置已更新，旧图标清理失败不影响新图标使用。
        }
    }

    private String detectImageType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') return "image/webp";
        return null;
    }

    private String contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record LoginIconContent(Path path, String contentType) {}
}
