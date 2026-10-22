package com.aibook.repository;

import com.aibook.model.entity.RecommendationBatch;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationBatchRepository extends JpaRepository<RecommendationBatch, Long> {
    Optional<RecommendationBatch> findFirstByRuleIdAndRecommendationDateOrderByIdDesc(Long ruleId, LocalDate date);
    List<RecommendationBatch> findByRuleIdAndRecommendationDateGreaterThanEqual(Long ruleId, LocalDate since);
}
