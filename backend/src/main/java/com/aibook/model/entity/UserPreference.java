package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 与账号基本资料分离存储的用户偏好配置。 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "book_preferences", columnDefinition = "TEXT")
    private String bookPreferences;

    @Column(name = "web_theme")
    private String webTheme;
    @Column(name = "modern_theme_color")
    private String modernThemeColor;
    @Column(name = "warm_theme_color")
    private String warmThemeColor;
    @Column(name = "natural_theme_color")
    private String naturalThemeColor;
    @Column(name = "macos26theme_color")
    private String macos26ThemeColor;

    @Column(name = "theme_background_settings", columnDefinition = "TEXT")
    private String themeBackgroundSettings;

    @Column(name = "library_view_mode")
    private String libraryViewMode;
    @Column(name = "library_page_size")
    private Integer libraryPageSize;
    @Column(name = "library_list_page_size")
    private Integer libraryListPageSize;
    @Column(name = "scan_thread_count")
    private Integer scanThreadCount;

    @Column(name = "scheduled_scan_enabled")
    private Boolean scheduledScanEnabled;

    @Column(name = "scheduled_scan_time", length = 5)
    private String scheduledScanTime;

    @Column(name = "trash_retention_days")
    private Integer trashRetentionDays;

    @Column(name = "dock_size")
    private Integer dockSize;
    @Column(name = "dock_opacity")
    private Integer dockOpacity;
    @Column(name = "dock_magnification")
    private Integer dockMagnification;
    @Column(name = "dock_blur")
    private Integer dockBlur;
    @Column(name = "dock_icon_style")
    private String dockIconStyle;
    @Column(name = "ui_font_id")
    private Long uiFontId;
    @Column(name = "reader_font_id")
    private Long readerFontId;
    @Column(name = "all_book_covers_hidden")
    private Boolean allBookCoversHidden;

    @Column(name = "book_cover_visibility_overrides", columnDefinition = "TEXT")
    private String bookCoverVisibilityOverrides;

    @Column(name = "all_random_covers_hidden")
    private Boolean allRandomCoversHidden;

    @Column(name = "random_cover_visibility_overrides", columnDefinition = "TEXT")
    private String randomCoverVisibilityOverrides;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
