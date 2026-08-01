package com.aibook.service.repair;

import com.aibook.dto.DetectedChapterDTO;
import com.aibook.model.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 章节识别服务
 */
@Service
@Slf4j
public class ChapterDetectService {

    /** 章节关键词 */
    private static final Set<String> CHAPTER_KEYWORDS = Set.of(
        "章", "节", "回", "卷", "部", "篇", "集"
    );

    /** 特殊章节关键词 */
    private static final Set<String> SPECIAL_KEYWORDS = Set.of(
        "序章", "序幕", "序言", "前言", "楔子", "引子", "引言",
        "后记", "终章", "尾声", "番外", "附录", "大结局"
    );

    // ========== 章节正则 ==========

    /** 第X章/回/节/卷/篇/部（中文数字或阿拉伯数字） */
    private static final Pattern CHAPTER_DI_PATTERN = Pattern.compile(
        "^第[\\s]*([一二三四五六七八九十百千万零壹贰叁肆伍陆柒捌玖拾佰仟\\d]+)[\\s]*([章节回卷篇部])");

    /** 卷X */
    private static final Pattern VOLUME_PATTERN = Pattern.compile(
        "^卷[\\s]*([一二三四五六七八九十\\d]+)");

    /** Chapter N */
    private static final Pattern CHAPTER_EN_PATTERN = Pattern.compile(
        "^[Cc][Hh][Aa][Pp][Tt][Ee][Rr][\\s]+(\\d+)");

    /** 纯数字编号 */
    private static final Pattern NUMBER_TITLE_PATTERN = Pattern.compile(
        "^(\\d{1,4})[\\.．]\\s*(\\S{1,30})$");

    /** 中文数字编号 */
    private static final Pattern CHINESE_NUMBER_PATTERN = Pattern.compile(
        "^([一二三四五六七八九十]+)[、\\.．]\\s*(\\S{1,30})$");

    /** 特殊章节 */
    private static final Pattern SPECIAL_CHAPTER_PATTERN = Pattern.compile(
        "^(序章|序幕|序言|前言|楔子|引子|引言|后记|终章|尾声|番外|附录|大结局)(.*)");

    /** 正文前缀 */
    private static final Pattern BODY_PREFIX_PATTERN = Pattern.compile(
        "^正文[\\s]*");

    /** Markdown 标题 */
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile(
        "^#{1,3}\\s+(.{1,100})");

    /**
     * 识别全文章节
     */
    public List<DetectedChapterDTO> detectChapters(String text) {
        String[] lines = text.split("\n", -1);
        List<ChapterCandidate> candidates = new ArrayList<>();
        int charOffset = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (!trimmed.isEmpty()) {
                ChapterCandidate candidate = tryMatch(
                        trimmed, charOffset + line.indexOf(trimmed), i, lines);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            charOffset += line.length() + 1;
        }

        // 如果正则匹配到足够多的章节，使用正则结果
        if (candidates.size() >= 2) {
            return convertToDTOs(candidates, text);
        }

        // 回退：空行启发式
        return detectByBlankLines(lines, text);
    }

    /**
     * 扫描章节识别问题
     */
    public List<TextRepairIssue> scanForIssues(
            List<DetectedChapterDTO> chapters, Long taskId) {
        return scanForIssues(chapters, taskId, 100, 30000, true, true);
    }

    public List<TextRepairIssue> scanForIssues(
            List<DetectedChapterDTO> chapters, Long taskId,
            int minWords, int maxWords,
            boolean includeNumberAnomalies, boolean includeAdhesion) {
        List<TextRepairIssue> issues = new ArrayList<>();

        for (int i = 0; i < chapters.size(); i++) {
            DetectedChapterDTO chapter = chapters.get(i);

            // 低置信度章节
            if (chapter.getConfidence() != null && chapter.getConfidence() < 0.5) {
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(i)
                        .type(RepairIssueType.CHAPTER)
                        .startOffset(chapter.getStartOffset())
                        .endOffset(chapter.getEndOffset())
                        .originalText(chapter.getOriginalTitle())
                        .suggestedText(null)
                        .reason("章节识别置信度较低（" + chapter.getConfidence() + "），请确认是否为章节标题。")
                        .confidence(chapter.getConfidence())
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.MEDIUM)
                        .build());
            }

