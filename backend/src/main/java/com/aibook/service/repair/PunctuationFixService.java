package com.aibook.service.repair;

import com.aibook.model.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 空格、标点与不可见字符修复服务
 */
@Service
@Slf4j
public class PunctuationFixService {

    /** 不可见字符正则 */
    private static final String INVISIBLE_CHARS = "[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]";

    /** 标点映射：英文 → 中文 */
    private static final Map<Character, Character> PUNCTUATION_MAP = new LinkedHashMap<>();
    static {
        PUNCTUATION_MAP.put(',', '，');
        PUNCTUATION_MAP.put('.', '。');
        PUNCTUATION_MAP.put('!', '！');
        PUNCTUATION_MAP.put('?', '？');
        PUNCTUATION_MAP.put(':', '：');
        PUNCTUATION_MAP.put('(', '（');
        PUNCTUATION_MAP.put(')', '）');
        PUNCTUATION_MAP.put('[', '【');
        PUNCTUATION_MAP.put(']', '】');
    }

    /**
     * 扫描空格、标点与不可见字符问题
     */
    public List<TextRepairIssue> scanForIssues(
            String text, String[] lines, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        // 1. 不可见字符
        issues.addAll(detectInvisibleChars(text, taskId));

        // 2. 行尾空格
        issues.addAll(detectTrailingSpaces(lines, taskId));

        // 3. 标点前多余空格
        issues.addAll(detectSpaceBeforePunctuation(lines, taskId));

        // 4. 英文标点（可选）
        issues.addAll(detectEnglishPunctuation(lines, taskId));

        // 5. 重复标点
        issues.addAll(detectRepeatedPunctuation(lines, taskId));

        return issues;
    }

    // ==================== 不可见字符 ====================

    private List<TextRepairIssue> detectInvisibleChars(String text, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        int zeroWidthCount = countInvisibleChars(text);
        if (zeroWidthCount > 0) {
            // 找到第一处不可见字符的位置
            int firstPos = findFirstInvisible(text);

            issues.add(TextRepairIssue.builder()
                    .taskId(taskId)
                    .chapterIndex(-1)
                    .type(RepairIssueType.INVISIBLE_CHAR)
                    .originalText("发现 " + zeroWidthCount + " 个不可见字符")
                    .suggestedText("[已清理]")
                    .reason("零宽空格、BOM、控制字符等不可见字符需要清理")
                    .confidence(0.95)
                    .status(RepairIssueStatus.PENDING)
                    .source(RepairSource.AUTO)
                    .riskLevel(RiskLevel.LOW)
                    .metadataJson(invisibleCharPreview(text, firstPos))
                    .build());
        }

        return issues;
    }

