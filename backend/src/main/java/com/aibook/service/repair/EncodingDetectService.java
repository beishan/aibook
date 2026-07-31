package com.aibook.service.repair;

import com.aibook.dto.EncodingDetectResult;
import com.aibook.model.entity.RepairIssueType;
import com.aibook.model.entity.RepairIssueStatus;
import com.aibook.model.entity.RepairSource;
import com.aibook.model.entity.RiskLevel;
import com.aibook.model.entity.TextRepairIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 编码检测与乱码修复服务
 */
@Service
@Slf4j
public class EncodingDetectService {

    private static final int PREVIEW_LENGTH = 500;

    /** 常见乱码特征 */
    private static final List<String> GARBLED_PATTERNS = List.of(
        "\uFFFD",       // Unicode 替换字符
        "锟斤拷",         // GBK→UTF-8 乱码
        "浣犲ソ",         // UTF-8→GBK 乱码（"你好"）
        "Ã",             // Latin-1 误读 UTF-8
        "Â",
        "â€™",           // UTF-8 被当 Latin-1 读取的右单引号
        "ï¿½"            // UTF-8 替换字符的 Latin-1 误读
    );

    /**
     * 检测文件编码
     */
    public EncodingDetectResult detectEncoding(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        return detectEncoding(bytes);
    }

    /**
     * 检测字节数组的编码
     */
    public EncodingDetectResult detectEncoding(byte[] bytes) {
        EncodingDetectResult.EncodingDetectResultBuilder result = EncodingDetectResult.builder();

        // 1. BOM 检测
        String bomType = detectBom(bytes);
        result.hasBom(!"NONE".equals(bomType));
        result.bomType(bomType);

        String encoding;
        int skipBytes = 0;

        if ("UTF-8".equals(bomType)) {
            encoding = "UTF-8";
            skipBytes = 3;
        } else if ("UTF-16LE".equals(bomType)) {
            encoding = "UTF-16LE";
            skipBytes = 2;
        } else if ("UTF-16BE".equals(bomType)) {
            encoding = "UTF-16BE";
            skipBytes = 2;
        } else {
            // 2. 无 BOM，尝试各种编码
            encoding = detectWithoutBom(bytes);
        }

        result.encoding(encoding);
        result.confidence(calculateConfidence(bytes, encoding, skipBytes));

        // 3. 解码预览
        String preview = tryDecode(bytes, encoding, skipBytes);
        result.previewText(preview != null && preview.length() > PREVIEW_LENGTH
                ? preview.substring(0, PREVIEW_LENGTH) : preview);

        // 4. 乱码检测
        int anomalyCount = countAnomalies(preview);
        result.anomalyCount(anomalyCount);
        result.hasGarbled(anomalyCount > 0);
        result.garbledType(anomalyCount > 0 ? identifyGarbledType(preview) : null);

        // 5. 候选编码列表
        result.candidateEncodings(getCandidateEncodings(bytes));

        return result.build();
    }

