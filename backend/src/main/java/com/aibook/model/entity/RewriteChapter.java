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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "rewrite_chapters")
@Getter
@Setter
public class RewriteChapter {

    public enum Status { NOT_STARTED, WRITING, REVIEW, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private RewriteProject project;

    @Column(nullable = false)
    private Integer sortIndex;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 200)
    private String volumeTitle;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "source_key", length = 500)
    private String sourceKey;

    @Column(name = "source_title", length = 500)
    private String sourceTitle;

    @Column(name = "source_content", columnDefinition = "TEXT")
    private String sourceContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NOT_STARTED;

    @Column(nullable = false)
    private Long revision = 0L;

    @Version
    private Long rowVersion;

    @Column(nullable = false)
    private Integer wordCount = 0;

    @Column(nullable = false)
    private Boolean deleted = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
