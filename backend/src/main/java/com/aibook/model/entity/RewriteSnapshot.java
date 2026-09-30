package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "rewrite_snapshots", uniqueConstraints =
        @UniqueConstraint(name = "uk_rewrite_snapshot_number",
                columnNames = {"project_id", "snapshot_number"}))
@Getter
@Setter
public class RewriteSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private RewriteProject project;

    @Column(name = "snapshot_number", nullable = false)
    private Long snapshotNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "snapshot_content", columnDefinition = "TEXT", nullable = false)
    private String snapshotContent;

    @Column(nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean automatic;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
