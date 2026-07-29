package com.aibook.repository;

import com.aibook.model.entity.FontAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FontAssetRepository extends JpaRepository<FontAsset, Long> {
    List<FontAsset> findAllByOrderByDisplayNameAsc();
    Optional<FontAsset> findByFileHash(String fileHash);
    Optional<FontAsset> findBySourceTypeAndFilePath(
            FontAsset.SourceType sourceType, String filePath);
    List<FontAsset> findByScanDirectoryId(Long scanDirectoryId);
    Optional<FontAsset> findByIdAndEnabledTrue(Long id);
    void deleteByScanDirectoryId(Long scanDirectoryId);
}
