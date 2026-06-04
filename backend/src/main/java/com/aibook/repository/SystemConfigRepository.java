package com.aibook.repository;

import com.aibook.model.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 系统配置仓库
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {

    /**
     * 根据配置键前缀查找配置
     */
    List<SystemConfig> findByConfigKeyStartingWith(String prefix);
}
