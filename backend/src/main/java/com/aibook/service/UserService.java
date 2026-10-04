package com.aibook.service;

import com.aibook.config.ScanSettings;
import com.aibook.dto.UserPreferencesDTO;
import com.aibook.dto.DockNavigationItemDTO;
import com.aibook.dto.RewriteSearchRuleDTO;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private static final Set<Integer> AUTHOR_PAGE_SIZES = Set.of(10, 20, 50);
    private static final Set<Integer> CATEGORY_PAGE_SIZES = Set.of(10, 20, 50, 100);
    private static final Set<Integer> CRAWLER_CHAPTER_PAGE_SIZES = Set.of(20, 50, 100);
    private static final Set<Integer> DEFAULT_CRAWLER_POLLING_INTERVALS =
            Set.of(1, 3, 5, 10, 30);
    private static final Set<String> CRAWLER_DISCOVERY_VIEW_MODES = Set.of("table", "card");
    private static final Set<String> CRAWLER_BOOK_VIEW_MODES = Set.of("table", "card");
    private static final Set<String> READER_APPEARANCES =
            Set.of("classic", "readingRoom", "trialReader");
    private static final Set<String> READER_EPUB_ENGINES = Set.of("epubjs", "readium");
    private static final Set<String> READER_CONTENT_WIDTHS =
            Set.of("narrow", "medium", "wide", "wider", "full");
    private static final Set<String> READER_BACKGROUND_COLORS =
            Set.of("auto", "#ffffff", "#f5f5dc", "#e8f5e9", "#fff8e1", "#2d2d2d", "#1a1a2e");
    private static final Set<String> READER_SCREEN_MODES = Set.of("single", "double");
    private static final Set<String> READER_BUILT_IN_FONTS = Set.of(
            "default", "SimSun, serif", "SimHei, sans-serif", "KaiTi, serif", "FangSong, serif");
    private static final Pattern MANAGED_READER_FONT_PATTERN = Pattern.compile("^managed:[1-9][0-9]*$");
    private static final Pattern READER_BACKGROUND_PATTERN = Pattern.compile(
            "^(none|warm-paper|rice-paper|sage-linen|mist-blue|custom-[1-9][0-9]*)$");
    private static final int DEFAULT_LIBRARY_PAGE_SIZE = 10;
    private static final Set<String> DOCK_ICON_STYLES =
            Set.of("minimal", "skeuomorphic", "macos26", "custom");
    private static final Set<String> DOCK_NAV_KEYS = Set.of("home", "library", "rewrite",
            "shelf", "repair", "conversion", "crawler", "statistics", "settings");
    private static final Set<String> DOCK_NAV_ICONS = Set.of("home", "library", "rewrite",
            "shelf", "repair", "conversion", "crawler", "statistics", "settings",
            "trashEmpty", "trashFull");
    private static final Set<String> REWRITE_SEARCH_SCOPES = Set.of("BOOK", "CHAPTER", "VOLUME");
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
    @Autowired
    private CrawlerPollingIntervalOptionsService crawlerPollingIntervalOptionsService;

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

    public List<RewriteSearchRuleDTO> getRewriteSearchRules(String username) {
        return readRewriteSearchRules(findByUsername(username).getRewriteSearchRules());
    }

    @Transactional
    public List<RewriteSearchRuleDTO> updateRewriteSearchRules(
            String username, List<RewriteSearchRuleDTO> rules) {
        User user = findByUsername(username);
        List<RewriteSearchRuleDTO> normalized = normalizeRewriteSearchRules(rules);
        user.setRewriteSearchRules(serializeRewriteSearchRules(normalized));
        userRepository.save(user);
        return normalized;
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
        if (request.getAuthorPageSize() != null) {
            requireAllowed("作者列表分页大小", request.getAuthorPageSize(), AUTHOR_PAGE_SIZES);
            user.setAuthorPageSize(request.getAuthorPageSize());
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
        if (request.getCrawlerFollowCurrentChapter() != null) {
            user.setCrawlerFollowCurrentChapter(request.getCrawlerFollowCurrentChapter());
        }
        if (request.getCrawlerPollingIntervalSeconds() != null) {
            Set<Integer> allowedIntervals = crawlerPollingIntervalOptionsService == null
                    ? DEFAULT_CRAWLER_POLLING_INTERVALS
                    : Set.copyOf(crawlerPollingIntervalOptionsService.getOptions());
            requireAllowed("采集自动刷新频率",
                    request.getCrawlerPollingIntervalSeconds(), allowedIntervals);
            user.setCrawlerPollingIntervalSeconds(request.getCrawlerPollingIntervalSeconds());
        }
        if (request.getCrawlerChapterPageSize() != null) {
            requireAllowed("采集章节分页大小", request.getCrawlerChapterPageSize(),
                    CRAWLER_CHAPTER_PAGE_SIZES);
            user.setCrawlerChapterPageSize(request.getCrawlerChapterPageSize());
        }
        if (request.getCrawlerDiscoveryViewMode() != null) {
            requireAllowed("发现书籍显示方式", request.getCrawlerDiscoveryViewMode(),
                    CRAWLER_DISCOVERY_VIEW_MODES);
            user.setCrawlerDiscoveryViewMode(request.getCrawlerDiscoveryViewMode());
        }
        if (request.getCrawlerBookViewMode() != null) {
            requireAllowed("采集书籍显示方式", request.getCrawlerBookViewMode(),
                    CRAWLER_BOOK_VIEW_MODES);
            user.setCrawlerBookViewMode(request.getCrawlerBookViewMode());
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
        if (request.getDockNavigationItems() != null) {
            user.setDockNavigationSettings(serializeDockNavigationItems(
                    normalizeDockNavigationItems(request.getDockNavigationItems())));
        }
        if (request.getRewriteSearchRules() != null) {
            user.setRewriteSearchRules(serializeRewriteSearchRules(
                    normalizeRewriteSearchRules(request.getRewriteSearchRules())));
        }
        if (request.hasUiFontId()) {
            validateFont(request.getUiFontId());
            user.setUiFontId(request.getUiFontId());
        }
        if (request.hasReaderFontId()) {
            validateFont(request.getReaderFontId());
            user.setReaderFontId(request.getReaderFontId());
        }
        if (request.getReaderSettings() != null) {
            user.setReaderSettings(serializeReaderSettings(
                    normalizeReaderSettings(request.getReaderSettings())));
        }
        if (request.getCategoryPageSize() != null) {
            requireAllowed("分类管理分页大小", request.getCategoryPageSize(), CATEGORY_PAGE_SIZES);
            user.setCategoryPageSize(request.getCategoryPageSize());
        }
        if (request.getQuickReaderWindow() != null) {
            user.setQuickReaderWindow(serializeQuickReaderWindow(
                    normalizeQuickReaderWindow(request.getQuickReaderWindow())));
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
                .authorPageSize(normalizeAuthorPageSize(user.getAuthorPageSize()))
                .categoryPageSize(user.getCategoryPageSize() != null
                        && CATEGORY_PAGE_SIZES.contains(user.getCategoryPageSize()) ? user.getCategoryPageSize() : 20)
                .scanThreadCount(
                        ScanSettings.normalizeThreadCount(user.getScanThreadCount()))
                .crawlerPollingIntervalSeconds(user.getCrawlerPollingIntervalSeconds())
                .crawlerFollowCurrentChapter(user.getCrawlerFollowCurrentChapter())
                .crawlerChapterPageSize(user.getCrawlerChapterPageSize())
                .crawlerDiscoveryViewMode(user.getCrawlerDiscoveryViewMode())
                .crawlerBookViewMode(user.getCrawlerBookViewMode())
                .dockSize(defaultIfNull(user.getDockSize(), DEFAULT_DOCK_SIZE))
                .dockOpacity(defaultIfNull(user.getDockOpacity(), DEFAULT_DOCK_OPACITY))
                .dockMagnification(defaultIfNull(
                        user.getDockMagnification(), DEFAULT_DOCK_MAGNIFICATION))
                .dockBlur(defaultIfNull(user.getDockBlur(), DEFAULT_DOCK_BLUR))
                .dockIconStyle(defaultIfBlank(
                        user.getDockIconStyle(), DEFAULT_DOCK_ICON_STYLE))
                .dockNavigationItems(readDockNavigationItems(user.getDockNavigationSettings()))
                .rewriteSearchRules(readRewriteSearchRules(user.getRewriteSearchRules()))
                .uiFontId(activeFontId(user.getUiFontId()))
                .readerFontId(activeFontId(user.getReaderFontId()))
                .readerSettings(readReaderSettings(user.getReaderSettings()))
                .quickReaderWindow(readQuickReaderWindow(user.getQuickReaderWindow()))
                .build();
    }

    private List<RewriteSearchRuleDTO> normalizeRewriteSearchRules(
            List<RewriteSearchRuleDTO> rules) {
        if (rules == null || rules.size() > 50) {
            throw new IllegalArgumentException("最多保存 50 条查找替换规则");
        }
        Set<String> names = new java.util.HashSet<>();
        List<RewriteSearchRuleDTO> normalized = new ArrayList<>();
        for (RewriteSearchRuleDTO rule : rules) {
            if (rule == null || rule.name() == null || rule.query() == null
                    || rule.name().isBlank() || rule.name().length() > 60
                    || rule.query().isBlank() || rule.query().length() > 100
                    || Objects.requireNonNullElse(rule.replacement(), "").length() > 10_000) {
                throw new IllegalArgumentException("规则名称和查找内容不能为空且不超过限制");
            }
            String name = rule.name().strip();
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("查找替换规则名称不能重复");
            }
            String scope = Objects.requireNonNullElse(rule.scope(), "BOOK").toUpperCase(Locale.ROOT);
            requireAllowed("查找范围", scope, REWRITE_SEARCH_SCOPES);
            normalized.add(new RewriteSearchRuleDTO(name, rule.query(),
                    Objects.requireNonNullElse(rule.replacement(), ""), scope,
                    Boolean.TRUE.equals(rule.matchCase()), Boolean.TRUE.equals(rule.wholeWord()),
                    Boolean.TRUE.equals(rule.regex())));
        }
        return List.copyOf(normalized);
    }

    private String serializeRewriteSearchRules(List<RewriteSearchRuleDTO> rules) {
        try {
            return OBJECT_MAPPER.writeValueAsString(rules);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存查找替换规则", exception);
        }
    }

    private List<RewriteSearchRuleDTO> readRewriteSearchRules(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            List<RewriteSearchRuleDTO> rules = OBJECT_MAPPER.readValue(
                    value, new TypeReference<>() { });
            return normalizeRewriteSearchRules(rules);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<DockNavigationItemDTO> normalizeDockNavigationItems(
            List<DockNavigationItemDTO> items) {
        if (items == null || items.size() != DOCK_NAV_KEYS.size()) {
            throw new IllegalArgumentException("Dock 导航必须包含全部内置项目");
        }
        Set<String> keys = new java.util.HashSet<>();
        Set<Integer> positions = new java.util.HashSet<>();
        List<DockNavigationItemDTO> normalized = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            DockNavigationItemDTO item = items.get(index);
            if (item == null || !DOCK_NAV_KEYS.contains(item.getKey())
                    || !keys.add(item.getKey())) {
                throw new IllegalArgumentException("Dock 导航项目无效或重复");
            }
            String title = item.getTitle();
            if (title == null || title.isBlank() || title.length() > 30) {
                throw new IllegalArgumentException("Dock 导航名称不能为空且不超过 30 字");
            }
            if (!DOCK_NAV_ICONS.contains(item.getIcon())) {
                throw new IllegalArgumentException("Dock 图标类型无效");
            }
            int order = item.getOrder() == null ? index : item.getOrder();
            if (order < 0 || order >= items.size() || !positions.add(order)) {
                throw new IllegalArgumentException("Dock 导航顺序无效");
            }
            normalized.add(new DockNavigationItemDTO(item.getKey(), title.strip(), item.getIcon(),
                    item.getEnabled() == null || item.getEnabled(), order));
        }
        if (!keys.equals(DOCK_NAV_KEYS)) throw new IllegalArgumentException("Dock 导航项目不完整");
        normalized.sort(Comparator.comparing(DockNavigationItemDTO::getOrder));
        for (int index = 0; index < normalized.size(); index++) {
            normalized.get(index).setOrder(index);
        }
        return normalized;
    }

    private String serializeDockNavigationItems(List<DockNavigationItemDTO> items) {
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存 Dock 导航设置", exception);
        }
    }

    private List<DockNavigationItemDTO> readDockNavigationItems(String value) {
        if (value == null || value.isBlank()) return defaultDockNavigationItems();
        try {
            List<DockNavigationItemDTO> items = OBJECT_MAPPER.readValue(value, new TypeReference<>() { });
            return normalizeDockNavigationItems(items);
        } catch (Exception exception) {
            return defaultDockNavigationItems();
        }
    }

    private List<DockNavigationItemDTO> defaultDockNavigationItems() {
        return List.of(
                new DockNavigationItemDTO("home", "首页", "home", true, 0),
                new DockNavigationItemDTO("library", "书库", "library", true, 1),
                new DockNavigationItemDTO("rewrite", "重写", "rewrite", true, 2),
                new DockNavigationItemDTO("shelf", "书架", "shelf", true, 3),
                new DockNavigationItemDTO("repair", "内容修复", "repair", true, 4),
                new DockNavigationItemDTO("conversion", "格式转换", "conversion", true, 5),
                new DockNavigationItemDTO("crawler", "书籍爬虫", "crawler", true, 6),
                new DockNavigationItemDTO("statistics", "阅读统计", "statistics", true, 7),
                new DockNavigationItemDTO("settings", "设置", "settings", true, 8));
    }

    private int normalizeLibraryPageSize(Integer value, int fallback) {
        return value != null && LIBRARY_PAGE_SIZES.contains(value) ? value : fallback;
    }

    private int normalizeAuthorPageSize(Integer value) {
        return value != null && AUTHOR_PAGE_SIZES.contains(value) ? value : 10;
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

    private UserPreferencesDTO.ReaderSettingsDTO normalizeReaderSettings(
            UserPreferencesDTO.ReaderSettingsDTO settings) {
        requireAllowed("阅读器外观", settings.getAppearance(), READER_APPEARANCES);
        requireAllowed("EPUB 阅读引擎", settings.getEpubEngine(), READER_EPUB_ENGINES);
        if (!READER_BUILT_IN_FONTS.contains(settings.getFontFamily())
                && (settings.getFontFamily() == null
                || !MANAGED_READER_FONT_PATTERN.matcher(settings.getFontFamily()).matches())) {
            throw new IllegalArgumentException("阅读字体不支持该值: " + settings.getFontFamily());
        }
        requireRange("阅读字号", requireValue("阅读字号", settings.getFontSize()), 12, 28);
        double lineHeight = requireValue("阅读行距", settings.getLineHeight());
        if (!Double.isFinite(lineHeight) || lineHeight < 1.2 || lineHeight > 2.5) {
            throw new IllegalArgumentException("阅读行距必须在 1.2 到 2.5 之间");
        }
        requireRange("阅读段距", requireValue("阅读段距", settings.getParagraphSpacing()), 0, 40);
        requireAllowed("阅读宽度", settings.getContentWidth(), READER_CONTENT_WIDTHS);
        requireAllowed("阅读背景色", settings.getBackgroundColor(), READER_BACKGROUND_COLORS);
        if (settings.getBackgroundImageId() == null
                || !READER_BACKGROUND_PATTERN.matcher(settings.getBackgroundImageId()).matches()) {
            throw new IllegalArgumentException("阅读背景壁纸不支持该值: " + settings.getBackgroundImageId());
        }
        requireAllowed("单双页模式", settings.getScreenMode(), READER_SCREEN_MODES);
        requireValue("翻页模式", settings.getPaginationMode());
        requireValue("首行缩进", settings.getTextIndent());
        requireValue("阅读进度显示", settings.getShowProgress());
        return settings;
    }

    private String serializeReaderSettings(UserPreferencesDTO.ReaderSettingsDTO settings) {
        try {
            return OBJECT_MAPPER.writeValueAsString(settings);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存阅读设置", exception);
        }
    }

    private UserPreferencesDTO.ReaderSettingsDTO readReaderSettings(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return normalizeReaderSettings(OBJECT_MAPPER.readValue(
                    value, UserPreferencesDTO.ReaderSettingsDTO.class));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private UserPreferencesDTO.QuickReaderWindowDTO normalizeQuickReaderWindow(
            UserPreferencesDTO.QuickReaderWindowDTO window) {
        if (window == null) {
            return null;
        }
        requireRange("小窗阅读宽度", requireValue("小窗阅读宽度", window.getWidth()), 120, 10000);
        requireRange("小窗阅读高度", requireValue("小窗阅读高度", window.getHeight()), 160, 10000);
        requireRange("小窗阅读左侧位置", requireValue("小窗阅读左侧位置", window.getLeft()), 0, 10000);
        requireRange("小窗阅读顶部位置", requireValue("小窗阅读顶部位置", window.getTop()), 0, 10000);
        return window;
    }

    private String serializeQuickReaderWindow(
            UserPreferencesDTO.QuickReaderWindowDTO window) {
        try {
            return OBJECT_MAPPER.writeValueAsString(window);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存小窗阅读窗口状态", exception);
        }
    }

    private UserPreferencesDTO.QuickReaderWindowDTO readQuickReaderWindow(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return normalizeQuickReaderWindow(OBJECT_MAPPER.readValue(
                    value, UserPreferencesDTO.QuickReaderWindowDTO.class));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private <T> T requireValue(String label, T value) {
        if (value == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value;
    }
}
