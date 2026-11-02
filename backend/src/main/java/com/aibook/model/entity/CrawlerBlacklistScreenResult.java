package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "crawler_blacklist_screen_results",
        uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "book_id"}),
        indexes = @Index(name = "idx_blacklist_screen_result_job", columnList = "job_id,id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerBlacklistScreenResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CrawlerBlacklistScreenJob job;
    @Column(name = "book_id", nullable = false)
    private Long bookId;
    @Column(length = 500)
    private String bookName;
    @Column(columnDefinition = "TEXT")
    private String author;
    private String siteName;
    @Column(columnDefinition = "TEXT")
    private String reason;
}
