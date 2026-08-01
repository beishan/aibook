package com.aibook.service.repair;

import com.aibook.dto.DetectedChapterDTO;
import com.aibook.model.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 重复内容检测服务
 */
@Service
@Slf4j
public class DuplicateDetectService {

    /** 近似重复阈值 */
    private static final double HIGH_SIMILARITY_THRESHOLD = 0.95;
    private static final double MEDIUM_SIMILARITY_THRESHOLD = 0.80;

    /**
     * 扫描重复内容问题
     *
     * @param text     全文
     * @param chapters 章节列表
     * @param taskId   任务 ID
     * @return 修复问题列表
     */
    public List<TextRepairIssue> scanForIssues(
            String text, List<DetectedChapterDTO> chapters, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        // 1. 完全重复章节
        issues.addAll(detectExactDuplicateChapters(text, chapters, taskId));

        // 2. 近似重复章节
        issues.addAll(detectSimilarChapters(text, chapters, taskId));

        // 3. 重复段落
        issues.addAll(detectDuplicateParagraphs(text, chapters, taskId));

        return issues;
    }

    // ==================== 完全重复章节 ====================

    private List<TextRepairIssue> detectExactDuplicateChapters(
            String text, List<DetectedChapterDTO> chapters, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();
        Map<String, List<Integer>> hashToChapters = new HashMap<>();

        for (int i = 0; i < chapters.size(); i++) {
            DetectedChapterDTO chapter = chapters.get(i);
            String chapterText = extractChapterText(text, chapter);
            String normalized = normalizeForComparison(chapterText);
            if (normalized.isEmpty()) continue;

            String hash = Integer.toHexString(normalized.hashCode());
            hashToChapters.computeIfAbsent(hash, k -> new ArrayList<>()).add(i);
        }

        for (Map.Entry<String, List<Integer>> entry : hashToChapters.entrySet()) {
            if (entry.getValue().size() >= 2) {
                List<Integer> indices = entry.getValue();
                for (int idx = 1; idx < indices.size(); idx++) {
                    int prevIdx = indices.get(idx - 1);
                    int currIdx = indices.get(idx);
                    DetectedChapterDTO prevChapter = chapters.get(prevIdx);
                    DetectedChapterDTO currChapter = chapters.get(currIdx);
                    String duplicateText = extractChapterText(text, currChapter);

                    issues.add(TextRepairIssue.builder()
                            .taskId(taskId)
                            .chapterIndex(currIdx)
                            .type(RepairIssueType.DUPLICATE)
                            .startOffset(currChapter.getStartOffset())
                            .endOffset(currChapter.getEndOffset())
                            .originalText(duplicateText)
                            .suggestedText("")
                            .reason("完全重复章节：第" + (prevIdx + 1) + "章与第"
                                    + (currIdx + 1) + "章内容哈希一致")
                            .confidence(0.95)
                            .status(RepairIssueStatus.PENDING)
                            .source(RepairSource.AUTO)
                            .riskLevel(RiskLevel.MEDIUM)
                            .build());
                }
            }
        }

        return issues;
    }

    // ==================== 近似重复章节 ====================

    private List<TextRepairIssue> detectSimilarChapters(
            String text, List<DetectedChapterDTO> chapters, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        // 提取每章的文本
        List<String> chapterTexts = new ArrayList<>();
        for (DetectedChapterDTO chapter : chapters) {
            chapterTexts.add(normalizeForComparison(
                    extractChapterText(text, chapter)));
        }

        for (int i = 0; i < chapterTexts.size(); i++) {
            for (int j = i + 1; j < chapterTexts.size(); j++) {
                if (chapterTexts.get(i).isEmpty() || chapterTexts.get(j).isEmpty()) continue;

                double similarity = calculateSimilarity(
                        chapterTexts.get(i), chapterTexts.get(j));

                if (similarity >= HIGH_SIMILARITY_THRESHOLD) {
                    DetectedChapterDTO chapter = chapters.get(j);
                    issues.add(TextRepairIssue.builder()
                            .taskId(taskId)
                            .chapterIndex(j)
                            .type(RepairIssueType.DUPLICATE)
                            .startOffset(chapter.getStartOffset())
                            .endOffset(chapter.getEndOffset())
                            .originalText("第" + (i + 1) + "章与第" + (j + 1) + "章相似度 " +
                                    String.format("%.1f%%", similarity * 100))
                            // Similar content is only a warning. It must not become a
                            // destructive replacement until the user chooses an action.
                            .suggestedText(null)
                            .reason("高度疑似重复章节")
                            .confidence(similarity)
                            .status(RepairIssueStatus.PENDING)
                            .source(RepairSource.AUTO)
                            .riskLevel(RiskLevel.HIGH)
                            .build());
                } else if (similarity >= MEDIUM_SIMILARITY_THRESHOLD) {
                    DetectedChapterDTO chapter = chapters.get(j);
                    issues.add(TextRepairIssue.builder()
                            .taskId(taskId)
                            .chapterIndex(j)
                            .type(RepairIssueType.DUPLICATE)
                            .startOffset(chapter.getStartOffset())
                            .endOffset(chapter.getEndOffset())
                            .originalText("第" + (i + 1) + "章与第" + (j + 1) + "章相似度 " +
                                    String.format("%.1f%%", similarity * 100))
                            .suggestedText(null)
                            .reason("可能重复章节")
                            .confidence(similarity)
                            .status(RepairIssueStatus.PENDING)
                            .source(RepairSource.AUTO)
                            .riskLevel(RiskLevel.HIGH)
                            .build());
                }
            }
        }

        return issues;
    }

