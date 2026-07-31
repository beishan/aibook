package com.aibook.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

/**
 * 用户界面偏好。字体字段记录是否出现在请求 JSON 中，以支持 partial PUT
 * 和显式传 null 清空之间的区别。
 */
public class UserPreferencesDTO {

    private String theme;
    private String libraryViewMode;
    private Integer libraryPageSize;
    private Integer scanThreadCount;
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

    public Integer getScanThreadCount() {
        return scanThreadCount;
    }

    public void setScanThreadCount(Integer scanThreadCount) {
        this.scanThreadCount = scanThreadCount;
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

        public UserPreferencesDTOBuilder scanThreadCount(Integer scanThreadCount) {
            value.setScanThreadCount(scanThreadCount);
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
}
