package com.aibook.service;

import com.aibook.config.ScanSettings;
import com.aibook.dto.UserPreferencesDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.FontAssetRepository;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 用户服务
 */
@Service
public class UserService implements UserDetailsService {

    private static final Set<String> WEB_THEMES =
            Set.of("modern", "warm", "natural", "macos26");
    private static final Set<String> LEGACY_WEB_THEMES = Set.of("modern", "warm", "natural");
    private static final Set<String> LIBRARY_VIEW_MODES =
            Set.of("card", "compact-card", "list");
    private static final Set<Integer> LIBRARY_PAGE_SIZES =
            Set.of(10, 30, 50, 100, 200);
    private static final int DEFAULT_LIBRARY_PAGE_SIZE = 10;
    private static final Set<String> DOCK_ICON_STYLES =
            Set.of("minimal", "skeuomorphic", "macos26", "custom");
    private static final int DEFAULT_DOCK_SIZE = 58;
    private static final int DEFAULT_DOCK_OPACITY = 72;
    private static final int DEFAULT_DOCK_MAGNIFICATION = 128;
    private static final int DEFAULT_DOCK_BLUR = 24;
    private static final String DEFAULT_DOCK_ICON_STYLE = "minimal";
    private static final String DEFAULT_MODERN_THEME_COLOR = "#2563EB";
    private static final String DEFAULT_WARM_THEME_COLOR = "#A0522D";
    private static final String DEFAULT_NATURAL_THEME_COLOR = "#2E7D5A";
    private static final String DEFAULT_MACOS26_THEME_COLOR = "#007AFF";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserRepository userRepository;
    private final FontAssetRepository fontAssetRepository;

    /**
     * 保留单参数构造器，兼容轻量控制器测试中的 StubUserService。
     */
    public UserService(UserRepository userRepository) {
        this(userRepository, null);
    }

    @Autowired
    public UserService(
            UserRepository userRepository,
            FontAssetRepository fontAssetRepository) {
        this.userRepository = userRepository;
        this.fontAssetRepository = fontAssetRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }

