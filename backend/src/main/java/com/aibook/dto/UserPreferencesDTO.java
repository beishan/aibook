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
    private String macos26ThemeColor;
    private Map<String, ThemeBackgroundDTO> themeBackgrounds;
    private String libraryViewMode;
    private Integer libraryPageSize;
    private Integer libraryCardPageSize;
    private Integer libraryListPageSize;
    private Integer authorPageSize;
    private Integer scanThreadCount;
    private Integer crawlerPollingIntervalSeconds;
    private Boolean crawlerFollowCurrentChapter;
    private Integer crawlerChapterPageSize;
    private String crawlerDiscoveryViewMode;
    private String crawlerBookViewMode;
    private Integer dockSize;
    private Integer dockOpacity;
    private Integer dockMagnification;
    private Integer dockBlur;
    private String dockIconStyle;
    private Long uiFontId;
    private Long readerFontId;
    private ReaderSettingsDTO readerSettings;

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

    public String getMacos26ThemeColor() {
        return macos26ThemeColor;
    }

    public void setMacos26ThemeColor(String macos26ThemeColor) {
        this.macos26ThemeColor = macos26ThemeColor;
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

    public Integer getAuthorPageSize() {
        return authorPageSize;
    }

    public void setAuthorPageSize(Integer authorPageSize) {
        this.authorPageSize = authorPageSize;
    }

    public Integer getScanThreadCount() {
        return scanThreadCount;
    }

    public void setScanThreadCount(Integer scanThreadCount) {
        this.scanThreadCount = scanThreadCount;
    }

    public Integer getCrawlerPollingIntervalSeconds() {
        return crawlerPollingIntervalSeconds;
    }

    public void setCrawlerPollingIntervalSeconds(Integer crawlerPollingIntervalSeconds) {
        this.crawlerPollingIntervalSeconds = crawlerPollingIntervalSeconds;
    }

    public Boolean getCrawlerFollowCurrentChapter() {
        return crawlerFollowCurrentChapter;
    }

    public void setCrawlerFollowCurrentChapter(Boolean crawlerFollowCurrentChapter) {
        this.crawlerFollowCurrentChapter = crawlerFollowCurrentChapter;
    }

    public Integer getCrawlerChapterPageSize() {
        return crawlerChapterPageSize;
    }

    public void setCrawlerChapterPageSize(Integer crawlerChapterPageSize) {
        this.crawlerChapterPageSize = crawlerChapterPageSize;
    }

    public String getCrawlerDiscoveryViewMode() {
        return crawlerDiscoveryViewMode;
    }

    public void setCrawlerDiscoveryViewMode(String crawlerDiscoveryViewMode) {
        this.crawlerDiscoveryViewMode = crawlerDiscoveryViewMode;
    }

    public String getCrawlerBookViewMode() {
        return crawlerBookViewMode;
    }

    public void setCrawlerBookViewMode(String crawlerBookViewMode) {
        this.crawlerBookViewMode = crawlerBookViewMode;
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

    public ReaderSettingsDTO getReaderSettings() {
        return readerSettings;
    }

    public void setReaderSettings(ReaderSettingsDTO readerSettings) {
        this.readerSettings = readerSettings;
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

        public UserPreferencesDTOBuilder authorPageSize(Integer authorPageSize) {
            value.setAuthorPageSize(authorPageSize);
            return this;
        }

        public UserPreferencesDTOBuilder scanThreadCount(Integer scanThreadCount) {
            value.setScanThreadCount(scanThreadCount);
            return this;
        }

        public UserPreferencesDTOBuilder crawlerPollingIntervalSeconds(Integer value) {
            this.value.setCrawlerPollingIntervalSeconds(value);
            return this;
        }

        public UserPreferencesDTOBuilder crawlerFollowCurrentChapter(Boolean value) {
            this.value.setCrawlerFollowCurrentChapter(value);
            return this;
        }

        public UserPreferencesDTOBuilder crawlerChapterPageSize(Integer value) {
            this.value.setCrawlerChapterPageSize(value);
            return this;
        }

        public UserPreferencesDTOBuilder crawlerDiscoveryViewMode(String value) {
            this.value.setCrawlerDiscoveryViewMode(value);
            return this;
        }

        public UserPreferencesDTOBuilder crawlerBookViewMode(String value) {
            this.value.setCrawlerBookViewMode(value);
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

        public UserPreferencesDTOBuilder macos26ThemeColor(String macos26ThemeColor) {
            value.setMacos26ThemeColor(macos26ThemeColor);
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

        public UserPreferencesDTOBuilder readerSettings(ReaderSettingsDTO readerSettings) {
            value.setReaderSettings(readerSettings);
            return this;
        }

        public UserPreferencesDTO build() {
            return value;
        }
    }

    public static class ReaderSettingsDTO {
        private String appearance;
        private String epubEngine;
        private String fontFamily;
        private Integer fontSize;
        private Double lineHeight;
        private Integer paragraphSpacing;
        private String contentWidth;
        private String backgroundColor;
        private String backgroundImageId;
        private Boolean paginationMode;
        private String screenMode;
        private Boolean textIndent;
        private Boolean showProgress;

        public ReaderSettingsDTO() {
        }

        public ReaderSettingsDTO(
                String appearance,
                String epubEngine,
                String fontFamily,
                Integer fontSize,
                Double lineHeight,
                Integer paragraphSpacing,
                String contentWidth,
                String backgroundColor,
                String backgroundImageId,
                Boolean paginationMode,
                String screenMode,
                Boolean textIndent,
                Boolean showProgress) {
            this.appearance = appearance;
            this.epubEngine = epubEngine;
            this.fontFamily = fontFamily;
            this.fontSize = fontSize;
            this.lineHeight = lineHeight;
            this.paragraphSpacing = paragraphSpacing;
            this.contentWidth = contentWidth;
            this.backgroundColor = backgroundColor;
            this.backgroundImageId = backgroundImageId;
            this.paginationMode = paginationMode;
            this.screenMode = screenMode;
            this.textIndent = textIndent;
            this.showProgress = showProgress;
        }

        public String getAppearance() { return appearance; }
        public void setAppearance(String value) { appearance = value; }
        public String getEpubEngine() { return epubEngine; }
        public void setEpubEngine(String value) { epubEngine = value; }
        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String value) { fontFamily = value; }
        public Integer getFontSize() { return fontSize; }
        public void setFontSize(Integer value) { fontSize = value; }
        public Double getLineHeight() { return lineHeight; }
        public void setLineHeight(Double value) { lineHeight = value; }
        public Integer getParagraphSpacing() { return paragraphSpacing; }
        public void setParagraphSpacing(Integer value) { paragraphSpacing = value; }
        public String getContentWidth() { return contentWidth; }
        public void setContentWidth(String value) { contentWidth = value; }
        public String getBackgroundColor() { return backgroundColor; }
        public void setBackgroundColor(String value) { backgroundColor = value; }
        public String getBackgroundImageId() { return backgroundImageId; }
        public void setBackgroundImageId(String value) { backgroundImageId = value; }
        public Boolean getPaginationMode() { return paginationMode; }
        public void setPaginationMode(Boolean value) { paginationMode = value; }
        public String getScreenMode() { return screenMode; }
        public void setScreenMode(String value) { screenMode = value; }
        public Boolean getTextIndent() { return textIndent; }
        public void setTextIndent(Boolean value) { textIndent = value; }
        public Boolean getShowProgress() { return showProgress; }
        public void setShowProgress(Boolean value) { showProgress = value; }
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
