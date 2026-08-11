package com.aibook.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.Map;

/**
 * 用户界面偏好。字体字段记录是否出现在请求 JSON 中，以支持 partial PUT
 * 和显式传 null 清空之间的区别。
 */
public class UserPreferencesDTO {

    private String theme;
    private String modernThemeColor;
    private String warmThemeColor;
    private String naturalThemeColor;
    private Map<String, ThemeBackgroundDTO> themeBackgrounds;
    private String libraryViewMode;
    private Integer libraryPageSize;
    private Integer libraryCardPageSize;
    private Integer libraryListPageSize;
    private Integer scanThreadCount;
    private Integer dockSize;
    private Integer dockOpacity;
    private Integer dockMagnification;
    private Integer dockBlur;
    private String dockIconStyle;
    private Long uiFontId;
    private Long readerFontId;

    @JsonIgnore
    private boolean uiFontIdPresent;
    @JsonIgnore
    private boolean readerFontIdPresent;

    public UserPreferencesDTO() {
    }

    public static UserPreferencesDTOBuilder builder() {
        return new UserPreferencesDTOBuilder();
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getModernThemeColor() {
        return modernThemeColor;
    }

    public void setModernThemeColor(String modernThemeColor) {
        this.modernThemeColor = modernThemeColor;
    }

    public String getWarmThemeColor() {
        return warmThemeColor;
    }

    public void setWarmThemeColor(String warmThemeColor) {
        this.warmThemeColor = warmThemeColor;
    }

    public String getNaturalThemeColor() {
        return naturalThemeColor;
    }

    public void setNaturalThemeColor(String naturalThemeColor) {
        this.naturalThemeColor = naturalThemeColor;
    }

    public Map<String, ThemeBackgroundDTO> getThemeBackgrounds() {
        return themeBackgrounds;
    }

    public void setThemeBackgrounds(Map<String, ThemeBackgroundDTO> themeBackgrounds) {
        this.themeBackgrounds = themeBackgrounds;
    }

    public String getLibraryViewMode() {
        return libraryViewMode;
    }

    public void setLibraryViewMode(String libraryViewMode) {
        this.libraryViewMode = libraryViewMode;
    }

    public Integer getLibraryPageSize() {
        return libraryPageSize;
    }

    public void setLibraryPageSize(Integer libraryPageSize) {
        this.libraryPageSize = libraryPageSize;
    }

    public Integer getLibraryCardPageSize() {
        return libraryCardPageSize;
    }

    public void setLibraryCardPageSize(Integer libraryCardPageSize) {
        this.libraryCardPageSize = libraryCardPageSize;
    }

    public Integer getLibraryListPageSize() {
        return libraryListPageSize;
    }

    public void setLibraryListPageSize(Integer libraryListPageSize) {
        this.libraryListPageSize = libraryListPageSize;
    }

    public Integer getScanThreadCount() {
        return scanThreadCount;
    }

    public void setScanThreadCount(Integer scanThreadCount) {
        this.scanThreadCount = scanThreadCount;
    }

    public Integer getDockSize() {
        return dockSize;
    }

    public void setDockSize(Integer dockSize) {
        this.dockSize = dockSize;
    }

    public Integer getDockOpacity() {
        return dockOpacity;
    }

    public void setDockOpacity(Integer dockOpacity) {
        this.dockOpacity = dockOpacity;
    }

    public Integer getDockMagnification() {
        return dockMagnification;
    }

    public void setDockMagnification(Integer dockMagnification) {
        this.dockMagnification = dockMagnification;
    }

    public Integer getDockBlur() {
        return dockBlur;
    }

    public void setDockBlur(Integer dockBlur) {
        this.dockBlur = dockBlur;
    }

    public String getDockIconStyle() {
        return dockIconStyle;
    }

    public void setDockIconStyle(String dockIconStyle) {
        this.dockIconStyle = dockIconStyle;
    }

    public Long getUiFontId() {
        return uiFontId;
    }

    @JsonSetter("uiFontId")
    public void setUiFontId(Long uiFontId) {
        this.uiFontId = uiFontId;
        this.uiFontIdPresent = true;
    }

    public Long getReaderFontId() {
        return readerFontId;
    }

    @JsonSetter("readerFontId")
    public void setReaderFontId(Long readerFontId) {
        this.readerFontId = readerFontId;
        this.readerFontIdPresent = true;
    }

    @JsonIgnore
    public boolean hasUiFontId() {
        return uiFontIdPresent;
    }

    @JsonIgnore
    public boolean hasReaderFontId() {
        return readerFontIdPresent;
    }

    public static final class UserPreferencesDTOBuilder {
        private final UserPreferencesDTO value = new UserPreferencesDTO();

        public UserPreferencesDTOBuilder theme(String theme) {
            value.setTheme(theme);
            return this;
        }

        public UserPreferencesDTOBuilder libraryViewMode(String libraryViewMode) {
            value.setLibraryViewMode(libraryViewMode);
            return this;
        }

        public UserPreferencesDTOBuilder libraryPageSize(Integer libraryPageSize) {
            value.setLibraryPageSize(libraryPageSize);
            return this;
        }

        public UserPreferencesDTOBuilder libraryCardPageSize(Integer libraryCardPageSize) {
            value.setLibraryCardPageSize(libraryCardPageSize);
            return this;
        }

        public UserPreferencesDTOBuilder libraryListPageSize(Integer libraryListPageSize) {
            value.setLibraryListPageSize(libraryListPageSize);
            return this;
        }

        public UserPreferencesDTOBuilder scanThreadCount(Integer scanThreadCount) {
            value.setScanThreadCount(scanThreadCount);
            return this;
        }

        public UserPreferencesDTOBuilder modernThemeColor(String modernThemeColor) {
            value.setModernThemeColor(modernThemeColor);
            return this;
        }

        public UserPreferencesDTOBuilder warmThemeColor(String warmThemeColor) {
            value.setWarmThemeColor(warmThemeColor);
            return this;
        }

        public UserPreferencesDTOBuilder naturalThemeColor(String naturalThemeColor) {
            value.setNaturalThemeColor(naturalThemeColor);
            return this;
        }

        public UserPreferencesDTOBuilder themeBackgrounds(
                Map<String, ThemeBackgroundDTO> themeBackgrounds) {
            value.setThemeBackgrounds(themeBackgrounds);
            return this;
        }

        public UserPreferencesDTOBuilder dockSize(Integer dockSize) {
            value.setDockSize(dockSize);
            return this;
        }

        public UserPreferencesDTOBuilder dockOpacity(Integer dockOpacity) {
            value.setDockOpacity(dockOpacity);
            return this;
        }

        public UserPreferencesDTOBuilder dockMagnification(Integer dockMagnification) {
            value.setDockMagnification(dockMagnification);
            return this;
        }

        public UserPreferencesDTOBuilder dockBlur(Integer dockBlur) {
            value.setDockBlur(dockBlur);
            return this;
        }

        public UserPreferencesDTOBuilder dockIconStyle(String dockIconStyle) {
            value.setDockIconStyle(dockIconStyle);
            return this;
        }

        public UserPreferencesDTOBuilder uiFontId(Long uiFontId) {
            value.setUiFontId(uiFontId);
            return this;
        }

        public UserPreferencesDTOBuilder readerFontId(Long readerFontId) {
            value.setReaderFontId(readerFontId);
            return this;
        }

        public UserPreferencesDTO build() {
            return value;
        }
    }

    public static class ThemeBackgroundDTO {
        private String mode;
        private String pageColor;
        private String secondaryColor;
        private String navColor;
        private Integer navOpacity;
        private String surfaceColor;
        private Integer surfaceOpacity;

        public ThemeBackgroundDTO() {
        }

        public ThemeBackgroundDTO(
                String mode,
                String pageColor,
                String secondaryColor,
                String navColor,
                Integer navOpacity,
                String surfaceColor,
                Integer surfaceOpacity) {
            this.mode = mode;
            this.pageColor = pageColor;
            this.secondaryColor = secondaryColor;
            this.navColor = navColor;
            this.navOpacity = navOpacity;
            this.surfaceColor = surfaceColor;
            this.surfaceOpacity = surfaceOpacity;
        }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getPageColor() { return pageColor; }
        public void setPageColor(String pageColor) { this.pageColor = pageColor; }
        public String getSecondaryColor() { return secondaryColor; }
        public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }
        public String getNavColor() { return navColor; }
        public void setNavColor(String navColor) { this.navColor = navColor; }
        public Integer getNavOpacity() { return navOpacity; }
        public void setNavOpacity(Integer navOpacity) { this.navOpacity = navOpacity; }
        public String getSurfaceColor() { return surfaceColor; }
        public void setSurfaceColor(String surfaceColor) { this.surfaceColor = surfaceColor; }
        public Integer getSurfaceOpacity() { return surfaceOpacity; }
        public void setSurfaceOpacity(Integer surfaceOpacity) { this.surfaceOpacity = surfaceOpacity; }
    }
}
