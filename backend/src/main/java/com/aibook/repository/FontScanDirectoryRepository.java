package com.aibook.repository;

import com.aibook.model.entity.FontScanDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FontScanDirectoryRepository
        extends JpaRepository<FontScanDirectory, Long> {
    List<FontScanDirectory> findAllByOrderByPathAsc();
    List<FontScanDirectory> findByEnabledTrueOrderByPathAsc();
    Optional<FontScanDirectory> findByPath(String path);
}
