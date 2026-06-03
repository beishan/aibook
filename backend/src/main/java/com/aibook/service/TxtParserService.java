package com.aibook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * TXT/MD 文本解析服务
 * 负责编码检测、章节解析、段落归一化
 */
@Service
@Slf4j
public class TxtParserService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 章节匹配正则 ==========

    private static final List<Pattern> CHAPTER_PATTERNS = List.of(
        // 中文数字格式：第X章/回/节/卷/篇/部
        Pattern.compile("^第[一二三四五六七八九十百千万零壹贰叁肆伍陆柒捌玖拾佰仟\\d]+[章回节卷篇部]"),
        // 英文格式：Chapter X / CHAPTER X
        Pattern.compile("^[Cc][Hh][Aa][Pp][Tt][Ee][Rr]\\s+\\d+.*"),
        // 卷X格式
        Pattern.compile("^卷[一二三四五六七八九十\\d]+.*"),
        // 特殊章节
        Pattern.compile("^(序章|序幕|楔子|尾声|终章|后记|前言|引言|番外|附录).*"),
        // 数字编号：1. 标题 / 一、标题（仅匹配短标题行，排除对话和长句）
        Pattern.compile("^[一二三四五六七八九十]+[、\\.．]\\s*\\S{1,30}$"),
        Pattern.compile("^\\d{1,4}[、\\.．]\\s*\\S{1,30}$"),
        // 方括号标题（网文常见）— 仅匹配短标题，排除对话和问句
        Pattern.compile("^【(?!.*[？。！?!…])[^】]{1,30}】$"),
        // Markdown 标题
        Pattern.compile("^#{1,3}\\s+.{1,100}")
    );

    // 句子结束标点
    private static final String SENTENCE_ENDINGS = "。！？!?…";

    /**
     * 解析 TXT 文件的章节信息
     */
    public String parseChapters(Path filePath) throws IOException {
        String text = readFileWithEncoding(filePath);
        List<ChapterInfo> chapters = detectChapters(text);
        return objectMapper.writeValueAsString(chapters);
    }

    /**
     * 读取文件并自动检测编码
     */
    public String readFileWithEncoding(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        return decodeBytes(bytes);
    }

    /**
     * 对原始文本做段落归一化处理
     */
    public String processText(String rawText) {
        return normalizeParagraphs(rawText);
    }

    // ==================== 编码检测 ====================

    private String decodeBytes(byte[] bytes) {
        if (bytes.length == 0) return "";

        // BOM 检测
        if (bytes.length >= 3 &&
            (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 &&
            (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 2 &&
            (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }

        // 尝试 UTF-8 严格解码
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(bytes));
            return charBuffer.toString();
        } catch (CharacterCodingException e) {
            log.debug("UTF-8 解码失败，回退到 GBK");
        }

        // 回退到 GBK (GB18030 是 GBK 的超集)
        return new String(bytes, Charset.forName("GB18030"));
    }

    // ==================== 章节检测 ====================

    private List<ChapterInfo> detectChapters(String text) {
        String[] lines = text.split("\n", -1);

        // Tier 1: 正则匹配
        List<ChapterInfo> regexChapters = detectByRegex(lines, text);
        if (regexChapters.size() >= 2) {
            fillEndIndices(regexChapters, text.length());
            log.debug("Tier 1 正则匹配到 {} 个章节", regexChapters.size());
            return regexChapters;
        }

        // Tier 2: 空行启发式
        List<ChapterInfo> heuristicChapters = detectByBlankLines(lines, text);
        if (heuristicChapters.size() >= 3) {
            fillEndIndices(heuristicChapters, text.length());
            log.debug("Tier 2 启发式匹配到 {} 个章节", heuristicChapters.size());
            return heuristicChapters;
        }

        // Tier 3: 按字数切分
        List<ChapterInfo> lengthChapters = detectByLength(text);
        log.debug("Tier 3 按字数切分为 {} 个部分", lengthChapters.size());
        return lengthChapters;
    }

    /**
     * Tier 1: 正则匹配章节标题
     */
    private List<ChapterInfo> detectByRegex(String[] lines, String text) {
        List<ChapterInfo> chapters = new ArrayList<>();
        int charOffset = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && isValidChapterTitle(trimmed)) {
                boolean matched = CHAPTER_PATTERNS.stream()
                    .anyMatch(p -> p.matcher(trimmed).find());
                if (matched) {
                    chapters.add(new ChapterInfo(trimmed, charOffset, 0));
                }
            }
            charOffset += line.length() + 1; // +1 for \n
        }
        return chapters;
    }

    /**
     * Tier 2: 空行启发式 — 连续 2+ 空行后的短行作为章节标题
     */
    private List<ChapterInfo> detectByBlankLines(String[] lines, String text) {
        List<ChapterInfo> chapters = new ArrayList<>();
        int blankRun = 0;
        int charOffset = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                blankRun++;
            } else {
                if (blankRun >= 2) {
                    // 连续 2+ 空行后的非空行，且长度合理，视为章节标题
                    if (trimmed.length() >= 2 && trimmed.length() <= 50
                            && isValidChapterTitle(trimmed)) {
                        chapters.add(new ChapterInfo(trimmed, charOffset, 0));
                    }
                }
                blankRun = 0;
            }
            charOffset += line.length() + 1;
        }
        return chapters;
    }

    /**
     * 判断是否为合法的章节标题（排除对话、分隔符、纯标点等）
     */
    private boolean isValidChapterTitle(String text) {
        if (text.length() < 2 || text.length() > 80) return false;

        // 排除纯分隔符行：--- === *** ~~~
        if (text.matches("^[\\-=*~─━═]{3,}\\s*$")) return false;

        // 排除包含问号、感叹号、省略号的行（对话或感叹句）
        if (text.contains("？") || text.contains("。") || text.contains("！")
                || text.contains("?") || text.contains("!") || text.contains("…")) {
            // 但允许 "第X章" 后面带标点，如 "第一章？"
            if (!text.matches("^第.*[章回节卷篇部].*")) return false;
        }

        // 排除以对话引导符号开头的行
        char first = text.charAt(0);
        if (first == '“' || first == '”' || first == '\'' || first == '"') return false;

        // 排除纯标点或纯数字
        if (text.matches("^[\\p{Punct}\\p{IsDigit}\\s]+$")) return false;

        // 排除包含【】但内容是对话的（含"吗""呢""吧""啊"等语气词）
        if (text.startsWith("【") && text.endsWith("】")) {
            String inner = text.substring(1, text.length() - 1);
            if (inner.matches(".*[吗呢吧啊呀哦嗯嘛呐].*")) return false;
            if (inner.length() > 20) return false; // 太长的方括号内容不是标题
        }

        return true;
    }

    /**
     * Tier 3: 按字数切分（最后手段）
     */
    private List<ChapterInfo> detectByLength(String text) {
        List<ChapterInfo> chapters = new ArrayList<>();
        int totalLen = text.length();

        if (totalLen < 10000) {
            chapters.add(new ChapterInfo("全文", 0, totalLen));
            return chapters;
        }

        int targetSize = 5000;
        int pos = 0;
        int partNum = 1;

        while (pos < totalLen) {
            int end = Math.min(pos + targetSize, totalLen);

            // 在目标位置前 500 字内找句子边界
            if (end < totalLen) {
                int searchStart = Math.max(end - 500, pos);
                int bestBreak = end;
                for (int j = searchStart; j < end; j++) {
                    if (SENTENCE_ENDINGS.indexOf(text.charAt(j)) >= 0) {
                        bestBreak = j + 1;
                    }
                }
                end = bestBreak;
            }

            chapters.add(new ChapterInfo("第" + partNum + "部分", pos, end));
            pos = end;
            partNum++;
        }

        return chapters;
    }

    /**
     * 填充每个章节的 endIndex
     */
    private void fillEndIndices(List<ChapterInfo> chapters, int totalLength) {
        for (int i = 0; i < chapters.size() - 1; i++) {
            chapters.get(i).setEndIndex(chapters.get(i + 1).getStartIndex());
        }
        chapters.get(chapters.size() - 1).setEndIndex(totalLength);
    }

    // ==================== 段落归一化 ====================

    private String normalizeParagraphs(String rawText) {
        // 统一换行符
        String text = rawText.replace("\r\n", "\n").replace("\r", "\n");

        String[] lines = text.split("\n", -1);

        // 检测文本结构
        TextStructure structure = detectStructure(lines);

        return switch (structure) {
            case CONTINUOUS -> normalizeContinuous(text);
            case WELL_FORMATTED -> normalizeWellFormatted(lines);
            case LINE_BREAK -> normalizeLineBreak(lines);
        };
    }

    /**
     * 检测文本结构类型
     */
    private TextStructure detectStructure(String[] lines) {
        if (lines.length < 3 && lines.length > 0) {
            // 总行数很少但文字量大 → 连续文本
            int totalChars = 0;
            for (String line : lines) totalChars += line.length();
            if (totalChars > 1000) return TextStructure.CONTINUOUS;
        }

        // 统计有缩进的行比例
        int nonEmptyCount = 0;
        int indentedCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                nonEmptyCount++;
                if (line.startsWith("  ") || line.startsWith("　") || line.startsWith("\t")) {
                    indentedCount++;
                }
            }
        }

        if (nonEmptyCount > 0 && (double) indentedCount / nonEmptyCount > 0.5) {
            return TextStructure.WELL_FORMATTED;
        }

        return TextStructure.LINE_BREAK;
    }

    /**
     * 连续文本模式：按句号切分
     */
    private String normalizeContinuous(String text) {
        StringBuilder sb = new StringBuilder();
        int start = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (SENTENCE_ENDINGS.indexOf(c) >= 0) {
                String segment = text.substring(start, i + 1).trim();
                if (!segment.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(segment);
                }
                start = i + 1;
                // 跳过标点后的空白
                while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                    start++;
                }
            }
        }

        // 处理末尾无标点的部分
        if (start < text.length()) {
            String segment = text.substring(start).trim();
            if (!segment.isEmpty()) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append(segment);
            }
        }

        return sb.toString();
    }

    /**
     * 有换行模式：合并短行，缩进处断段
     */
    private String normalizeLineBreak(String[] lines) {
        StringBuilder sb = new StringBuilder();
        StringBuilder currentPara = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                // 空行 → 段落分隔
                if (!currentPara.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(currentPara);
                    currentPara.setLength(0);
                }
                continue;
            }

            // 有缩进 → 新段落开始
            boolean isNewPara = line.startsWith("  ") ||
                                line.startsWith("　") ||
                                line.startsWith("\t");

            if (isNewPara && !currentPara.isEmpty()) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append(currentPara);
                currentPara.setLength(0);
            }

            if (!currentPara.isEmpty()) {
                // 行首有标点（如引号）则直接拼接，否则加空格
                char firstChar = trimmed.charAt(0);
                if (isPunctuation(firstChar)) {
                    currentPara.append(trimmed);
                } else {
                    currentPara.append(trimmed);
                }
            } else {
                currentPara.append(trimmed);
            }
        }

        // 最后一段
        if (!currentPara.isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(currentPara);
        }

        return sb.toString();
    }

    /**
     * 格式良好模式：保留结构，规范化多余空行
     */
    private String normalizeWellFormatted(String[] lines) {
        StringBuilder sb = new StringBuilder();
        int consecutiveBlanks = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                consecutiveBlanks++;
                if (consecutiveBlanks <= 2) {
                    sb.append("\n");
                }
            } else {
                consecutiveBlanks = 0;
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append("\n");
                }
                sb.append(trimmed).append("\n");
            }
        }

        // 规范化为段落分隔
        String result = sb.toString();
        // 连续换行 → 双换行
        result = result.replaceAll("\n{3,}", "\n\n");
        return result.trim();
    }

    private boolean isPunctuation(char c) {
        // 中文标点 + 英文标点
        return Character.getType(c) == Character.OTHER_PUNCTUATION
            || Character.getType(c) == Character.DASH_PUNCTUATION
            || ",.;:!?\"'()[]{}".indexOf(c) >= 0;
    }

    // ==================== 内部类型 ====================

    enum TextStructure {
        CONTINUOUS,     // 整段文字连在一起
        LINE_BREAK,     // 有换行但不规范
        WELL_FORMATTED  // 格式良好（有缩进）
    }

    @Data
    @AllArgsConstructor
    public static class ChapterInfo {
        private String title;
        private int startIndex;
        private int endIndex;
    }
}
