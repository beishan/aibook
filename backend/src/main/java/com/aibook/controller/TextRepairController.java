package com.aibook.controller;

import com.aibook.dto.*;
import com.aibook.model.entity.RepairIssueStatus;
import com.aibook.model.entity.RepairIssueType;
import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import com.aibook.service.repair.TextRepairRuleService;
import com.aibook.service.repair.TextRepairService;
import com.aibook.service.repair.TextRepairTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * TXT 内容修复控制器
 */
@RestController
@RequestMapping("/api/text-repair")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class TextRepairController {

    private final TextRepairService repairService;
    private final TextRepairRuleService ruleService;
    private final TextRepairTemplateService templateService;
    private final UserService userService;

    // ==================== 修复任务 ====================

    /**
     * 创建修复任务并扫描内容
     */
    @PostMapping("/tasks")
    public ResponseEntity<RepairTaskDTO> createTask(
            Authentication authentication,
            @Valid @RequestBody CreateRepairTaskRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(repairService.createTask(request, user.getId()));
    }

    /**
     * 获取修复任务列表
     */
    @GetMapping("/tasks")
    public ResponseEntity<Page<RepairTaskDTO>> getTasks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = userService.findByUsername(authentication.getName());
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(repairService.getTasks(user.getId(), pageRequest));
    }

    /**
     * 获取修复任务详情
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<RepairTaskDTO> getTask(
            Authentication authentication,
            @PathVariable Long taskId) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(repairService.getTask(taskId, user.getId()));
    }

    /**
     * 删除修复任务
     */
    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(
            Authentication authentication,
            @PathVariable Long taskId) {
        User user = userService.findByUsername(authentication.getName());
        repairService.deleteTask(taskId, user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取书籍的修复任务列表
     */
    @GetMapping("/books/{bookId}/tasks")
    public ResponseEntity<List<RepairTaskDTO>> getTasksByBookId(
            Authentication authentication,
            @PathVariable Long bookId) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(repairService.getTasksByBookId(bookId, user.getId()));
    }

    // ==================== 修复问题 ====================

    /**
     * 获取修复问题列表（分页，可筛选类型和状态）
     */
    @GetMapping("/tasks/{taskId}/issues")
    public ResponseEntity<Page<RepairIssueDTO>> getIssues(
            Authentication authentication,
            @PathVariable Long taskId,
            @RequestParam(required = false) RepairIssueType type,
            @RequestParam(required = false) RepairIssueStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        userService.findByUsername(authentication.getName());
        PageRequest pageRequest = PageRequest.of(page, size);
        return ResponseEntity.ok(repairService.getIssues(taskId, type, status, pageRequest));
    }

    /**
     * 更新单个修复问题状态
     */
    @PutMapping("/issues/{issueId}")
    public ResponseEntity<RepairIssueDTO> updateIssue(
            Authentication authentication,
            @PathVariable Long issueId,
            @Valid @RequestBody UpdateIssueRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(repairService.updateIssue(issueId, request, user.getId()));
    }

    /**
     * 批量更新修复问题状态
     */
    @PostMapping("/tasks/{taskId}/issues/batch")
    public ResponseEntity<Void> batchUpdateIssues(
            Authentication authentication,
            @PathVariable Long taskId,
            @Valid @RequestBody BatchUpdateIssuesRequest request) {
        User user = userService.findByUsername(authentication.getName());
        repairService.batchUpdateIssues(request, user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量接受高置信度问题
     */
    @PostMapping("/tasks/{taskId}/accept-high")
    public ResponseEntity<Map<String, Integer>> acceptHighConfidence(
            Authentication authentication,
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0.8") double threshold) {
        User user = userService.findByUsername(authentication.getName());
        int count = repairService.acceptHighConfidenceIssues(taskId, threshold);
        return ResponseEntity.ok(Map.of("acceptedCount", count));
    }

    /**
     * 撤销全部修改
     */
    @PostMapping("/tasks/{taskId}/revert")
    public ResponseEntity<Void> revertAll(
            Authentication authentication,
            @PathVariable Long taskId) {
        User user = userService.findByUsername(authentication.getName());
        repairService.revertAllIssues(taskId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // ==================== 编码检测 ====================

    /**
     * 检测书籍编码
     */
    @GetMapping("/books/{bookId}/encoding")
    public ResponseEntity<EncodingDetectResult> detectEncoding(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestParam(required = false) Long versionId) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(
                repairService.detectEncoding(bookId, versionId, user.getId()));
    }

    /**
     * 切换编码预览
     */
    @PostMapping("/books/{bookId}/encoding/preview")
    public ResponseEntity<Map<String, String>> switchEncodingPreview(
            Authentication authentication,
            @PathVariable Long bookId,
            @RequestBody SwitchEncodingRequest request) {
        User user = userService.findByUsername(authentication.getName());
        String preview = repairService.switchEncodingPreview(
                bookId, request.getVersionId(), request.getEncoding(), user.getId());
        return ResponseEntity.ok(Map.of("preview", preview));
    }

    // ==================== 修复预览与应用 ====================

    /**
     * 生成修复预览
     */
    @GetMapping("/tasks/{taskId}/preview")
    public ResponseEntity<RepairPreviewResponse> previewRepair(
            Authentication authentication,
            @PathVariable Long taskId) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(repairService.previewRepair(taskId, user.getId()));
    }

    /**
     * 执行修复，保存为新版本
     */
    @PostMapping("/apply")
    public ResponseEntity<BookVersionDTO> applyRepair(
            Authentication authentication,
            @Valid @RequestBody ApplyRepairRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(repairService.applyRepair(request, user.getId()));
    }

    // ==================== 广告规则管理 ====================

    /**
     * 获取广告规则列表
     */
    @GetMapping("/rules")
    public ResponseEntity<List<RepairRuleDTO>> getRules(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(ruleService.getRules(user.getId()));
    }

    /**
     * 创建广告规则
     */
    @PostMapping("/rules")
    public ResponseEntity<RepairRuleDTO> createRule(
            Authentication authentication,
            @Valid @RequestBody CreateRuleRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(ruleService.createRule(request, user.getId()));
    }

    /**
     * 更新广告规则
     */
    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<RepairRuleDTO> updateRule(
            Authentication authentication,
            @PathVariable Long ruleId,
            @Valid @RequestBody CreateRuleRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(ruleService.updateRule(ruleId, request, user.getId()));
    }

    /**
     * 删除广告规则
     */
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(
            Authentication authentication,
            @PathVariable Long ruleId) {
        User user = userService.findByUsername(authentication.getName());
        ruleService.deleteRule(ruleId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // ==================== 修复模板管理 ====================

    /**
     * 获取修复模板列表
     */
    @GetMapping("/templates")
    public ResponseEntity<List<RepairTemplateDTO>> getTemplates(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(templateService.getTemplates(user.getId()));
    }

    /**
     * 创建修复模板
     */
    @PostMapping("/templates")
    public ResponseEntity<RepairTemplateDTO> createTemplate(
            Authentication authentication,
            @Valid @RequestBody CreateTemplateRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(templateService.createTemplate(request, user.getId()));
    }

    /**
     * 更新修复模板
     */
    @PutMapping("/templates/{templateId}")
    public ResponseEntity<RepairTemplateDTO> updateTemplate(
            Authentication authentication,
            @PathVariable Long templateId,
            @Valid @RequestBody CreateTemplateRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(templateService.updateTemplate(templateId, request, user.getId()));
    }

    /**
     * 删除修复模板
     */
    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<Void> deleteTemplate(
            Authentication authentication,
            @PathVariable Long templateId) {
        User user = userService.findByUsername(authentication.getName());
        templateService.deleteTemplate(templateId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
