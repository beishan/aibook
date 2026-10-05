package com.aibook.repository;

import com.aibook.model.entity.MihomoNodeGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MihomoNodeGroupRepository extends JpaRepository<MihomoNodeGroup, Long> {
    List<MihomoNodeGroup> findBySystemProxyIdAndUserIdOrderByIdAsc(Long proxyId, Long userId);
}
