package com.aibook.dto;

import com.aibook.model.entity.FontAsset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FontAssetDTO {
    private Long id;
    private String displayName;
    private String fontFamily;
    private Integer fontWeight;
    private String fontStyle;
    private String format;
    private FontAsset.SourceType sourceType;
    private String filePath;
    private Long fileSize;
    private Boolean enabled;
    private Boolean available;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