    /**
     * 根据用户名查找用户
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }

    public UserPreferencesDTO getPreferences(String username) {
        User user = findByUsername(username);
        return toPreferences(user);
    }

    @Transactional
    public UserPreferencesDTO updatePreferences(
            String username,
            UserPreferencesDTO request) {
        User user = findByUsername(username);

        if (request.getTheme() != null) {
            requireAllowed("主题", request.getTheme(), WEB_THEMES);
            user.setWebTheme(request.getTheme());
        }
        if (request.getLibraryViewMode() != null) {
            requireAllowed(
                    "书库显示方式",
                    request.getLibraryViewMode(),
                    LIBRARY_VIEW_MODES);
            user.setLibraryViewMode(request.getLibraryViewMode());
        }
        if (request.getLibraryPageSize() != null) {
            requireAllowed(
                    "书库分页大小",
                    request.getLibraryPageSize(),
                    LIBRARY_PAGE_SIZES);
            user.setLibraryPageSize(request.getLibraryPageSize());
        }
        if (request.getLibraryCardPageSize() != null) {
            requireAllowed(
                    "书库卡片分页大小",
                    request.getLibraryCardPageSize(),
                    LIBRARY_PAGE_SIZES);
            user.setLibraryPageSize(request.getLibraryCardPageSize());
        }
        if (request.getLibraryListPageSize() != null) {
            requireAllowed(
                    "书库列表分页大小",
                    request.getLibraryListPageSize(),
                    LIBRARY_PAGE_SIZES);
            user.setLibraryListPageSize(request.getLibraryListPageSize());
        }
        if (request.getScanThreadCount() != null) {
            if (!ScanSettings.isValidThreadCount(request.getScanThreadCount())) {
                throw new IllegalArgumentException(
                        "扫描线程数必须在 "
                                + ScanSettings.MIN_THREAD_COUNT
                                + " 到 "
                                + ScanSettings.MAX_THREAD_COUNT
                                + " 之间");
            }
            user.setScanThreadCount(request.getScanThreadCount());
        }
        if (request.getModernThemeColor() != null) {
            user.setModernThemeColor(normalizeThemeColor(request.getModernThemeColor()));
        }
        if (request.getWarmThemeColor() != null) {
            user.setWarmThemeColor(normalizeThemeColor(request.getWarmThemeColor()));
        }
        if (request.getNaturalThemeColor() != null) {
            user.setNaturalThemeColor(normalizeThemeColor(request.getNaturalThemeColor()));
        }
        if (request.getMacos26ThemeColor() != null) {
            user.setMacos26ThemeColor(normalizeThemeColor(request.getMacos26ThemeColor()));
        }
        if (request.getThemeBackgrounds() != null) {
            user.setThemeBackgroundSettings(serializeThemeBackgrounds(
                    normalizeThemeBackgrounds(request.getThemeBackgrounds())));
        }
        if (request.getDockSize() != null) {
            requireRange("Dock 大小", request.getDockSize(), 44, 76);
            user.setDockSize(request.getDockSize());
        }
        if (request.getDockOpacity() != null) {
            requireRange("Dock 透明度", request.getDockOpacity(), 40, 96);
            user.setDockOpacity(request.getDockOpacity());
        }
        if (request.getDockMagnification() != null) {
            requireRange("Dock 悬浮放大", request.getDockMagnification(), 100, 150);
            user.setDockMagnification(request.getDockMagnification());
        }
        if (request.getDockBlur() != null) {
            requireRange("Dock 玻璃模糊", request.getDockBlur(), 8, 40);
            user.setDockBlur(request.getDockBlur());
        }
        if (request.getDockIconStyle() != null) {
            requireAllowed("Dock 图标风格", request.getDockIconStyle(), DOCK_ICON_STYLES);
            user.setDockIconStyle(request.getDockIconStyle());
        }
        if (request.hasUiFontId()) {
            validateFont(request.getUiFontId());
            user.setUiFontId(request.getUiFontId());
        }
        if (request.hasReaderFontId()) {
            validateFont(request.getReaderFontId());
            user.setReaderFontId(request.getReaderFontId());
        }

        return toPreferences(userRepository.save(user));
    }

    private UserPreferencesDTO toPreferences(User user) {
        int cardPageSize = normalizeLibraryPageSize(user.getLibraryPageSize(),
                DEFAULT_LIBRARY_PAGE_SIZE);
        int listPageSize = normalizeLibraryPageSize(user.getLibraryListPageSize(), cardPageSize);
        return UserPreferencesDTO.builder()
                .theme(user.getWebTheme())
                .modernThemeColor(defaultIfBlank(
                        user.getModernThemeColor(), DEFAULT_MODERN_THEME_COLOR))
                .warmThemeColor(defaultIfBlank(
                        user.getWarmThemeColor(), DEFAULT_WARM_THEME_COLOR))
                .naturalThemeColor(defaultIfBlank(
                        user.getNaturalThemeColor(), DEFAULT_NATURAL_THEME_COLOR))
                .macos26ThemeColor(defaultIfBlank(
                        user.getMacos26ThemeColor(), DEFAULT_MACOS26_THEME_COLOR))
                .themeBackgrounds(readThemeBackgrounds(user.getThemeBackgroundSettings()))
                .libraryViewMode(user.getLibraryViewMode())
                .libraryPageSize(cardPageSize)
                .libraryCardPageSize(cardPageSize)
                .libraryListPageSize(listPageSize)
                .scanThreadCount(
                        ScanSettings.normalizeThreadCount(user.getScanThreadCount()))
                .dockSize(defaultIfNull(user.getDockSize(), DEFAULT_DOCK_SIZE))
                .dockOpacity(defaultIfNull(user.getDockOpacity(), DEFAULT_DOCK_OPACITY))
                .dockMagnification(defaultIfNull(
                        user.getDockMagnification(), DEFAULT_DOCK_MAGNIFICATION))
                .dockBlur(defaultIfNull(user.getDockBlur(), DEFAULT_DOCK_BLUR))
                .dockIconStyle(defaultIfBlank(
                        user.getDockIconStyle(), DEFAULT_DOCK_ICON_STYLE))
                .uiFontId(activeFontId(user.getUiFontId()))
                .readerFontId(activeFontId(user.getReaderFontId()))
                .build();
    }

    private int normalizeLibraryPageSize(Integer value, int fallback) {
        return value != null && LIBRARY_PAGE_SIZES.contains(value) ? value : fallback;
    }

    private void validateFont(Long id) {
        if (id == null) {
            return;
        }
        if (fontAssetRepository == null
                || fontAssetRepository.findByIdAndEnabledTrue(id).isEmpty()) {
            throw new IllegalArgumentException("字体不存在或未启用: " + id);
        }
    }

    private Long activeFontId(Long id) {
        if (id == null || fontAssetRepository == null) {
            return id;
        }
        return fontAssetRepository.findByIdAndEnabledTrue(id).isPresent()
                ? id
                : null;
    }

    private <T> void requireAllowed(String label, T value, Set<T> allowedValues) {
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException(label + "不支持该值: " + value);
        }
    }

    private void requireRange(String label, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    label + "必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private int defaultIfNull(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String normalizeThemeColor(String value) {
        if (value == null) {
            throw new IllegalArgumentException("主题颜色不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!HEX_COLOR_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("主题色必须使用 #RRGGBB 格式");
        }
        return normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Map<String, UserPreferencesDTO.ThemeBackgroundDTO> normalizeThemeBackgrounds(
            Map<String, UserPreferencesDTO.ThemeBackgroundDTO> backgrounds) {
        if (!backgrounds.keySet().equals(WEB_THEMES)
                && !backgrounds.keySet().equals(LEGACY_WEB_THEMES)) {
            throw new IllegalArgumentException(
                    "背景设置必须完整包含 modern、warm、natural、macos26 四个主题");
        }
        Map<String, UserPreferencesDTO.ThemeBackgroundDTO> normalized = new LinkedHashMap<>();
        for (String theme : new String[] {"modern", "warm", "natural", "macos26"}) {
            UserPreferencesDTO.ThemeBackgroundDTO value = backgrounds.get(theme);
            if (value == null && "macos26".equals(theme)) {
                value = defaultThemeBackgrounds().get(theme);
            }
            if (value == null || (!"solid".equals(value.getMode())
                    && !"gradient".equals(value.getMode()))) {
                throw new IllegalArgumentException("背景模式必须为 solid 或 gradient");
            }
            if (value.getNavOpacity() == null || value.getSurfaceOpacity() == null) {
                throw new IllegalArgumentException("背景透明度不能为空");
            }
            requireRange("导航透明度", value.getNavOpacity(), 20, 100);
            requireRange("卡片透明度", value.getSurfaceOpacity(), 35, 100);
            normalized.put(theme, new UserPreferencesDTO.ThemeBackgroundDTO(
                    value.getMode(),
                    normalizeThemeColor(value.getPageColor()),
                    normalizeThemeColor(value.getSecondaryColor()),
                    normalizeThemeColor(value.getNavColor()),
                    value.getNavOpacity(),
                    normalizeThemeColor(value.getSurfaceColor()),
                    value.getSurfaceOpacity()));
        }
        return normalized;
    }

    private String serializeThemeBackgrounds(
            Map<String, UserPreferencesDTO.ThemeBackgroundDTO> backgrounds) {
        try {
            return OBJECT_MAPPER.writeValueAsString(backgrounds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存主题背景设置", exception);
        }
    }

    private Map<String, UserPreferencesDTO.ThemeBackgroundDTO> readThemeBackgrounds(String value) {
        if (value == null || value.isBlank()) {
            return defaultThemeBackgrounds();
        }
        try {
            Map<String, UserPreferencesDTO.ThemeBackgroundDTO> backgrounds =
                    OBJECT_MAPPER.readValue(value, new TypeReference<>() {});
            return normalizeThemeBackgrounds(backgrounds);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return defaultThemeBackgrounds();
        }
    }

    private Map<String, UserPreferencesDTO.ThemeBackgroundDTO> defaultThemeBackgrounds() {
        Map<String, UserPreferencesDTO.ThemeBackgroundDTO> defaults = new LinkedHashMap<>();
        defaults.put("modern", new UserPreferencesDTO.ThemeBackgroundDTO(
                "solid", "#F5F5F5", "#EEF2F7", "#FFFFFF", 100, "#FFFFFF", 100));
        defaults.put("warm", new UserPreferencesDTO.ThemeBackgroundDTO(
                "solid", "#FAF6F1", "#F3E9DC", "#FFFBF5", 100, "#FFFBF5", 100));
        defaults.put("natural", new UserPreferencesDTO.ThemeBackgroundDTO(
                "gradient", "#E8F5E9", "#E0F2F1", "#FFFFFF", 75, "#FFFFFF", 72));
        defaults.put("macos26", new UserPreferencesDTO.ThemeBackgroundDTO(
                "gradient", "#DCEBFA", "#F1E4F8", "#F8FBFF", 62, "#FFFFFF", 58));
        return defaults;
    }
}
