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
     * 书籍首次入库方式。历史数据由启动后的独立迁移回填；新书必须在入库时明确设置。
     *
     * <p>升级时保持数据库列可空，确保 PostgreSQL 的既有 books 表能够先完成 DDL 更新，
     * 再由回填任务补齐历史记录。</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private SourceType sourceType;

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

    /** 是否已加入用户的“书架”页。 */
    @Builder.Default
    private Boolean onShelf = false;

    /** 加入书架时间，用于默认倒序展示。 */
    private LocalDateTime shelfAddedAt;

    /** 书籍在所属书架分组内的手动顺序，数值越小越靠前。 */
    private Integer shelfSortOrder;

    /** 书架分组；为空表示未分组。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shelf_group_id")
    private ShelfGroup shelfGroup;

    /**
     * 笔记
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * 章节信息 (JSON格式，仅TXT/MD)
     * 格式: [{"title":"...","startIndex":0,"endIndex":1234},...]
     */
    @Column(columnDefinition = "TEXT")
    private String chapterInfo;

    /**
     * 解析得到的章节数量。
     */
    private Integer chapterCount;

    /**
     * 移入系统回收站的时间；为空表示书籍正常可用。
     * 回收站只管理数据库记录，绝不删除 filePath 指向的原始文件。
     */
    private LocalDateTime deletedAt;

    /**
     * 从回收站永久移除的时间。保留最小书籍记录和文件哈希作为墓碑，
     * 避免目录扫描再次导入；原始文件仍不会被删除。
     */
    private LocalDateTime purgedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum ReadingStatus {
        UNREADING,    // 未读
        READING,     // 正在阅读
        FINISHED     // 已读完
    }

    public enum SourceType {
        UPLOAD,
        DIRECTORY_SCAN
    }
}
