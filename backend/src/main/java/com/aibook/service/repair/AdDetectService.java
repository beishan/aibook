package com.aibook.service.repair;

import com.aibook.model.entity.*;
import com.aibook.repository.TextRepairRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 广告信息检测服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdDetectService {

    private final TextRepairRuleRepository ruleRepository;

    /** 默认广告关键词评分表 */
    private static final Map<String, Integer> AD_KEYWORD_SCORES = new LinkedHashMap<>();
    static {
        AD_KEYWORD_SCORES.put("最新网址", 5);
        AD_KEYWORD_SCORES.put("本站", 3);
        AD_KEYWORD_SCORES.put("书友群", 4);
        AD_KEYWORD_SCORES.put("QQ群", 4);
        AD_KEYWORD_SCORES.put("微信群", 4);
        AD_KEYWORD_SCORES.put("微信公众号", 4);
        AD_KEYWORD_SCORES.put("加群", 4);
        AD_KEYWORD_SCORES.put("关注公众号", 4);
        AD_KEYWORD_SCORES.put("手机用户", 3);
        AD_KEYWORD_SCORES.put("下载地址", 3);
        AD_KEYWORD_SCORES.put("更多精彩", 3);
        AD_KEYWORD_SCORES.put("收藏本站", 3);
        AD_KEYWORD_SCORES.put("整理制作", 3);
        AD_KEYWORD_SCORES.put("校对", 2);
        AD_KEYWORD_SCORES.put("APP下载", 3);
    }

    /** URL 正则 */
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[^\\s\u4e00-\u9fff]{2,}|www\\.[a-zA-Z0-9][-a-zA-Z0-9.]+\\.[a-zA-Z]{2,}");

    /** QQ 群号正则 */
    private static final Pattern QQ_GROUP_PATTERN = Pattern.compile(
        "QQ群[：:]?\\s*\\d{5,}|群号[：:]?\\s*\\d{5,}");

    /** 微信号正则 */
    private static final Pattern WECHAT_PATTERN = Pattern.compile(
        "微信[号：:]?\\s*[a-zA-Z0-9_-]{6,}|微信号[：:]?\\s*[a-zA-Z0-9_-]{6,}");

    /** 超短行阈值（广告行通常较短） */
    private static final int SHORT_LINE_THRESHOLD = 100;

    /**
     * 扫描文本中的广告内容
     *
     * @param text       全文文本
     * @param lines      按行分割的文本
     * @param chapters   章节列表（用于判断位置和跨章节重复）
     * @param taskId     修复任务 ID
     * @param rules      自定义广告规则
     * @return 广告问题列表
     */
    public List<TextRepairIssue> scanForIssues(
            String text, String[] lines,
            List<ChapterInfo> chapters,
            Long taskId, List<TextRepairRule> rules) {

        List<TextRepairIssue> issues = new ArrayList<>();
        List<Pattern> whitelistPatterns = compileWhitelistPatterns(rules);

        // 1. 明确规则匹配
        issues.addAll(detectByRules(
                text, lines, chapters, taskId, rules, whitelistPatterns));

        // 2. 关键词+URL 评分检测
        issues.addAll(detectByScoring(lines, chapters, taskId, whitelistPatterns));

        // 3. 跨章节重复短句检测
        issues.addAll(detectRepeatedAds(lines, chapters, taskId, whitelistPatterns));

        return issues;
    }

    // ==================== 明确规则匹配 ====================

    private List<TextRepairIssue> detectByRules(
            String text, String[] lines, List<ChapterInfo> chapters,
            Long taskId, List<TextRepairRule> rules,
            List<Pattern> whitelistPatterns) {

        List<TextRepairIssue> issues = new ArrayList<>();
        int charOffset = 0;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String trimmed = line.trim();
            int chapterIndex = getChapterIndex(chapters, charOffset);

            if (isWhitelisted(line, whitelistPatterns)) {
                charOffset += line.length() + 1;
                continue;
            }

            for (TextRepairRule rule : rules) {
                if (!Boolean.TRUE.equals(rule.getEnabled())) continue;
                if (Boolean.TRUE.equals(rule.getWhitelist())) continue;
                if (rule.getType() != RepairIssueType.AD) continue;

                java.util.regex.Matcher matcher;
                try {
                    matcher = Pattern.compile(rule.getPattern()).matcher(line);
                } catch (Exception e) {
                    log.warn("广告规则正则编译失败: {}", rule.getPattern());
                    continue;
                }

                if (matcher.find() && matchesConfiguredScope(
                        rule.getMatchScope(), chapters, charOffset)) {
                    int startOffset = charOffset;
                    int endOffset = Math.min(text.length(), charOffset + line.length());
                    String originalText = line;
                    String suggestedText;
                    switch (rule.getAction()) {
                        case DELETE_MATCH -> suggestedText = matcher.replaceAll("");
                        case REPLACE -> suggestedText = matcher.replaceAll(
                                rule.getReplacement() == null ? "" : rule.getReplacement());
                        case MARK_ONLY -> suggestedText = null;
                        case DELETE_PARAGRAPH -> {
                            startOffset = paragraphStart(text, charOffset);
                            endOffset = paragraphEnd(text, charOffset + line.length());
                            originalText = text.substring(startOffset, endOffset);
                            suggestedText = "";
                        }
                        case DELETE_LINE -> {
                            endOffset = Math.min(text.length(), charOffset + line.length() + 1);
                            suggestedText = "";
                        }
                        default -> suggestedText = null;
                    }
                    issues.add(TextRepairIssue.builder()
                            .taskId(taskId)
                            .chapterIndex(chapterIndex)
                            .type(RepairIssueType.AD)
                            .startOffset(startOffset)
                            .endOffset(endOffset)
                            .originalText(originalText)
                            .suggestedText(suggestedText)
                            .reason("匹配规则: " + rule.getName())
                            .ruleId(String.valueOf(rule.getId()))
                            .confidence(0.9)
                            .status(RepairIssueStatus.PENDING)
                            .source(RepairSource.AUTO)
                            .riskLevel(rule.getRiskLevel())
                            .build());
                }
            }
            charOffset += line.length() + 1;
        }
        return issues;
    }

    private boolean matchesConfiguredScope(MatchScope scope,
                                           List<ChapterInfo> chapters,
                                           int charOffset) {
        if (scope == null || scope == MatchScope.LINE
                || scope == MatchScope.CONTENT || scope == MatchScope.PARAGRAPH) {
            return true;
        }
        if (chapters == null || chapters.isEmpty()) return false;
        for (ChapterInfo chapter : chapters) {
            if (scope == MatchScope.CHAPTER_START
                    && Math.abs(charOffset - chapter.startOffset) < 200) return true;
            if (scope == MatchScope.CHAPTER_END && chapter.endOffset > 0
                    && Math.abs(charOffset - chapter.endOffset) < 200) return true;
        }
        return false;
    }

    private int paragraphStart(String text, int offset) {
        int boundary = text.lastIndexOf("\n\n", Math.max(0, offset - 1));
        return boundary < 0 ? 0 : boundary + 2;
    }

    private int paragraphEnd(String text, int offset) {
        int boundary = text.indexOf("\n\n", Math.min(offset, text.length()));
        return boundary < 0 ? text.length() : boundary + 2;
    }

    // ==================== 评分检测 ====================

    private List<TextRepairIssue> detectByScoring(
            String[] lines, List<ChapterInfo> chapters, Long taskId,
            List<Pattern> whitelistPatterns) {

        List<TextRepairIssue> issues = new ArrayList<>();
        int charOffset = 0;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String trimmed = line.trim();

            if (!trimmed.isEmpty() && !isWhitelisted(line, whitelistPatterns)) {
                int score = calculateAdScore(trimmed, lineIndex, lines, chapters, charOffset);
                int chapterIndex = getChapterIndex(chapters, charOffset);

                if (score >= 7) {
                    // 高分：建议自动删除
                    issues.add(TextRepairIssue.builder()
                            .taskId(taskId)
                            .chapterIndex(chapterIndex)
                            .type(RepairIssueType.AD)
                            .startOffset(charOffset)
                            .endOffset(charOffset + line.length() + 1)
                            .originalText(trimmed)
                            .suggestedText("[已删除]")
                            .reason("广告评分: " + score + "（建议自动删除）")
                            .confidence(Math.min(1.0, score / 10.0))
                            .status(RepairIssueStatus.PENDING)
                            .source(RepairSource.AUTO)
                            .riskLevel(RiskLevel.LOW)
                            .build());
                } else if (score >= 4) {
                    // 中分：需用户确认
                    issues.add(TextRepairIssue.builder()
                            .taskId(taskId)
                            .chapterIndex(chapterIndex)
                            .type(RepairIssueType.AD)
                            .startOffset(charOffset)
                            .endOffset(charOffset + line.length() + 1)
                            .originalText(trimmed)
                            .suggestedText("[已删除]")
                            .reason("广告评分: " + score + "（需确认）")
                            .confidence(score / 10.0)
                            .status(RepairIssueStatus.PENDING)
                            .source(RepairSource.AUTO)
                            .riskLevel(RiskLevel.MEDIUM)
                            .build());
                }
            }
            charOffset += line.length() + 1;
        }
        return issues;
    }

    private int calculateAdScore(String text, int lineIndex,
                                 String[] lines, List<ChapterInfo> chapters,
                                 int charOffset) {
        int score = 0;

        // URL +5
        if (URL_PATTERN.matcher(text).find()) score += 5;

        // 关键词评分
        for (Map.Entry<String, Integer> entry : AD_KEYWORD_SCORES.entrySet()) {
            if (text.contains(entry.getKey())) {
                score += entry.getValue();
            }
        }

        // QQ 群 / 微信号
        if (QQ_GROUP_PATTERN.matcher(text).find()) score += 4;
        if (WECHAT_PATTERN.matcher(text).find()) score += 4;

        // 位于章节开头或结尾 +2
        boolean atChapterBoundary = isAtChapterBoundary(chapters, charOffset, lines, lineIndex);
        if (atChapterBoundary) score += 2;

        // 长度小于 100 字 +1
        if (text.length() < SHORT_LINE_THRESHOLD) score += 1;

        // 包含句号、问号等正文标点 → 减分
        if (text.contains("。") || text.contains("？") || text.contains("！")) {
            score -= 2;
        }

        // 长度超过 200 字 → 减分（长文本不太可能是广告）
        if (text.length() > 200) score -= 3;

        return Math.max(0, score);
    }

    private boolean isAtChapterBoundary(List<ChapterInfo> chapters,
                                         int charOffset, String[] lines,
                                         int lineIndex) {
        if (chapters == null || chapters.isEmpty()) return false;
        for (ChapterInfo chapter : chapters) {
            // 章节开头（章节起始位置前后 3 行）
            if (Math.abs(charOffset - chapter.startOffset) < 200) return true;
            // 章节结尾（下一章起始位置前 3 行）
            if (chapter.endOffset > 0 && Math.abs(charOffset - chapter.endOffset) < 200) return true;
        }
        return false;
    }

    // ==================== 跨章节重复检测 ====================

    private List<TextRepairIssue> detectRepeatedAds(
            String[] lines, List<ChapterInfo> chapters, Long taskId,
            List<Pattern> whitelistPatterns) {

        List<TextRepairIssue> issues = new ArrayList<>();

        // 统计每行出现的章节
        Map<String, Set<Integer>> lineChapters = new HashMap<>();
        Map<String, int[]> lineOffsets = new HashMap<>();

        int charOffset = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            // 只关注 8~150 字的短句
            if (!isWhitelisted(lines[i], whitelistPatterns)
                    && trimmed.length() >= 8 && trimmed.length() <= 150) {
                final int offset = charOffset;
                final int lineEnd = charOffset + lines[i].length() + 1;
                lineChapters.computeIfAbsent(trimmed, k -> new HashSet<>())
                        .add(getChapterIndex(chapters, offset));
                lineOffsets.computeIfAbsent(trimmed, k -> new int[]{offset, lineEnd});
            }
            charOffset += lines[i].length() + 1;
        }

        // 筛选跨章节重复出现的短句
        for (Map.Entry<String, Set<Integer>> entry : lineChapters.entrySet()) {
            if (entry.getValue().size() >= 3 && entry.getValue().size() >= 5
                    || (entry.getValue().size() >= 3 && countTotalOccurrences(lines, entry.getKey()) >= 5)) {
                int[] offsets = lineOffsets.get(entry.getKey());
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(-1)
                        .type(RepairIssueType.AD)
                        .startOffset(offsets[0])
                        .endOffset(offsets[1])
                        .originalText(entry.getKey())
                        .suggestedText("[已删除]")
                        .reason("跨" + entry.getValue().size() + "个章节重复出现，疑似广告")
                        .confidence(0.8)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.MEDIUM)
                        .build());
            }
        }

        return issues;
    }

    private int countTotalOccurrences(String[] lines, String text) {
        int count = 0;
        for (String line : lines) {
            if (line.trim().equals(text)) count++;
        }
        return count;
    }

    // ==================== 工具方法 ====================

    private int getChapterIndex(List<ChapterInfo> chapters, int charOffset) {
        if (chapters == null || chapters.isEmpty()) return -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (charOffset >= chapters.get(i).startOffset
                    && (chapters.get(i).endOffset == 0
                        || charOffset < chapters.get(i).endOffset)) {
                return i;
            }
        }
        return chapters.size() - 1;
    }

    private List<Pattern> compileWhitelistPatterns(List<TextRepairRule> rules) {
        List<Pattern> patterns = new ArrayList<>();
        for (TextRepairRule rule : rules) {
            if (!Boolean.TRUE.equals(rule.getEnabled())
                    || !Boolean.TRUE.equals(rule.getWhitelist())) continue;
            try {
                patterns.add(Pattern.compile(rule.getPattern()));
            } catch (Exception e) {
                log.warn("广告白名单正则编译失败: {}", rule.getPattern());
            }
        }
        return patterns;
    }

    private boolean isWhitelisted(String text, List<Pattern> patterns) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    /**
     * 章节信息内部类
     */
    public record ChapterInfo(int startOffset, int endOffset, String title) {}
}
