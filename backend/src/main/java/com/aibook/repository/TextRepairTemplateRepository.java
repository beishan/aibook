package com.aibook.repository;

import com.aibook.model.entity.TextRepairTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextRepairTemplateRepository extends JpaRepository<TextRepairTemplate, Long> {

    List<TextRepairTemplate> findByUserIdOrUserIdIsNullOrderByCreatedAtAsc(Long userId);

    List<TextRepairTemplate> findBySystemTemplateTrue();
}
