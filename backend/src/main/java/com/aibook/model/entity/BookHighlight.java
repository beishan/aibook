package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 书籍高亮/批注实体
 */
@Entity
@Table(name = "book_highlights",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_id", "book_id", "cfi_range"}
    ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookHighlight {

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
     * EPUB CFI 或位置标识符
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String cfiRange;

    /**
     * 高亮文本内容
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    /**
     * 高亮颜色
     */
    @Builder.Default
    private String color = "#ffff00";

    /**
     * 章节标题/位置
     */
    @Column(length = 500)
    private String chapter;

    /**
     * 用户批注
     */
    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
