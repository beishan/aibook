package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 同一本书的一个可阅读文件版本。
 */
@Entity
@Table(name = "book_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String format;

    @Column(nullable = false)
    private String filePath;

    private Long fileSize;

    @Column(unique = true)
    private String fileHash;

    @Column(nullable = false)
    @Builder.Default
    private Boolean primaryVersion = false;

    @Column(columnDefinition = "TEXT")
    private String chapterInfo;

    private Integer chapterCount;

    /** 可追溯的版本来源；爬虫导入时指向采集中心记录。 */
    private String sourceType;
    private String sourceId;
    private String sourceSite;
    @Column(length = 1500)
    private String sourceUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
