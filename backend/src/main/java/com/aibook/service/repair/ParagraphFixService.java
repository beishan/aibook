package com.aibook.service.repair;

import com.aibook.model.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 段落格式修复服务
 */
@Service
@Slf4j
public class ParagraphFixService {

    /** 句末标点 */
    private static final String SENTENCE_ENDINGS = "。！？!?…";

    /** 对话引导符号 */
    private static final String DIALOGUE_STARTS = "'\u201c\u201d\u2018\u2019";

    /** 列表项前缀 */
    private static final String LIST_PREFIXES = "-*•·①②③④⑤⑥⑦⑧⑨⑩";

    /**
     * 扫描段落格式问题
     *
     * @param text     全文
     * @param lines    行数组
     * @param taskId   任务 ID
     * @return 修复问题列表
     */
    public List<TextRepairIssue> scanForIssues(
            String text, String[] lines, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        // 1. 换行符不统一
        issues.addAll(detectLineEndingIssues(text, taskId));

        // 2. 多余空行
        issues.addAll(detectExcessiveBlankLines(lines, taskId));

        // 3. 错误换行（一行被拆成多行）
        issues.addAll(detectBrokenLines(lines, taskId));

        // 4. 段首缩进不统一
        issues.addAll(detectIndentIssues(lines, taskId));

        return issues;
    }

    // ==================== 换行符检测 ====================

    private List<TextRepairIssue> detectLineEndingIssues(String text, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        boolean hasCRLF = text.contains("\r\n");
        boolean hasCR = text.contains("\r") && !hasCRLF;

        if (hasCRLF) {
            int count = countOccurrences(text, "\r\n");
            issues.add(TextRepairIssue.builder()
                    .taskId(taskId)
                    .chapterIndex(-1)
                    .type(RepairIssueType.PARAGRAPH)
                    .originalText("发现 " + count + " 处 \\r\\n 换行符")
                    .suggestedText("统一为 \\n")
                    .reason("换行符不统一，需将 \\r\\n 转换为 \\n")
                    .confidence(0.95)
                    .status(RepairIssueStatus.PENDING)
                    .source(RepairSource.AUTO)
                    .riskLevel(RiskLevel.LOW)
                    .build());
        }

        if (hasCR) {
            int count = countChar(text, '\r');
            issues.add(TextRepairIssue.builder()
                    .taskId(taskId)
                    .chapterIndex(-1)
                    .type(RepairIssueType.PARAGRAPH)
                    .originalText("发现 " + count + " 处 \\r 换行符")
                    .suggestedText("统一为 \\n")
                    .reason("换行符不统一，需将 \\r 转换为 \\n")
                    .confidence(0.95)
                    .status(RepairIssueStatus.PENDING)
                    .source(RepairSource.AUTO)
                    .riskLevel(RiskLevel.LOW)
                    .build());
        }

        return issues;
    }

    // ==================== 多余空行检测 ====================

