package com.aibook.service.repair;

import com.aibook.dto.CreateTemplateRequest;
import com.aibook.dto.RepairTemplateDTO;
import com.aibook.model.entity.RepairMode;
import com.aibook.model.entity.TextRepairTemplate;
import com.aibook.repository.TextRepairTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 修复模板管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextRepairTemplateService {

    private final TextRepairTemplateRepository templateRepository;

    @Transactional
    public List<RepairTemplateDTO> getTemplates(Long userId) {
        return templateRepository.findByUserIdOrUserIdIsNullOrderByCreatedAtAsc(userId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public RepairTemplateDTO createTemplate(CreateTemplateRequest request, Long userId) {
        TextRepairTemplate template = TextRepairTemplate.builder()
                .name(request.getName())
                .description(request.getDescription())
                .repairMode(request.getRepairMode() != null
                        ? request.getRepairMode() : RepairMode.STANDARD)
                .enabledItemsJson(request.getEnabledItemsJson())
                .chapterFormat(request.getChapterFormat())
                .indentStyle(request.getIndentStyle())
                .blankLineCount(request.getBlankLineCount())
                .punctuationNormalize(request.getPunctuationNormalize())
                .traditionalSimplified(request.getTraditionalSimplified())
                .minChapterWords(request.getMinChapterWords())
                .maxChapterWords(request.getMaxChapterWords())
                .autoApplyThreshold(request.getAutoApplyThreshold())
                .systemTemplate(false)
                .userId(userId)
                .build();
        template = templateRepository.save(template);
        return toDTO(template);
    }

    @Transactional
    public RepairTemplateDTO updateTemplate(Long templateId, CreateTemplateRequest request, Long userId) {
        TextRepairTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        if (Boolean.TRUE.equals(template.getSystemTemplate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统模板不能修改");
        }
        if (template.getUserId() != null && !template.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权修改此模板");
        }
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        if (request.getRepairMode() != null) {
            template.setRepairMode(request.getRepairMode());
        }
        template.setEnabledItemsJson(request.getEnabledItemsJson());
        template.setChapterFormat(request.getChapterFormat());
        template.setIndentStyle(request.getIndentStyle());
        template.setBlankLineCount(request.getBlankLineCount());
        template.setPunctuationNormalize(request.getPunctuationNormalize());
        template.setTraditionalSimplified(request.getTraditionalSimplified());
        template.setMinChapterWords(request.getMinChapterWords());
        template.setMaxChapterWords(request.getMaxChapterWords());
        template.setAutoApplyThreshold(request.getAutoApplyThreshold());
        template = templateRepository.save(template);
        return toDTO(template);
    }

    @Transactional
    public void deleteTemplate(Long templateId, Long userId) {
        TextRepairTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        if (Boolean.TRUE.equals(template.getSystemTemplate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统模板不能删除");
        }
        if (template.getUserId() != null && !template.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权删除此模板");
        }
        templateRepository.delete(template);
    }

    /**
     * 初始化系统默认模板
     */
    @Transactional
    public void initSystemTemplates() {
        if (!templateRepository.findBySystemTemplateTrue().isEmpty()) {
            return; // 已经初始化
        }

        List<TextRepairTemplate> systemTemplates = List.of(
            TextRepairTemplate.builder()
                .name("安全修复").description("仅处理低风险问题：编码检测、明确乱码、换行符统一、不可见字符清理、明确网址广告、多余空行、高置信度章节识别")
                .repairMode(RepairMode.SAFE)
                .chapterFormat("第{number}章 {title}")
                .indentStyle("FULL_WIDTH_SPACE")
                .blankLineCount(1)
                .punctuationNormalize(false)
                .traditionalSimplified("NONE")
                .minChapterWords(100)
                .maxChapterWords(30000)
                .autoApplyThreshold(0.9)
                .systemTemplate(true)
                .build(),
            TextRepairTemplate.builder()
                .name("标准修复").description("安全修复 + 常见广告清理、章节标题统一、章节编号检查、重复章节检查、高置信度错误换行修复、段首缩进统一")
                .repairMode(RepairMode.STANDARD)
                .chapterFormat("第{number}章 {title}")
                .indentStyle("FULL_WIDTH_SPACE")
                .blankLineCount(1)
                .punctuationNormalize(false)
                .traditionalSimplified("NONE")
                .minChapterWords(100)
                .maxChapterWords(30000)
                .autoApplyThreshold(0.8)
                .systemTemplate(true)
                .build(),
            TextRepairTemplate.builder()
                .name("深度修复").description("标准修复 + 模糊广告识别、章节粘连检测、近似重复段落检测、全书段落重新分析")
                .repairMode(RepairMode.DEEP)
                .chapterFormat("第{number}章 {title}")
                .indentStyle("FULL_WIDTH_SPACE")
                .blankLineCount(1)
                .punctuationNormalize(true)
                .traditionalSimplified("NONE")
                .minChapterWords(100)
                .maxChapterWords(30000)
                .autoApplyThreshold(0.7)
                .systemTemplate(true)
                .build()
        );

        templateRepository.saveAll(systemTemplates);
        log.info("系统默认修复模板初始化完成");
    }

    private RepairTemplateDTO toDTO(TextRepairTemplate template) {
        return RepairTemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .repairMode(template.getRepairMode())
                .enabledItemsJson(template.getEnabledItemsJson())
                .chapterFormat(template.getChapterFormat())
                .indentStyle(template.getIndentStyle())
                .blankLineCount(template.getBlankLineCount())
                .punctuationNormalize(template.getPunctuationNormalize())
                .traditionalSimplified(template.getTraditionalSimplified())
                .minChapterWords(template.getMinChapterWords())
                .maxChapterWords(template.getMaxChapterWords())
                .autoApplyThreshold(template.getAutoApplyThreshold())
                .systemTemplate(template.getSystemTemplate())
                .userId(template.getUserId())
                .build();
    }
}
