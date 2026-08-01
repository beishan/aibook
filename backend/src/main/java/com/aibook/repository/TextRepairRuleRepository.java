package com.aibook.repository;

import com.aibook.model.entity.TextRepairRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextRepairRuleRepository extends JpaRepository<TextRepairRule, Long> {

    List<TextRepairRule> findByEnabledTrueOrderByRiskLevelAsc();

    List<TextRepairRule> findByUserIdAndEnabledTrue(Long userId);

    @Query("select r from TextRepairRule r where (r.userId = :userId or r.userId is null) "
            + "order by r.riskLevel asc, r.id asc")
    List<TextRepairRule> findVisibleRules(@Param("userId") Long userId);

    @Query("select r from TextRepairRule r where (r.userId = :userId or r.userId is null) "
            + "and r.enabled = true order by r.riskLevel asc, r.id asc")
    List<TextRepairRule> findEnabledRules(@Param("userId") Long userId);

    List<TextRepairRule> findByScopeAndEnabledTrue(String scope);
}
