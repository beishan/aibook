package com.aibook.service.repair;

import com.aibook.dto.DetectedChapterDTO;
import com.aibook.model.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 章节标题规范化服务
 */
@Service
@Slf4j
public class ChapterNormalizeService {

    /** 默认格式 */
    public static final String DEFAULT_FORMAT = "第{number}章 {title}";

    /**
     * 规范化章节标题
     *
     * @param originalTitle 原始标题
     * @param number        章节编号
     * @param format        输出格式，如 "第{number}章 {title}"
     * @return 规范化后的标题
     */
    public String normalize(String originalTitle, int number, String format) {
        if (format == null || format.isEmpty()) {
            format = DEFAULT_FORMAT;
        }

        // 1. 清理原始标题
        String cleanedTitle = cleanTitle(originalTitle);

        // 2. 提取纯标题文本（去掉章节编号部分）
        String pureTitle = extractPureTitle(cleanedTitle);

        // 3. 应用格式模板
        return applyFormat(number, pureTitle, format);
    }

    /**
     * 批量规范化章节标题，生成修复问题
     */
    public List<TextRepairIssue> scanForIssues(
            List<DetectedChapterDTO> chapters, String format, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        for (int i = 0; i < chapters.size(); i++) {
            DetectedChapterDTO chapter = chapters.get(i);
            String originalTitle = chapter.getOriginalTitle();
            if (originalTitle == null) continue;

            String normalizedTitle = normalize(originalTitle,
                    chapter.getNumber() != null ? chapter.getNumber() : i + 1, format);

            if (!originalTitle.equals(normalizedTitle)) {
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(i)
                        .type(RepairIssueType.CHAPTER)
                        .startOffset(chapter.getStartOffset())
                        // DetectedChapterDTO.endOffset is the end of the whole chapter.
                        // A title normalization must only replace the title line.
                        .endOffset(chapter.getStartOffset() + originalTitle.length())
                        .originalText(originalTitle)
                        .suggestedText(normalizedTitle)
                        .reason("章节标题规范化")
                        .confidence(0.85)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.LOW)
                        .build());
            }
        }

        return issues;
    }

    // ==================== 标题清理 ====================

    /**
     * 清理标题中的多余字符
     */
    public String cleanTitle(String title) {
        if (title == null) return "";

        String result = title;

        // 删除"正文"前缀
        result = result.replaceFirst("^正文[\\s：:]*", "");

        // 删除首尾空格
        result = result.trim();

        // 合并连续空格
        result = result.replaceAll("[\\s　]+", " ");

        // 删除重复冒号（如 "：：" → "："）
        result = result.replaceAll("[：:]{2,}", "：");

        // 统一冒号为中文冒号
        result = result.replace(":", "：");

        // 删除编号前多余符号（如 "..第1章" → "第1章"）
        result = result.replaceFirst("^[\\.．、，,]+", "");

        // 删除标题首尾的空格和冒号
        result = result.replaceAll("^[\\s：:]+|[\\s：:]+$", "");

        return result;
    }

    /**
     * 从清理后的标题中提取纯标题文本（去掉章节编号部分）
     */
    public String extractPureTitle(String cleanedTitle) {
        if (cleanedTitle == null || cleanedTitle.isEmpty()) return "";

        String result = cleanedTitle;

        // 去掉 "第X章/回/节/卷/篇/部" 前缀
        result = result.replaceFirst(
            "^第[\\s]*[一二三四五六七八九十百千万零壹贰叁肆伍陆柒捌玖拾佰仟\\d]+[\\s]*[章节回卷篇部][\\s：:]*", "");

        // 去掉 "卷X" 前缀
        result = result.replaceFirst(
            "^卷[\\s]*[一二三四五六七八九十\\d]+[\\s：:]*", "");

        // 去掉 "Chapter N" 前缀
        result = result.replaceFirst(
            "^[Cc][Hh][Aa][Pp][Tt][Ee][Rr][\\s]+\\d+[\\s：:]*", "");

        // 去掉特殊章节前缀
        result = result.replaceFirst(
            "^(序章|序幕|序言|前言|楔子|引子|引言|后记|终章|尾声|番外|附录|大结局)[\\s：:]*", "");

        // 去掉纯数字编号前缀
        result = result.replaceFirst("^\\d{1,4}[\\.．、][\\s]*", "");

        // 去掉中文数字编号前缀
        result = result.replaceFirst("^[一二三四五六七八九十]+[、\\.．][\\s]*", "");

        // 去掉 Markdown 前缀
        result = result.replaceFirst("^#{1,3}\\s+", "");

        return result.trim();
    }

    // ==================== 格式应用 ====================

    /**
     * 应用格式模板
     *
     * 支持的占位符：
     * {number}        章节编号
     * {number:3}      三位补零编号
     * {chineseNumber} 中文章节编号
     * {title}         章节名称
     * {volume}        卷编号
     * {volumeTitle}   卷名称
     */
    public String applyFormat(int number, String title, String format) {
        String result = format;

        // {number:3} 三位补零
        result = result.replace("{number:3}", String.format("%03d", number));

        // {number} 编号
        result = result.replace("{number}", String.valueOf(number));

        // {chineseNumber} 中文编号
        result = result.replace("{chineseNumber}", ChapterNumberConverter.numberToChinese(number));

        // {title} 标题
        result = result.replace("{title}", title != null ? title : "");

        // {volume} 和 {volumeTitle} 暂不支持
        result = result.replace("{volume}", "");
        result = result.replace("{volumeTitle}", "");

        // 清理多余空格
        result = result.trim();

        return result;
    }
}
