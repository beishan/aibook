package com.aibook.repository;

import com.aibook.model.entity.SystemProxyConfig;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemProxyConfigRepository extends JpaRepository<SystemProxyConfig, Long> {
    List<SystemProxyConfig> findAllByOrderByPriorityAscIdAsc();
}
