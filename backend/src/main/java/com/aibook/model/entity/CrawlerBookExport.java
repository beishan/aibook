package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_book_exports", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_export_book_format", columnNames = {"crawler_book_id", "format"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CrawlerBookExport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crawler_book_id", nullable = false) private CrawlerBook crawlerBook;
    @Column(nullable = false, length = 12) private String format;
    @Column(nullable = false, length = 1500) private String filePath;
    private Long fileSize;
    private String fileHash;
    @CreationTimestamp private LocalDateTime createdAt;
}