    // ==================== 重复段落 ====================

    private List<TextRepairIssue> detectDuplicateParagraphs(
            String text, List<DetectedChapterDTO> chapters, Long taskId) {
        List<TextRepairIssue> issues = new ArrayList<>();

        // 按段落分割全文
        String[] paragraphs = text.split("\\n{2,}");
        Map<String, List<int[]>> paragraphPositions = new HashMap<>();

        int offset = 0;
        for (String para : paragraphs) {
            String normalized = normalizeForComparison(para);
            if (normalized.length() >= 50) { // 只关注较长的段落
                paragraphPositions
                        .computeIfAbsent(normalized, k -> new ArrayList<>())
                        .add(new int[]{offset, offset + para.length()});
            }
            offset += para.length() + 2; // +2 for \n\n
        }

        for (Map.Entry<String, List<int[]>> entry : paragraphPositions.entrySet()) {
            if (entry.getValue().size() >= 2) {
                String preview = entry.getKey().length() > 50
                        ? entry.getKey().substring(0, 50) + "..." : entry.getKey();
                int[] lastPos = entry.getValue().get(entry.getValue().size() - 1);
                issues.add(TextRepairIssue.builder()
                        .taskId(taskId)
                        .chapterIndex(-1)
                        .type(RepairIssueType.DUPLICATE)
                        .startOffset(lastPos[0])
                        .endOffset(lastPos[1])
                        .originalText(text.substring(lastPos[0], lastPos[1]))
                        .suggestedText("")
                        .reason("重复段落（" + preview + "）：该段落重复出现 "
                                + entry.getValue().size() + " 次，建议删除后一处")
                        .confidence(0.85)
                        .status(RepairIssueStatus.PENDING)
                        .source(RepairSource.AUTO)
                        .riskLevel(RiskLevel.MEDIUM)
                        .build());
            }
        }

        return issues;
    }

    // ==================== 工具方法 ====================

    private String extractChapterText(String text, DetectedChapterDTO chapter) {
        if (chapter.getStartOffset() == null || chapter.getEndOffset() == null) return "";
        int start = Math.min(chapter.getStartOffset(), text.length());
        int end = Math.min(chapter.getEndOffset(), text.length());
        if (start >= end) return "";
        String chapterText = text.substring(start, end);
        int titleEnd = chapterText.indexOf('\n');
        return titleEnd >= 0 ? chapterText.substring(titleEnd + 1) : "";
    }

    private String normalizeForComparison(String text) {
        if (text == null) return "";
        // 去除所有空白字符
        return text.replaceAll("\\s+", "");
    }

    /**
     * 计算两段文本的相似度（基于 2-gram Jaccard）
     */
    private double calculateSimilarity(String text1, String text2) {
        if (text1.isEmpty() || text2.isEmpty()) return 0.0;

        Set<String> ngrams1 = buildNgrams(text1, 2);
        Set<String> ngrams2 = buildNgrams(text2, 2);

        if (ngrams1.isEmpty() || ngrams2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(ngrams1);
        intersection.retainAll(ngrams2);

        Set<String> union = new HashSet<>(ngrams1);
        union.addAll(ngrams2);

        return (double) intersection.size() / union.size();
    }

    private Set<String> buildNgrams(String text, int n) {
        Set<String> ngrams = new HashSet<>();
        for (int i = 0; i <= text.length() - n; i++) {
            ngrams.add(text.substring(i, i + n));
        }
        return ngrams;
    }
}