    private List<TextRepairIssue> detectExcessiveBlankLines(
            String[] lines, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();
        int charOffset = 0;
        int blankRun = 0;
        int blankStart = -1;

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                if (blankRun == 0) blankStart = charOffset;
                blankRun++;
            } else {
                if (blankRun >= 3) {
                    issues.add(TextRepairIssue.builder()
                            .taskId(taskId)
                            .chapterIndex(-1)
                            .type(RepairIssueType.PARAGRAPH)
                            .startOffset(blankStart)
                            .endOffset(charOffset)
                            .originalText("连续 " + blankRun + " 个空行")
                            .suggestedText("压缩为 1 个空行")
                            .reason("多余空行，建议压缩为 1 个空行")
                            .confidence(0.9)
                            .status(RepairIssueStatus.PENDING)
                            .source(RepairSource.AUTO)
                            .riskLevel(RiskLevel.LOW)
                            .build());
                }
                blankRun = 0;
            }
            charOffset += lines[i].length() + 1;
        }

        return issues;
    }

    // ==================== 错误换行检测 ====================

    private List<TextRepairIssue> detectBrokenLines(
            String[] lines, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();
        int charOffset = 0;

        for (int i = 0; i < lines.length - 1; i++) {
            String current = lines[i];
            String next = lines[i + 1];
            String trimmedCurrent = current.trim();
            String trimmedNext = next.trim();

            if (trimmedCurrent.isEmpty() || trimmedNext.isEmpty()) {
                charOffset += current.length() + 1;
                continue;
            }

            // 检查是否应该合并
            if (shouldMergeLines(trimmedCurrent, trimmedNext)) {
                String merged = trimmedCurrent + trimmedNext;
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(-1)
                        .type(RepairIssueType.PARAGRAPH)
                        .startOffset(charOffset)
                        .endOffset(charOffset + current.length() + next.length() + 2)
                        .originalText(trimmedCurrent + "\n" + trimmedNext)
                        .suggestedText(merged)
                        .reason("一句话被拆成多行，建议合并")
                        .confidence(0.7)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.MEDIUM)
                        .build());
            }

            charOffset += current.length() + 1;
        }

        return issues;
    }

    /**
     * 判断两行是否应该合并
     */
    private boolean shouldMergeLines(String current, String next) {
        // 下一行是章节标题 → 不合并
        if (isChapterTitle(next)) return false;

        // 下一行以对话引导符号开头 → 不合并
        if (next.length() > 0 && DIALOGUE_STARTS.indexOf(next.charAt(0)) >= 0) return false;

        // 下一行是列表项 → 不合并
        if (next.length() > 0 && LIST_PREFIXES.indexOf(next.charAt(0)) >= 0) return false;

        // 上一行有完整句末标点 → 不合并
        if (current.length() > 0
                && SENTENCE_ENDINGS.indexOf(current.charAt(current.length() - 1)) >= 0) {
            return false;
        }

        // 上一行有对话结束标点（如 "） → 不合并
        if (current.length() > 0 && current.endsWith("\u201d")) return false;

        // 合并后长度合理（不超过 500 字）
        String merged = current + next;
        if (merged.length() > 500) return false;

        return true;
    }

    private boolean isChapterTitle(String line) {
        if (line == null || line.isEmpty()) return false;
        return line.matches("^第[一二三四五六七八九十百千万零\\d]+[章节回卷篇部].*")
                || java.util.regex.Pattern.compile("^Chapter\\s+\\d+.*",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(line).find()
                || line.matches("^(序章|序幕|楔子|尾声|终章|后记|前言|引言|番外).*");
    }

    // ==================== 段首缩进检测 ====================

    private List<TextRepairIssue> detectIndentIssues(
            String[] lines, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();
        int fullWidthIndent = 0;
        int halfSpaceIndent = 0;
        int noIndent = 0;
        int totalNonEmpty = 0;
        int charOffset = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isChapterTitle(trimmed)) {
                charOffset += line.length() + 1;
                continue;
            }
            totalNonEmpty++;

            if (line.startsWith("　　")) {
                fullWidthIndent++;
            } else if (line.startsWith("    ") || line.startsWith("\t")) {
                halfSpaceIndent++;
            } else if (line.equals(trimmed)) {
                noIndent++;
            }
            charOffset += line.length() + 1;
        }

        if (totalNonEmpty > 0) {
            double fullRatio = (double) fullWidthIndent / totalNonEmpty;
            double halfRatio = (double) halfSpaceIndent / totalNonEmpty;
            double noRatio = (double) noIndent / totalNonEmpty;

            // 如果缩进方式不一致
            if (fullRatio > 0.1 && halfRatio > 0.1) {
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(-1)
                        .type(RepairIssueType.PARAGRAPH)
                        .originalText("全角空格缩进: " + fullWidthIndent + " 行, 半角空格缩进: " + halfSpaceIndent + " 行")
                        .suggestedText("统一为全角空格缩进（　　）")
                        .reason("段首缩进方式不统一")
                        .confidence(0.7)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.MEDIUM)
                        .build());
            }
        }

        return issues;
    }

    // ==================== 段落修复执行 ====================

    /**
     * 统一换行符
     */
    public String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * 清理多余空行
     */
    public String cleanBlankLines(String text, int maxBlankLines) {
        if (maxBlankLines < 0) return text;
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int blankRun = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                blankRun++;
                if (blankRun <= maxBlankLines) {
                    if (sb.length() > 0) sb.append("\n");
                }
            } else {
                blankRun = 0;
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append("\n");
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 统一段首缩进
     */
    public String normalizeIndent(String text, String indentStyle) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        String indent = switch (indentStyle) {
            case "FULL_WIDTH_SPACE" -> "　　";
            case "HALF_SPACE" -> "  ";
            case "FOUR_SPACE" -> "    ";
            default -> "";
        };

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append("\n");
                }
            } else if (isChapterTitle(trimmed)) {
                // 章节标题不缩进
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append("\n");
                }
                sb.append(trimmed);
            } else {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append("\n");
                }
                sb.append(indent).append(trimmed);
            }
        }
        return sb.toString();
    }

    /**
     * 合并错误换行
     */
    public String mergeBrokenLines(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        StringBuilder currentPara = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                // 空行 → 段落分隔
                if (!currentPara.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(currentPara);
                    currentPara.setLength(0);
                }
            } else if (isChapterTitle(trimmed)) {
                // 章节标题 → 新段落
                if (!currentPara.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(currentPara);
                    currentPara.setLength(0);
                }
                if (sb.length() > 0) sb.append("\n");
                sb.append(trimmed);
            } else {
                // 普通行
                if (!currentPara.isEmpty() && shouldMergeLines(currentPara.toString(), trimmed)) {
                    currentPara.append(trimmed);
                } else {
                    if (!currentPara.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(currentPara);
                        currentPara.setLength(0);
                    }
                    currentPara.append(trimmed);
                }
            }
        }

        if (!currentPara.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(currentPara);
        }

        return sb.toString();
    }

    // ==================== 工具方法 ====================

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(sub, index)) != -1) {
            count++;
            index += sub.length();
        }
        return count;
    }

    private int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) count++;
        }
        return count;
    }
}
