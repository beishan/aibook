package com.aibook.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "system_proxy_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemProxyConfig {
    public enum ProxyType { SIMPLE, MIHOMO }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 1000) private String url;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProxyType proxyType = ProxyType.SIMPLE;
    @Column(length = 500)
    private String controllerUrl;
    @Column(length = 500)
    private String controllerSecret;
    @Builder.Default private Boolean enabled = true;
    @Builder.Default private Integer priority = 100;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
