package com.aibook.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "crawler_proxy_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerProxyConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 100) private String name;
    @Column(length = 1000) private String url;
    private Long systemProxyId;
    @Builder.Default private Boolean enabled = true;
    @Builder.Default private Integer priority = 100;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