            // 检测章节标题粘连
            if (includeAdhesion && chapter.getOriginalTitle() != null
                    && chapter.getOriginalTitle().length() > 50) {
                // 可能是正文和章节标题粘连
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(i)
                        .type(RepairIssueType.CHAPTER_ADHESION)
                        .startOffset(chapter.getStartOffset())
                        .endOffset(chapter.getEndOffset())
                        .originalText(chapter.getOriginalTitle())
                        .suggestedText(null)
                        .reason("章节标题过长，可能与正文粘连。")
                        .confidence(0.6)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.HIGH)
                        .build());
            }
        }

        // 检测章节编号异常
        if (includeNumberAnomalies) {
            issues.addAll(detectChapterNumberAnomalies(chapters, taskId));
        }

        // 检测章节字数异常
        if (includeNumberAnomalies) {
            issues.addAll(detectChapterWordCountAnomalies(
                    chapters, taskId, minWords, maxWords));
        }

        return issues;
    }

    // ==================== 正则匹配 ====================

    private ChapterCandidate tryMatch(String text, int charOffset,
                                       int lineIndex, String[] lines) {
        if (!isValidChapterTitle(text)) return null;

        // 去掉"正文"前缀
        String cleanText = BODY_PREFIX_PATTERN.matcher(text).replaceFirst("").trim();

        // 匹配 第X章
        Matcher diMatcher = CHAPTER_DI_PATTERN.matcher(cleanText);
        if (diMatcher.find()) {
            String numberStr = diMatcher.group(1);
            String keyword = diMatcher.group(2);
            String title = cleanText.substring(diMatcher.end()).trim();
            int number = ChapterNumberConverter.chineseToNumber(numberStr);
            double confidence = calculateConfidence(cleanText, lineIndex, lines, numberStr);
            return new ChapterCandidate(number, numberStr, text,
                    title, keyword, charOffset, confidence);
        }

        // 匹配 卷X
        Matcher volMatcher = VOLUME_PATTERN.matcher(cleanText);
        if (volMatcher.find()) {
            String numberStr = volMatcher.group(1);
            String title = cleanText.substring(volMatcher.end()).trim();
            int number = ChapterNumberConverter.chineseToNumber(numberStr);
            double confidence = calculateConfidence(cleanText, lineIndex, lines, numberStr);
            return new ChapterCandidate(number, numberStr, text,
                    title, "卷", charOffset, confidence);
        }

        // 匹配 Chapter N
        Matcher enMatcher = CHAPTER_EN_PATTERN.matcher(cleanText);
        if (enMatcher.find()) {
            int number = Integer.parseInt(enMatcher.group(1));
            String title = cleanText.substring(enMatcher.end()).trim();
            return new ChapterCandidate(number, String.valueOf(number), text,
                    title, "Chapter", charOffset, 0.7);
        }

        // 匹配特殊章节
        Matcher specialMatcher = SPECIAL_CHAPTER_PATTERN.matcher(cleanText);
        if (specialMatcher.find()) {
            String keyword = specialMatcher.group(1);
            String title = specialMatcher.group(2).trim();
            return new ChapterCandidate(0, keyword, text,
                    title, keyword, charOffset, 0.8);
        }

        // 匹配数字编号
        Matcher numMatcher = NUMBER_TITLE_PATTERN.matcher(cleanText);
        if (numMatcher.find()) {
            int number = Integer.parseInt(numMatcher.group(1));
            String title = numMatcher.group(2);
            return new ChapterCandidate(number, numMatcher.group(1), text,
                    title, "数字编号", charOffset, 0.6);
        }

        // 匹配中文数字编号
        Matcher chMatcher = CHINESE_NUMBER_PATTERN.matcher(cleanText);
        if (chMatcher.find()) {
            int number = ChapterNumberConverter.chineseToNumber(chMatcher.group(1));
            String title = chMatcher.group(2);
            return new ChapterCandidate(number, chMatcher.group(1), text,
                    title, "中文编号", charOffset, 0.6);
        }

        // 匹配 Markdown 标题
        Matcher mdMatcher = MARKDOWN_PATTERN.matcher(cleanText);
        if (mdMatcher.find()) {
            String title = mdMatcher.group(1);
            return new ChapterCandidate(0, "", text,
                    title, "Markdown", charOffset, 0.5);
        }

        return null;
    }

    private double calculateConfidence(String text, int lineIndex, String[] lines, String numberStr) {
        int score = 6; // 匹配"第X章"基础分

        // 单独占一行 +2
        score += 2;

        // 长度小于 40 字 +2
        if (text.length() < 40) score += 2;

        // 上一行为空行 +1
        if (lineIndex > 0 && lines[lineIndex - 1].trim().isEmpty()) score += 1;

        // 下一行为空行 +1
        if (lineIndex < lines.length - 1 && lines[lineIndex + 1].trim().isEmpty()) score += 1;

        // 包含句号 -3
        if (text.contains("。") || text.contains("？") || text.contains("！")) score -= 3;

        // 长度超过 80 字 -5
        if (text.length() > 80) score -= 5;

        // 以对话引导符开头 -4
        char first = text.charAt(0);
        if (first == '"' || first == '"' || first == '\'') score -= 4;

        return Math.max(0, Math.min(1.0, score / 10.0));
    }

    private boolean isValidChapterTitle(String text) {
        if (text == null || text.length() < 2 || text.length() > 80) return false;
        // 排除纯分隔符
        if (text.matches("^[\\-=*~─━═]{3,}\\s*$")) return false;
        // 排除纯标点或纯数字
        if (text.matches("^[\\p{Punct}\\s]+$")) return false;
        return true;
    }

    // ==================== 空行启发式 ====================

    private List<DetectedChapterDTO> detectByBlankLines(String[] lines, String text) {
        List<DetectedChapterDTO> chapters = new ArrayList<>();
        int blankRun = 0;
        int charOffset = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                blankRun++;
            } else {
                if (blankRun >= 2) {
                    if (trimmed.length() >= 2 && trimmed.length() <= 50
                            && isValidChapterTitle(trimmed)) {
                        chapters.add(DetectedChapterDTO.builder()
                                .number(chapters.size() + 1)
                                .originalTitle(trimmed)
                                .normalizedTitle(trimmed)
                                .type("HEURISTIC")
                                .startOffset(charOffset)
                                .confidence(0.4)
                                .build());
                    }
                }
                blankRun = 0;
            }
            charOffset += line.length() + 1;
        }

        // 填充 endOffset
        fillEndOffsets(chapters, text.length());
        return chapters;
    }

    // ==================== 章节编号异常检测 ====================

    private List<TextRepairIssue> detectChapterNumberAnomalies(
            List<DetectedChapterDTO> chapters, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        // 过滤掉无编号的特殊章节
        List<DetectedChapterDTO> numberedChapters = chapters.stream()
                .filter(c -> c.getNumber() != null && c.getNumber() > 0)
                .toList();

        for (int i = 1; i < numberedChapters.size(); i++) {
            int prevNum = numberedChapters.get(i - 1).getNumber();
            int currNum = numberedChapters.get(i).getNumber();

            // 缺失章节
            if (currNum > prevNum + 1) {
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(i)
                        .type(RepairIssueType.CHAPTER_ANOMALY)
                        .originalText("第" + prevNum + "章 → 第" + currNum + "章")
                        .suggestedText(null)
                        .reason("疑似缺失第" + (prevNum + 1) + "章至第" + (currNum - 1) + "章")
                        .confidence(0.8)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.MEDIUM)
                        .build());
            }

            // 重复章节编号
            if (currNum == prevNum) {
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(i)
                        .type(RepairIssueType.CHAPTER_ANOMALY)
                        .originalText("第" + currNum + "章 重复出现")
                        .suggestedText(null)
                        .reason("章节编号重复：第" + currNum + "章")
                        .confidence(0.9)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.MEDIUM)
                        .build());
            }

            // 章节乱序
            if (currNum < prevNum) {
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(i)
                        .type(RepairIssueType.CHAPTER_ANOMALY)
                        .originalText("第" + prevNum + "章 → 第" + currNum + "章")
                        .suggestedText(null)
                        .reason("疑似章节顺序异常")
                        .confidence(0.9)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.HIGH)
                        .build());
            }
        }

        return issues;
    }

    // ==================== 章节字数异常检测 ====================

    private List<TextRepairIssue> detectChapterWordCountAnomalies(
            List<DetectedChapterDTO> chapters, Long taskId,
            int minWords, int maxWords) {
        List<TextRepairIssue> issues = new ArrayList<>();

        for (int i = 0; i < chapters.size(); i++) {
            DetectedChapterDTO chapter = chapters.get(i);
            Integer wordCount = chapter.getWordCount();
            if (wordCount == null) continue;

            // 超短章节
            if (wordCount < minWords) {
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(i)
                        .type(RepairIssueType.CHAPTER_ANOMALY)
                        .originalText(chapter.getOriginalTitle() + "（" + wordCount + "字）")
                        .suggestedText(null)
                        .reason("超短章节：仅 " + wordCount + " 字")
                        .confidence(0.5)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.LOW)
                        .build());
            }

            // 超长章节
            if (wordCount > maxWords) {
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(i)
                        .type(RepairIssueType.CHAPTER_ANOMALY)
                        .originalText(chapter.getOriginalTitle() + "（" + wordCount + "字）")
                        .suggestedText(null)
                        .reason("超长章节：" + wordCount + " 字，可能存在多章粘连")
                        .confidence(0.6)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.MEDIUM)
                        .build());
            }
        }
        return issues;
    }

    // ==================== 工具方法 ====================

    private List<DetectedChapterDTO> convertToDTOs(
            List<ChapterCandidate> candidates, String text) {
        List<DetectedChapterDTO> dtos = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            ChapterCandidate c = candidates.get(i);
            int endOffset = i + 1 < candidates.size()
                    ? candidates.get(i + 1).charOffset : text.length();
            int wordCount = endOffset > c.charOffset
                    ? text.substring(c.charOffset, Math.min(endOffset, text.length()))
                        .replaceAll("\\s+", "").length() : 0;

            dtos.add(DetectedChapterDTO.builder()
                    .number(c.number)
                    .originalNumber(c.originalNumber)
                    .originalTitle(c.originalTitle)
                    .normalizedTitle(c.originalTitle)
                    .type(c.keyword)
                    .startOffset(c.charOffset)
                    .endOffset(endOffset)
                    .confidence(c.confidence)
                    .wordCount(wordCount)
                    .build());
        }
        return dtos;
    }

    private void fillEndOffsets(List<DetectedChapterDTO> chapters, int totalLength) {
        for (int i = 0; i < chapters.size() - 1; i++) {
            chapters.get(i).setEndOffset(chapters.get(i + 1).getStartOffset());
        }
        if (!chapters.isEmpty()) {
            chapters.get(chapters.size() - 1).setEndOffset(totalLength);
        }
    }

    // ==================== 内部类 ====================

    private static class ChapterCandidate {
        final int number;
        final String originalNumber;
        final String originalTitle;
        final String title;
        final String keyword;
        final int charOffset;
        final double confidence;

        ChapterCandidate(int number, String originalNumber, String originalTitle,
                          String title, String keyword, int charOffset, double confidence) {
            this.number = number;
            this.originalNumber = originalNumber;
            this.originalTitle = originalTitle;
            this.title = title;
            this.keyword = keyword;
            this.charOffset = charOffset;
            this.confidence = confidence;
        }
    }
}
