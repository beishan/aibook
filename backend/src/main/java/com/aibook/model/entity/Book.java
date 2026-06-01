package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 书籍实体
 */
@Entity
@Table(name = "books")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 书名
     */
    @Column(nullable = false)
    private String title;

    /**
     * 作者
     */
    private String author;

    /**
     * ISBN
     */
    private String isbn;

    /**
     * 出版社
     */
    private String publisher;

    /**
     * 出版日期
     */
    private String publishDate;

    /**
     * 简介
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 封面图URL
     */
    private String coverUrl;

    /**
     * 文件格式 (epub, txt, pdf, mobi, azw3, docx, html, md)
     */
    @Column(nullable = false)
    private String format;

    /**
     * 文件路径
     */
    @Column(nullable = false)
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件哈希（MD5/SHA256）
     */
    @Column(unique = true)
    private String fileHash;

    /**
     * 语言
     */
    private String language;

    /**
     * 评分 (1-5)
     */
    private Integer rating;

    /**
     * 阅读状态 (UNREADING, READING, FINISHED)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReadingStatus readingStatus = ReadingStatus.UNREADING;

    /**
     * 分类
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * 标签
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "book_tags",
        joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    /**
     * 所属用户
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 是否收藏
     */
    @Builder.Default
    private Boolean isFavorite = false;

    /**
     * 是否想读
     */
    @Builder.Default
    private Boolean isWanted = false;

    /**
     * 笔记
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum ReadingStatus {
        UNREADING,    // 未读
        READING,     // 正在阅读
        FINISHED     // 已读完
    }
}
