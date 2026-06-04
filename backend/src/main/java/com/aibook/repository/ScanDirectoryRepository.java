package com.aibook.repository;

import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 扫描目录 Repository
 */
@Repository
public interface ScanDirectoryRepository extends JpaRepository<ScanDirectory, Long> {

    List<ScanDirectory> findByUser(User user);
    /**
     * 查找所有启用的扫描目录
     */
    List<ScanDirectory> findByEnabledTrue();

    Optional<ScanDirectory> findByUserAndPath(User user, String path);

    long countByUser(User user);
    /**
     * 检查路径是否已存在
     */
    boolean existsByPath(String path);
}
