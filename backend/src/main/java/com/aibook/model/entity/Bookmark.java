package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 书签实体
 */
@Entity
@Table(name = "bookmarks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属用户
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 书籍
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /**
     * 书签标题
     */
    @Column(length = 500)
    private String title;

    /**
     * 章节名称
     */
    @Column(length = 500)
    private String chapter;

    /**
     * EPUB CFI 位置
     */
    @Column(columnDefinition = "TEXT")
    private String cfi;

    /**
     * 滚动位置（用于 TXT/MD）
     */
    private Long scrollPosition;

    /**
     * 页码
     */
    @Builder.Default
    private Integer page = 1;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
