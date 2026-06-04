package com.aibook.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 扫描目录实体
 */
@Entity
@Table(name = "scan_directories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanDirectory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 目录路径
     */
    @Column(nullable = false)
    private String path;

    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 上次扫描时间
     */
    private LocalDateTime lastScanTime;

    /**
     * 扫描到的书籍数量
     */
    @Builder.Default
    private Integer bookCount = 0;

    /**
     * 所属用户
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