    private int countInvisibleChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isInvisibleChar(c)) count++;
        }
        return count;
    }

    private int findFirstInvisible(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isInvisibleChar(text.charAt(i))) return i;
        }
        return -1;
    }

    private boolean isInvisibleChar(char c) {
        // 零宽空格 U+200B ~ U+200F
        if (c >= 0x200B && c <= 0x200F) return true;
        // BOM U+FEFF
        if (c == 0xFEFF) return true;
        // 控制字符（除换行和制表符）
        if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') return true;
        if (c == 0x7F) return true;
        // 双向控制字符
        if (c >= 0x202A && c <= 0x202E) return true;
        // Word joiner
        if (c == 0x2060) return true;
        return false;
    }

    // ==================== 行尾空格 ====================

    private List<TextRepairIssue> detectTrailingSpaces(
            String[] lines, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();
        int count = 0;
        String sampleOriginal = null;
        String sampleSuggested = null;

        for (String line : lines) {
            int end = line.length();
            while (end > 0 && (line.charAt(end - 1) == ' '
                    || line.charAt(end - 1) == '\t'
                    || line.charAt(end - 1) == '　')) {
                end--;
            }
            int trailing = line.length() - end;
            if (trailing > 0) {
                count++;
                if (sampleOriginal == null) {
                    sampleOriginal = line.substring(0, end)
                            + "⟦行尾空白 × " + trailing + "⟧";
                    sampleSuggested = line.substring(0, end);
                }
            }
        }

        if (count > 0) {
            issues.add(TextRepairIssue.builder()
                    .taskId(taskId)
                    .chapterIndex(-1)
                    .type(RepairIssueType.PUNCTUATION)
                    .originalText("发现 " + count + " 行行尾空格")
                    .suggestedText("[已删除行尾空格]")
                    .reason("行尾多余空格需要删除")
                    .confidence(0.9)
                    .status(RepairIssueStatus.PENDING)
                    .source(RepairSource.AUTO)
                    .riskLevel(RiskLevel.LOW)
                    .metadataJson(RepairMetadataUtil.samplePreview(
                            sampleOriginal, sampleSuggested, "空白字符已转换为可见标记"))
                    .build());
        }

        return issues;
    }

    // ==================== 标点前空格 ====================

    private List<TextRepairIssue> detectSpaceBeforePunctuation(
            String[] lines, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();
        int count = 0;
        String sampleOriginal = null;
        String sampleSuggested = null;

        for (String line : lines) {
            // 检测 " ，" " 。" " ：" 等模式
            String trimmed = line.trim();
            int lineCount = countSpaceBeforePunct(trimmed);
            count += lineCount;
            if (lineCount > 0 && sampleOriginal == null) {
                sampleOriginal = trimmed;
                sampleSuggested = removeSpaceBeforePunct(trimmed);
            }
        }

        if (count > 0) {
            issues.add(TextRepairIssue.builder()
                    .taskId(taskId)
                    .chapterIndex(-1)
                    .type(RepairIssueType.PUNCTUATION)
                    .originalText("发现 " + count + " 处标点前多余空格")
                    .suggestedText("[已删除标点前空格]")
                    .reason("标点前不应有空格")
                    .confidence(0.9)
                    .status(RepairIssueStatus.PENDING)
                    .source(RepairSource.AUTO)
                    .riskLevel(RiskLevel.LOW)
                    .metadataJson(RepairMetadataUtil.samplePreview(
                            sampleOriginal, sampleSuggested, "展示第一处匹配样例"))
                    .build());
        }

        return issues;
    }

    private int countSpaceBeforePunct(String text) {
        int count = 0;
        for (int i = 1; i < text.length(); i++) {
            char prev = text.charAt(i - 1);
            char curr = text.charAt(i);
            if ((prev == ' ' || prev == '　')
                    && isChinesePunct(curr)) {
                count++;
            }
        }
        return count;
    }

    private boolean isChinesePunct(char c) {
        return c == '，' || c == '。' || c == '：' || c == '；'
                || c == '！' || c == '？' || c == '、'
                || c == '"' || c == '"';
    }

    // ==================== 英文标点 ====================

    private List<TextRepairIssue> detectEnglishPunctuation(
            String[] lines, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();
        int count = 0;
        String sampleOriginal = null;
        String sampleSuggested = null;

        for (String line : lines) {
            String trimmed = line.trim();
            for (Map.Entry<Character, Character> entry : PUNCTUATION_MAP.entrySet()) {
                // 排除 URL 和英文句子中的标点
                // 简单判断：如果行中包含中文字符，则检测英文标点
                if (hasChinese(trimmed)) {
                    count += countChar(trimmed, entry.getKey());
                }
            }
            if (sampleOriginal == null && hasChinese(trimmed)
                    && containsMappedPunctuation(trimmed)) {
                sampleOriginal = trimmed;
                sampleSuggested = normalizePunctuation(trimmed);
            }
        }

        if (count > 0) {
            issues.add(TextRepairIssue.builder()
                    .taskId(taskId)
                    .chapterIndex(-1)
                    .type(RepairIssueType.PUNCTUATION)
                    .originalText("发现 " + count + " 处英文标点")
                    .suggestedText("[已转换为中文标点]")
                    .reason("中文文本中应使用中文标点")
                    .confidence(0.7)
                    .status(RepairIssueStatus.PENDING)
                    .source(RepairSource.AUTO)
                    .riskLevel(RiskLevel.LOW)
                    .metadataJson(RepairMetadataUtil.samplePreview(
                            sampleOriginal, sampleSuggested, "展示第一处匹配样例"))
                    .build());
        }

        return issues;
    }

    // ==================== 重复标点 ====================

    private List<TextRepairIssue> detectRepeatedPunctuation(
            String[] lines, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();
        int count = 0;
        String sampleOriginal = null;
        String sampleSuggested = null;

        for (String line : lines) {
            String trimmed = line.trim();
            // 检测重复标点（3个以上）
            for (int i = 0; i < trimmed.length() - 2; i++) {
                char c = trimmed.charAt(i);
                if (isChinesePunct(c) || c == '.' || c == '!' || c == '?') {
                    int run = 1;
                    while (i + run < trimmed.length() && trimmed.charAt(i + run) == c) {
                        run++;
                    }
                    if (run >= 3) {
                        count++;
                        if (sampleOriginal == null) {
                            sampleOriginal = trimmed;
                            sampleSuggested = cleanRepeatedPunctuation(trimmed);
                        }
                        i += run - 1;
                    }
                }
            }
        }

        if (count > 0) {
            issues.add(TextRepairIssue.builder()
                    .taskId(taskId)
                    .chapterIndex(-1)
                    .type(RepairIssueType.PUNCTUATION)
                    .originalText("发现 " + count + " 处重复标点")
                    .suggestedText("[已清理重复标点]")
                    .reason("重复标点需要清理")
                    .confidence(0.8)
                    .status(RepairIssueStatus.PENDING)
                    .source(RepairSource.AUTO)
                    .riskLevel(RiskLevel.LOW)
                    .metadataJson(RepairMetadataUtil.samplePreview(
                            sampleOriginal, sampleSuggested, "展示第一处匹配样例"))
                    .build());
        }

        return issues;
    }

    // ==================== 修复执行方法 ====================

    /**
     * 清理不可见字符
     */
    public String cleanInvisibleChars(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!isInvisibleChar(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 删除行尾空格
     */
    public String trimTrailingSpaces(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(stripTrailing(lines[i]));
        }
        return sb.toString();
    }

    /**
     * 删除标点前空格
     */
    public String removeSpaceBeforePunct(String text) {
        return text.replaceAll("[\\s\u3000]+([\uff0c\u3002\uff1a\uff1b\uff01\uff1f\u3001\u201c\u201d])", "$1");
    }

    /**
     * 英文标点转中文标点
     */
    public String normalizePunctuation(String text) {
        String result = text;
        for (Map.Entry<Character, Character> entry : PUNCTUATION_MAP.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 清理重复标点（3个以上压缩为2个）
     */
    public String cleanRepeatedPunctuation(String text) {
        return text.replaceAll("([，。：；！？、\\.\\!\\?])\\1{2,}", "$1$1");
    }

    // ==================== 工具方法 ====================

    private boolean hasChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    private int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) count++;
        }
        return count;
    }

    private boolean containsMappedPunctuation(String text) {
        for (Character punctuation : PUNCTUATION_MAP.keySet()) {
            if (text.indexOf(punctuation) >= 0) return true;
        }
        return false;
    }

    private String invisibleCharPreview(String text, int position) {
        if (position < 0 || position >= text.length()) return null;
        int lineStart = text.lastIndexOf('\n', position);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        int lineEnd = text.indexOf('\n', position);
        lineEnd = lineEnd < 0 ? text.length() : lineEnd;
        String line = text.substring(lineStart, lineEnd);
        return RepairMetadataUtil.samplePreview(
                renderInvisibleChars(line), cleanInvisibleChars(line),
                "不可见字符已转换为 Unicode 可见标记");
    }

    private String renderInvisibleChars(String text) {
        StringBuilder visible = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isInvisibleChar(ch)) {
                visible.append(String.format("⟦U+%04X⟧", (int) ch));
            } else {
                visible.append(ch);
            }
        }
        return visible.toString();
    }

    private String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' '
                || s.charAt(end - 1) == '\t'
                || s.charAt(end - 1) == '　')) {
            end--;
        }
        return end < s.length() ? s.substring(0, end) : s;
    }
}
