package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "backup_tasks")
@Getter
@Setter
public class BackupTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean databaseEnabled;

    @Column(nullable = false)
    private boolean booksEnabled;

    @Column(nullable = false)
    private boolean uploadsEnabled;

    @Column(nullable = false)
    private boolean crawlerDataEnabled;

    @Column(nullable = false)
    private boolean scheduleEnabled;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 100)
    private String cronExpression;

    private LocalDateTime nextRunAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
