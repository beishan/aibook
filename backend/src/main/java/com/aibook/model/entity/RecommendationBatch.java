package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Append-only batches also retain rerolls for recent-recommendation exclusion. */
@Entity
@Table(name = "recommendation_batches", indexes = @Index(columnList = "rule_id,recommendation_date,id"))
@Getter
@Setter
public class RecommendationBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RecommendationRule rule;
    @Column(name = "recommendation_date", nullable = false)
    private LocalDate recommendationDate;
    private LocalDateTime generatedAt;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String filterSnapshot;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String bookIdsJson;
    private int candidateCount;
    private int repeatedCount;
}
