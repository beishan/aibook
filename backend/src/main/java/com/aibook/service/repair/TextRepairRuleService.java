package com.aibook.service.repair;

import com.aibook.dto.CreateRuleRequest;
import com.aibook.dto.RepairRuleDTO;
import com.aibook.model.entity.TextRepairRule;
import com.aibook.repository.TextRepairRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 修复规则管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextRepairRuleService {

    private final TextRepairRuleRepository ruleRepository;

    @Transactional
    public List<RepairRuleDTO> getRules(Long userId) {
        return ruleRepository.findByUserIdOrUserIdIsNullAndEnabledTrue(userId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public RepairRuleDTO createRule(CreateRuleRequest request, Long userId) {
        TextRepairRule rule = TextRepairRule.builder()
                .name(request.getName())
                .type(request.getType())
                .pattern(request.getPattern())
                .matchScope(request.getMatchScope())
                .action(request.getAction())
                .replacement(request.getReplacement())
                .riskLevel(request.getRiskLevel())
                .enabled(request.getEnabled())
                .systemRule(false)
                .scope(request.getScope())
                .templateId(request.getTemplateId())
                .userId(userId)
                .build();
        rule = ruleRepository.save(rule);
        return toDTO(rule);
    }

    @Transactional
    public RepairRuleDTO updateRule(Long ruleId, CreateRuleRequest request, Long userId) {
        TextRepairRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在"));
        if (Boolean.TRUE.equals(rule.getSystemRule())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统规则不能修改");
        }
        if (rule.getUserId() != null && !rule.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权修改此规则");
        }
        rule.setName(request.getName());
        rule.setType(request.getType());
        rule.setPattern(request.getPattern());
        rule.setMatchScope(request.getMatchScope());
        rule.setAction(request.getAction());
        rule.setReplacement(request.getReplacement());
        rule.setRiskLevel(request.getRiskLevel());
        rule.setEnabled(request.getEnabled());
        rule.setScope(request.getScope());
        rule.setTemplateId(request.getTemplateId());
        rule = ruleRepository.save(rule);
        return toDTO(rule);
    }

    @Transactional
    public void deleteRule(Long ruleId, Long userId) {
        TextRepairRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在"));
        if (Boolean.TRUE.equals(rule.getSystemRule())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统规则不能删除");
        }
        if (rule.getUserId() != null && !rule.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权删除此规则");
        }
        ruleRepository.delete(rule);
    }

    /**
     * 初始化系统默认广告规则
     */
    @Transactional
    public void initSystemRules() {
        if (!ruleRepository.findByScopeAndEnabledTrue("ALL_BOOKS").isEmpty()) {
            return; // 已经初始化
        }

        List<TextRepairRule> systemRules = List.of(
            TextRepairRule.builder()
                .name("网址广告").type(com.aibook.model.entity.RepairIssueType.AD)
                .pattern("https?://[^\\s\u4e00-\u9fff]+|www\\.[a-zA-Z0-9][-a-zA-Z0-9.]+\\.[a-zA-Z]{2,}")
                .matchScope(com.aibook.model.entity.MatchScope.LINE)
                .action(com.aibook.model.entity.RepairAction.DELETE_LINE)
                .riskLevel(com.aibook.model.entity.RiskLevel.LOW)
                .enabled(true).systemRule(true).scope("ALL_BOOKS")
                .build(),
            TextRepairRule.builder()
                .name("QQ群广告").type(com.aibook.model.entity.RepairIssueType.AD)
                .pattern("QQ群[：:]?\\s*\\d{5,}|群号[：:]?\\s*\\d{5,}")
                .matchScope(com.aibook.model.entity.MatchScope.LINE)
                .action(com.aibook.model.entity.RepairAction.DELETE_LINE)
                .riskLevel(com.aibook.model.entity.RiskLevel.LOW)
                .enabled(true).systemRule(true).scope("ALL_BOOKS")
                .build(),
            TextRepairRule.builder()
                .name("微信号广告").type(com.aibook.model.entity.RepairIssueType.AD)
                .pattern("微信[号：:]?\\s*[a-zA-Z0-9_-]{6,}")
                .matchScope(com.aibook.model.entity.MatchScope.LINE)
                .action(com.aibook.model.entity.RepairAction.DELETE_LINE)
                .riskLevel(com.aibook.model.entity.RiskLevel.LOW)
                .enabled(true).systemRule(true).scope("ALL_BOOKS")
                .build(),
            TextRepairRule.builder()
                .name("本站最新网址").type(com.aibook.model.entity.RepairIssueType.AD)
                .pattern("请记住本站|本站最新网址|收藏本站")
                .matchScope(com.aibook.model.entity.MatchScope.LINE)
                .action(com.aibook.model.entity.RepairAction.DELETE_LINE)
                .riskLevel(com.aibook.model.entity.RiskLevel.LOW)
                .enabled(true).systemRule(true).scope("ALL_BOOKS")
                .build(),
            TextRepairRule.builder()
                .name("手机阅读提示").type(com.aibook.model.entity.RepairIssueType.AD)
                .pattern("手机用户请访问|手机阅读")
                .matchScope(com.aibook.model.entity.MatchScope.LINE)
                .action(com.aibook.model.entity.RepairAction.DELETE_LINE)
                .riskLevel(com.aibook.model.entity.RiskLevel.LOW)
                .enabled(true).systemRule(true).scope("ALL_BOOKS")
                .build(),
            TextRepairRule.builder()
                .name("网站整理声明").type(com.aibook.model.entity.RepairIssueType.AD)
                .pattern("本书由.*整理|本书由.*制作|.*网站整理制作")
                .matchScope(com.aibook.model.entity.MatchScope.LINE)
                .action(com.aibook.model.entity.RepairAction.DELETE_LINE)
                .riskLevel(com.aibook.model.entity.RiskLevel.LOW)
                .enabled(true).systemRule(true).scope("ALL_BOOKS")
                .build()
        );

        ruleRepository.saveAll(systemRules);
        log.info("系统默认广告规则初始化完成");
    }

    private RepairRuleDTO toDTO(TextRepairRule rule) {
        return RepairRuleDTO.builder()
                .id(rule.getId())
                .name(rule.getName())
                .type(rule.getType())
                .pattern(rule.getPattern())
                .matchScope(rule.getMatchScope())
                .action(rule.getAction())
                .replacement(rule.getReplacement())
                .riskLevel(rule.getRiskLevel())
                .enabled(rule.getEnabled())
                .systemRule(rule.getSystemRule())
                .scope(rule.getScope())
                .templateId(rule.getTemplateId())
                .userId(rule.getUserId())
                .build();
    }
}
