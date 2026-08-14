package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 独立于书库入库流程的格式转换任务。 */
@Entity
@Table(name = "book_conversion_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookConversionTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Long sourceBookId;
    private Long sourceVersionId;
    @Column(nullable = false) private String sourceFilename;
    @Column(nullable = false) private String sourceFormat;
    @Column(nullable = false) private String targetFormat;
    @Column(nullable = false) private String sourcePath;
    @Column(nullable = false) private Boolean uploadedSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    private String stage;
    private Integer progress;
    @Column(columnDefinition = "TEXT") private String errorMessage;

    @Column(nullable = false) private String title;
    private String author;
    private String isbn;
    private String publisher;
    private String publishDate;
    private String language;
    private String categoryName;
    private String seriesName;
    private String seriesIndex;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String tagsJson;
    private String coverPath;

    private String encoding;
    private String newlineFormat;
    private Long characterCount;
    private Integer anomalyCount;
    @Column(columnDefinition = "TEXT") private String chaptersJson;
    @Column(columnDefinition = "TEXT") private String settingsJson;

    private String outputFilename;
    private String outputPath;
    private Long outputSize;
    private Long elapsedMillis;
    private LocalDateTime expiresAt;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;

    public enum Status { CREATED, ANALYZING, READY, CONVERTING, SUCCESS, FAILED, CANCELLED }
}
