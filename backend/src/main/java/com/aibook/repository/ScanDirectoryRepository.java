package com.aibook.repository;

import com.aibook.model.entity.ScanDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 扫描目录 Repository
 */
@Repository
public interface ScanDirectoryRepository extends JpaRepository<ScanDirectory, Long> {

    /**
     * 查找所有启用的扫描目录
     */
    List<ScanDirectory> findByEnabledTrue();

    /**
     * 检查路径是否已存在
     */
    boolean existsByPath(String path);
}
