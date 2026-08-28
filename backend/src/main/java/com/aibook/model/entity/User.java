package com.aibook.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    @OneToOne(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private UserPreference preferences;

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

    private UserPreference preference() {
        if (preferences == null) {
            preferences = UserPreference.builder().user(this).build();
        }
        return preferences;
    }

    public String getBookPreferences() {
        return preference().getBookPreferences();
    }

    public void setBookPreferences(String value) {
        preference().setBookPreferences(value);
    }

    public String getWebTheme() {
        return preference().getWebTheme();
    }

    public void setWebTheme(String value) {
        preference().setWebTheme(value);
    }

    public String getModernThemeColor() {
        return preference().getModernThemeColor();
    }

    public void setModernThemeColor(String value) {
        preference().setModernThemeColor(value);
    }

    public String getWarmThemeColor() {
        return preference().getWarmThemeColor();
    }

    public void setWarmThemeColor(String value) {
        preference().setWarmThemeColor(value);
    }

    public String getNaturalThemeColor() {
        return preference().getNaturalThemeColor();
    }

    public void setNaturalThemeColor(String value) {
        preference().setNaturalThemeColor(value);
    }

    public String getMacos26ThemeColor() {
        return preference().getMacos26ThemeColor();
    }

    public void setMacos26ThemeColor(String value) {
        preference().setMacos26ThemeColor(value);
    }

    public String getThemeBackgroundSettings() {
        return preference().getThemeBackgroundSettings();
    }

    public void setThemeBackgroundSettings(String value) {
        preference().setThemeBackgroundSettings(value);
    }

    public String getLibraryViewMode() {
        return preference().getLibraryViewMode();
    }

    public void setLibraryViewMode(String value) {
        preference().setLibraryViewMode(value);
    }

    public Integer getLibraryPageSize() {
        return preference().getLibraryPageSize();
    }

    public void setLibraryPageSize(Integer value) {
        preference().setLibraryPageSize(value);
    }

    public Integer getLibraryListPageSize() {
        return preference().getLibraryListPageSize();
    }

    public void setLibraryListPageSize(Integer value) {
        preference().setLibraryListPageSize(value);
    }

    public Integer getScanThreadCount() {
        return preference().getScanThreadCount();
    }

    public void setScanThreadCount(Integer value) {
        preference().setScanThreadCount(value);
    }

    public Boolean getScheduledScanEnabled() {
        return preference().getScheduledScanEnabled();
    }

    public void setScheduledScanEnabled(Boolean value) {
        preference().setScheduledScanEnabled(value);
    }

    public String getScheduledScanTime() {
        return preference().getScheduledScanTime();
    }

    public void setScheduledScanTime(String value) {
        preference().setScheduledScanTime(value);
    }

    public Integer getTrashRetentionDays() {
        return preference().getTrashRetentionDays();
    }

    public void setTrashRetentionDays(Integer value) {
        preference().setTrashRetentionDays(value);
    }

    public Integer getDockSize() {
        return preference().getDockSize();
    }

    public void setDockSize(Integer value) {
        preference().setDockSize(value);
    }

    public Integer getDockOpacity() {
        return preference().getDockOpacity();
    }

    public void setDockOpacity(Integer value) {
        preference().setDockOpacity(value);
    }

    public Integer getDockMagnification() {
        return preference().getDockMagnification();
    }

    public void setDockMagnification(Integer value) {
        preference().setDockMagnification(value);
    }

    public Integer getDockBlur() {
        return preference().getDockBlur();
    }

    public void setDockBlur(Integer value) {
        preference().setDockBlur(value);
    }

    public String getDockIconStyle() {
        return preference().getDockIconStyle();
    }

    public void setDockIconStyle(String value) {
        preference().setDockIconStyle(value);
    }

    public Long getUiFontId() {
        return preference().getUiFontId();
    }

    public void setUiFontId(Long value) {
        preference().setUiFontId(value);
    }

    public Long getReaderFontId() {
        return preference().getReaderFontId();
    }

    public void setReaderFontId(Long value) {
        preference().setReaderFontId(value);
    }

    public Boolean getAllBookCoversHidden() {
        return preference().getAllBookCoversHidden();
    }

    public void setAllBookCoversHidden(Boolean value) {
        preference().setAllBookCoversHidden(value);
    }

    public String getBookCoverVisibilityOverrides() {
        return preference().getBookCoverVisibilityOverrides();
    }

    public void setBookCoverVisibilityOverrides(String value) {
        preference().setBookCoverVisibilityOverrides(value);
    }

    public Boolean getAllRandomCoversHidden() {
        return preference().getAllRandomCoversHidden();
    }

    public void setAllRandomCoversHidden(Boolean value) {
        preference().setAllRandomCoversHidden(value);
    }

    public String getRandomCoverVisibilityOverrides() {
        return preference().getRandomCoverVisibilityOverrides();
    }

    public void setRandomCoverVisibilityOverrides(String value) {
        preference().setRandomCoverVisibilityOverrides(value);
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