    /**
     * 使用指定编码解码并返回预览文本
     */
    public String decodeWithEncoding(Path filePath, String encoding) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        return decodeWithEncoding(bytes, encoding);
    }

    /**
     * 使用指定编码解码字节数组
     */
    public String decodeWithEncoding(byte[] bytes, String encoding) {
        if ("AUTO".equalsIgnoreCase(encoding)) {
            return tryDecode(bytes, detectEncoding(bytes).getEncoding(), 0);
        }

        int skip = getBomSkip(bytes, encoding);
        return tryDecode(bytes, encoding, skip);
    }

    /**
     * 尝试二次编码修复，生成候选结果
     */
    public List<String> tryEncodingRepair(String garbledText) {
        List<String> candidates = new ArrayList<>();

        // 常见错误编码路径
        Map<String, String> repairPaths = new LinkedHashMap<>();
        repairPaths.put("UTF-8 → GBK → UTF-8", "utf8_to_gbk");
        repairPaths.put("GBK → UTF-8 → GBK", "gbk_to_utf8");
        repairPaths.put("ISO-8859-1 → UTF-8", "latin1_to_utf8");
        repairPaths.put("Big5 → UTF-8", "big5_to_utf8");

        for (Map.Entry<String, String> entry : repairPaths.entrySet()) {
            try {
                String repaired = attemptRepair(garbledText, entry.getValue());
                if (repaired != null && !repaired.equals(garbledText) && isReadableChinese(repaired)) {
                    candidates.add(repaired);
                }
            } catch (Exception e) {
                log.debug("编码修复尝试失败: {}", entry.getKey());
            }
        }

        return candidates;
    }

    /**
     * 扫描全文，生成编码/乱码相关的修复问题
     */
    public List<TextRepairIssue> scanForIssues(String text, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        // 1. 检测 Unicode 替换字符
        int replaceCharCount = countChar(text, '\uFFFD');
        if (replaceCharCount > 0) {
            issues.add(TextRepairIssue.builder()
                    .taskId(taskId)
                    .type(RepairIssueType.ENCODING)
                    .originalText("发现 " + replaceCharCount + " 处 Unicode 替换字符（）")
                    .suggestedText(null)
                    .reason("原始字符可能已经在之前的解码过程中丢失，无法保证自动恢复。")
                    .confidence(0.9)
                    .status(RepairIssueStatus.PENDING)
                    .source(RepairSource.AUTO)
                    .riskLevel(RiskLevel.HIGH)
                    .build());
        }

        // 2. 检测常见乱码特征
        for (String pattern : GARBLED_PATTERNS) {
            if (!pattern.equals("\uFFFD")) {
                int count = countOccurrences(text, pattern);
                if (count > 0) {
                    // 尝试修复
                    List<String> candidates = tryEncodingRepair(pattern);
                    issues.add(TextRepairIssue.builder()
                            .taskId(taskId)
                            .type(RepairIssueType.ENCODING)
                            .originalText("发现乱码特征: " + pattern + "（" + count + " 处）")
                            .suggestedText(candidates.isEmpty() ? null : candidates.get(0))
                            .reason("检测到常见编码乱码特征，可尝试二次编码修复。")
                            .confidence(0.7)
                            .status(RepairIssueStatus.PENDING)
                            .source(RepairSource.AUTO)
                            .riskLevel(RiskLevel.MEDIUM)
                            .metadataJson(candidates.isEmpty() ? null : toJsonArray(candidates))
                            .build());
                }
            }
        }

        // 3. 检测连续异常字符
        int consecutiveAnomaly = detectConsecutiveAnomalies(text);
        if (consecutiveAnomaly > 0) {
            issues.add(TextRepairIssue.builder()
                    .taskId(taskId)
                    .type(RepairIssueType.ENCODING)
                    .originalText("发现 " + consecutiveAnomaly + " 处连续异常字符")
                    .suggestedText(null)
                    .reason("文本中存在大量连续异常字符，可能由编码错误导致。")
                    .confidence(0.6)
                    .status(RepairIssueStatus.PENDING)
                    .source(RepairSource.AUTO)
                    .riskLevel(RiskLevel.MEDIUM)
                    .build());
        }

        return issues;
    }

    // ==================== 内部方法 ====================

    private String detectBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return "UTF-8";
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xFE) {
            return "UTF-16LE";
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFE
                && (bytes[1] & 0xFF) == 0xFF) {
            return "UTF-16BE";
        }
        return "NONE";
    }

    private String detectWithoutBom(byte[] bytes) {
        // 尝试 UTF-8 严格解码
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
            return "UTF-8";
        } catch (CharacterCodingException e) {
            log.debug("UTF-8 严格解码失败");
        }

        // 尝试 GB18030（GBK 超集）
        try {
            CharsetDecoder decoder = Charset.forName("GB18030").newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
            return "GB18030";
        } catch (CharacterCodingException e) {
            log.debug("GB18030 严格解码失败");
        }

        // 尝试 Big5
        try {
            CharsetDecoder decoder = Charset.forName("Big5").newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
            return "Big5";
        } catch (CharacterCodingException e) {
            log.debug("Big5 严格解码失败");
        }

        // 回退到 GB18030 宽松模式
        return "GB18030";
    }

    private double calculateConfidence(byte[] bytes, String encoding, int skipBytes) {
        try {
            String decoded = tryDecode(bytes, encoding, skipBytes);
            if (decoded == null) return 0.0;

            int chineseCount = 0;
            int anomalyCount = 0;
            int totalChars = Math.min(decoded.length(), 1000);

            for (int i = 0; i < totalChars; i++) {
                char c = decoded.charAt(i);
                if (c >= '\u4E00' && c <= '\u9FFF') {
                    chineseCount++;
                } else if (c == '\uFFFD') {
                    anomalyCount++;
                }
            }

            if (totalChars == 0) return 0.5;
            double chineseRatio = (double) chineseCount / totalChars;
            double anomalyRatio = (double) anomalyCount / totalChars;

            return Math.max(0, Math.min(1, chineseRatio * 2 - anomalyRatio * 3));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String tryDecode(byte[] bytes, String encoding, int skipBytes) {
        try {
            if (skipBytes > 0 && bytes.length > skipBytes) {
                return new String(bytes, skipBytes, bytes.length - skipBytes,
                        Charset.forName(encoding));
            }
            return new String(bytes, Charset.forName(encoding));
        } catch (Exception e) {
            return null;
        }
    }

    private String decodeWithEncoding(byte[] bytes, String encoding, int skipBytes) {
        return tryDecode(bytes, encoding, skipBytes);
    }

    private int getBomSkip(byte[] bytes, String encoding) {
        String bomType = detectBom(bytes);
        if ("UTF-8".equals(encoding) && "UTF-8".equals(bomType)) return 3;
        if ("UTF-16LE".equals(encoding) && "UTF-16LE".equals(bomType)) return 2;
        if ("UTF-16BE".equals(encoding) && "UTF-16BE".equals(bomType)) return 2;
        return 0;
    }

    private int countAnomalies(String text) {
        if (text == null) return 0;
        int count = 0;
        for (String pattern : GARBLED_PATTERNS) {
            count += countOccurrences(text, pattern);
        }
        // 控制字符
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                count++;
            }
        }
        return count;
    }

    private String identifyGarbledType(String text) {
        if (text.contains("锟斤拷")) return "GBK→UTF-8 乱码";
        if (text.contains("浣犲ソ")) return "UTF-8→GBK 乱码";
        if (text.contains("Ã") || text.contains("Â")) return "Latin-1 误读 UTF-8";
        if (text.contains("\uFFFD")) return "Unicode 替换字符（不可恢复）";
        return "未知乱码类型";
    }

    private List<String> getCandidateEncodings(byte[] bytes) {
        List<String> candidates = new ArrayList<>();
        for (String enc : List.of("UTF-8", "GB18030", "Big5", "UTF-16LE", "UTF-16BE")) {
            try {
                CharsetDecoder decoder = Charset.forName(enc).newDecoder();
                decoder.onMalformedInput(CodingErrorAction.REPORT);
                decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                decoder.decode(ByteBuffer.wrap(bytes));
                candidates.add(enc);
            } catch (CharacterCodingException ignored) {
                // 该编码无法严格解码
            }
        }
        return candidates;
    }

    private String attemptRepair(String garbledText, String repairPath) {
        try {
            return switch (repairPath) {
                case "utf8_to_gbk" -> {
                    // 当前是 UTF-8 被错误读为 GBK，再编码回 GBK 字节后用 UTF-8 解码
                    byte[] gbkBytes = garbledText.getBytes(Charset.forName("GB18030"));
                    yield new String(gbkBytes, StandardCharsets.UTF_8);
                }
                case "gbk_to_utf8" -> {
                    // 当前是 GBK 被错误读为 UTF-8，再编码回 UTF-8 字节后用 GBK 解码
                    byte[] utf8Bytes = garbledText.getBytes(StandardCharsets.UTF_8);
                    yield new String(utf8Bytes, Charset.forName("GB18030"));
                }
                case "latin1_to_utf8" -> {
                    byte[] latin1Bytes = garbledText.getBytes(StandardCharsets.ISO_8859_1);
                    yield new String(latin1Bytes, StandardCharsets.UTF_8);
                }
                case "big5_to_utf8" -> {
                    byte[] big5Bytes = garbledText.getBytes(StandardCharsets.UTF_8);
                    yield new String(big5Bytes, Charset.forName("Big5"));
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isReadableChinese(String text) {
        if (text == null || text.isEmpty()) return false;
        int chineseCount = 0;
        int total = Math.min(text.length(), 100);
        for (int i = 0; i < total; i++) {
            char c = text.charAt(i);
            if (c >= '\u4E00' && c <= '\u9FFF') chineseCount++;
            if (c == '\uFFFD') return false;
        }
        return chineseCount > total * 0.3;
    }

    private int detectConsecutiveAnomalies(String text) {
        int count = 0;
        int consecutive = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x80 && c <= 0xBF && c != '\uFFFD') {
                consecutive++;
                if (consecutive >= 3) {
                    count++;
                    consecutive = 0;
                }
            } else {
                consecutive = 0;
            }
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

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(sub, index)) != -1) {
            count++;
            index += sub.length();
        }
        return count;
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
