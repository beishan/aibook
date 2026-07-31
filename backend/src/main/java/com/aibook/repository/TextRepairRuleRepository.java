package com.aibook.repository;

import com.aibook.model.entity.TextRepairRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextRepairRuleRepository extends JpaRepository<TextRepairRule, Long> {

    List<TextRepairRule> findByEnabledTrueOrderByRiskLevelAsc();

    List<TextRepairRule> findByUserIdAndEnabledTrue(Long userId);

    List<TextRepairRule> findByUserIdOrUserIdIsNullAndEnabledTrue(Long userId);

    List<TextRepairRule> findByScopeAndEnabledTrue(String scope);
}
