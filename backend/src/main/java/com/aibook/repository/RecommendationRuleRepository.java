package com.aibook.repository;

import com.aibook.model.entity.RecommendationRule;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRuleRepository extends JpaRepository<RecommendationRule, Long> {
    @Query("select r.id from RecommendationRule r where r.user.id = :userId order by r.id")
    List<Long> ownedIds(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecommendationRule r where r.id = :id and r.user.id = :userId")
    Optional<RecommendationRule> lockOwned(@Param("id") Long id, @Param("userId") Long userId);
}
