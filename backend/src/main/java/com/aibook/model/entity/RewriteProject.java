package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "rewrite_projects", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rewrite_project_version", columnNames = "rewrite_version_id"),
        @UniqueConstraint(name = "uk_rewrite_project_token", columnNames = "creation_token")
})
@Getter
@Setter
public class RewriteProject {

    public enum Status { ACTIVE, PAUSED, COMPLETED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_version_id", nullable = false)
    private BookVersion sourceVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rewrite_version_id", nullable = false)
    private BookVersion rewriteVersion;

    @Column(name = "creation_token", nullable = false, length = 64)
    private String creationToken;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    private Long currentChapterId;
    @Column(name = "current_chapter_position")
    private Integer currentChapterPosition = 0;
    private Integer incompleteCount;
    private LocalDateTime completedAt;

    @Version
    private Long rowVersion;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
