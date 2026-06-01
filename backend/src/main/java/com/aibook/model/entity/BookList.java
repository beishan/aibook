package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 书单实体
 */
@Entity
@Table(name = "book_lists")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 书单名称
     */
    @Column(nullable = false)
    private String name;

    /**
     * 书单描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 所属用户
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 书单中的书籍
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "book_list_items",
        joinColumns = @JoinColumn(name = "book_list_id"),
        inverseJoinColumns = @JoinColumn(name = "book_id")
    )
    @Builder.Default
    private List<Book> books = new ArrayList<>();

    /**
     * 排序顺序
     */
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
