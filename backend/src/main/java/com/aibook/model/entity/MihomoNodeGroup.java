package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mihomo_node_groups")
@Getter
@Setter
@NoArgsConstructor
public class MihomoNodeGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long systemProxyId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 200)
    private String controlGroup;
    @Column(nullable = false, length = 500)
    private String proxyUrl;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String nodesJson;
}
