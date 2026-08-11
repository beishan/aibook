package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 用户实体
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名
     */
    @Column(unique = true, nullable = false)
    private String username;

    /**
     * 邮箱
     */
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * 密码（加密后）
     */
    @Column(nullable = false)
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 当前心情或个性签名。
     */
    @Column(length = 100)
    private String mood;

    /**
     * 个人备注。
     */
    @Column(columnDefinition = "TEXT")
    private String profileNotes;

    /**
     * 出生日期。
     */
    private LocalDate birthDate;

    /**
     * 偏好的书籍类型、作者或阅读主题。
     */
    @Column(columnDefinition = "TEXT")
    private String bookPreferences;

    /**
     * Web 端主题偏好（modern、warm、natural）
     */
    private String webTheme;

    /**
     * 现代简约主题强调色（#RRGGBB）
     */
    private String modernThemeColor;

    /**
     * 暖色文艺主题强调色（#RRGGBB）
     */
    private String warmThemeColor;

    /**
     * 自然清新主题强调色（#RRGGBB）
     */
    private String naturalThemeColor;

    /**
     * MACOS26 Liquid Glass 主题强调色（#RRGGBB）
     */
    private String macos26ThemeColor;

    /**
     * Web 端各主题的页面、导航与卡片背景配置（JSON）。
     */
    @Column(columnDefinition = "TEXT")
    private String themeBackgroundSettings;

    /**
     * Web 端书库显示方式（card、compact-card、list）
     */
    private String libraryViewMode;

    /**
     * Web 端书库每页显示数量
     */
    private Integer libraryPageSize;

    /**
     * Web 端书库列表视图每页显示数量；卡片视图继续使用 libraryPageSize。
     */
    private Integer libraryListPageSize;

    /**
     * 后端目录扫描工作线程数
     */
    private Integer scanThreadCount;

    /**
     * 是否按用户设置的时间自动扫描已启用目录。
     */
    @Column(name = "scheduled_scan_enabled")
    private Boolean scheduledScanEnabled;

    /**
     * 每日自动扫描时间，格式为 HH:mm。
     */
    @Column(name = "scheduled_scan_time", length = 5)
    private String scheduledScanTime;

    /**
     * 回收站记录保留天数；0 表示不自动清理。
     */
    @Column(name = "trash_retention_days")
    private Integer trashRetentionDays;

    /**
     * 自然清新主题 Dock 图标尺寸（像素）
     */
    private Integer dockSize;

    /**
     * 自然清新主题 Dock 玻璃底色透明度（百分比）
     */
    private Integer dockOpacity;

    /**
     * 自然清新主题 Dock 悬浮放大比例（百分比）
     */
    private Integer dockMagnification;

    /**
     * 自然清新主题 Dock 背景模糊半径（像素）
     */
    private Integer dockBlur;

    /**
     * MACOS 主题 Dock 图标风格（minimal、skeuomorphic、macos26、custom）
     */
    private String dockIconStyle;

    /**
     * 系统界面字体资源 ID。
     */
    private Long uiFontId;

    /**
     * 阅读器默认字体资源 ID。
     */
    private Long readerFontId;

    /**
     * 角色 (USER, ADMIN)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum Role {
        USER,
        ADMIN
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
