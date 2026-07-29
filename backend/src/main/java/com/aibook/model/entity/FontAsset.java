package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 可供界面和阅读器使用的字体文件。
 */
@Entity
@Table(name = "font_assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FontAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String fontFamily;

    private Integer fontWeight;

    private String fontStyle;

    @Column(nullable = false, length = 16)
    private String format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SourceType sourceType;

    @Column(nullable = false, length = 2048)
    private String filePath;

    @Column(nullable = false, unique = true, length = 64)
    private String fileHash;

    @Column(nullable = false)
    private Long fileSize;

    private Long scanDirectoryId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean available = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum SourceType {
        SCANNED,
        UPLOADED
    }
}
