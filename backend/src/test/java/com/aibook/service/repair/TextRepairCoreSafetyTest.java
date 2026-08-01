package com.aibook.service.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aibook.dto.DetectedChapterDTO;
import com.aibook.model.entity.MatchScope;
import com.aibook.model.entity.RepairAction;
import com.aibook.model.entity.RepairIssueType;
import com.aibook.model.entity.RiskLevel;
import com.aibook.model.entity.TextRepairIssue;
import com.aibook.model.entity.TextRepairRule;
import com.aibook.repository.TextRepairRuleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextRepairCoreSafetyTest {

    private final ChapterDetectService chapterDetectService = new ChapterDetectService();
    private final ChapterNormalizeService chapterNormalizeService = new ChapterNormalizeService();
    private final ParagraphFixService paragraphFixService = new ParagraphFixService();
    private final DuplicateDetectService duplicateDetectService = new DuplicateDetectService();
    private final PunctuationFixService punctuationFixService = new PunctuationFixService();
    private final EncodingDetectService encodingDetectService = new EncodingDetectService();

    @Test
    void chapterNormalizationOnlyReplacesTheTitleLine() {
        String text = "正文  第一章：：  开始\n这里是正文，不能被标题替换。\n\n第二章 继续\n第二章正文。";
        List<DetectedChapterDTO> chapters = chapterDetectService.detectChapters(text);

        List<TextRepairIssue> issues = chapterNormalizeService.scanForIssues(
                chapters, ChapterNormalizeService.DEFAULT_FORMAT, 1L);

        TextRepairIssue first = issues.get(0);
        assertThat(first.getEndOffset() - first.getStartOffset())
                .isEqualTo(first.getOriginalText().length());
        assertThat(first.getEndOffset()).isLessThan(chapters.get(0).getEndOffset());
        assertThat(text.substring(first.getStartOffset(), first.getEndOffset()))
                .isEqualTo(first.getOriginalText());
    }

    @Test
    void excessiveBlankLinesProduceReplacementTextInsteadOfAnInstructionLabel() {
        String text = "第一行\n\n\n\n第二行";

        TextRepairIssue issue = paragraphFixService
                .scanForIssues(text, text.split("\n", -1), 1L).stream()
                .filter(item -> item.getReason().contains("多余空行"))
                .findFirst()
                .orElseThrow();

        assertThat(issue.getSuggestedText()).isEqualTo("\n");
        assertThat(issue.getMetadataJson())
                .contains("\"blankLineCount\":3")
                .contains("\"contextBefore\":\"第一行\"")
                .contains("\"contextAfter\":\"第二行\"");
    }

    @Test
    void brokenLineReplacementDoesNotConsumeTheFollowingLineBreak() {
        String text = "一句话被拆成\n两行。\n下一段。";

        TextRepairIssue issue = paragraphFixService
                .scanForIssues(text, text.split("\n", -1), 1L).stream()
                .filter(item -> item.getReason().contains("拆成多行"))
                .findFirst()
                .orElseThrow();

        assertThat(text.substring(issue.getStartOffset(), issue.getEndOffset()))
                .isEqualTo(issue.getOriginalText());
        String repaired = text.substring(0, issue.getStartOffset())
                + issue.getSuggestedText() + text.substring(issue.getEndOffset());
        assertThat(repaired).isEqualTo("一句话被拆成两行。\n下一段。");
    }

    @Test
    void duplicateParagraphOffsetsRemainExactWithThreeBlankLines() {
        String paragraph = "这是一个用于验证重复段落偏移的长段落，内容必须超过五十个字符，"
                + "并且第二次出现时仍然可以准确定位原文范围而不会发生偏移。";
        String text = paragraph + "\n\n\n短过渡。\n\n\n" + paragraph;

        TextRepairIssue issue = duplicateDetectService
                .scanForIssues(text, List.of(), 1L).stream()
                .filter(item -> item.getReason().startsWith("重复段落"))
                .findFirst()
                .orElseThrow();

        assertThat(text.substring(issue.getStartOffset(), issue.getEndOffset()))
                .isEqualTo(issue.getOriginalText())
                .isEqualTo(paragraph);
        assertThat(issue.getSuggestedText()).isEmpty();
    }

    @Test
    void wholeDocumentCleanupIssuesExposeARealSamplePreview() {
        String text = "这一行末尾有空格   \n下一行";

        TextRepairIssue issue = punctuationFixService
                .scanForIssues(text, text.split("\n", -1), 1L).stream()
                .filter(item -> item.getReason().contains("行尾多余空格"))
                .findFirst()
                .orElseThrow();

        assertThat(issue.getMetadataJson())
                .contains("previewOriginal")
                .contains("行尾空白")
                .contains("previewSuggested");
    }

    @Test
    void recoverableEncodingPatternCarriesAnActionableReplacementKey() {
        TextRepairIssue issue = encodingDetectService.scanForIssues("开头浣犲ソ结尾", 1L).stream()
                .filter(item -> item.getOriginalText().contains("浣犲ソ"))
                .findFirst()
                .orElseThrow();

        assertThat(issue.getMetadataJson())
                .contains("\"garbledPattern\":\"浣犲ソ\"")
                .contains("\"candidates\"")
                .contains("\"previewOriginal\"");
    }

    @Test
    void exactDuplicateChapterUsesAnEmptyReplacement() {
        String text = "第一章 A\n相同正文。\n\n第二章 B\n相同正文。";
        List<DetectedChapterDTO> chapters = chapterDetectService.detectChapters(text);

        TextRepairIssue issue = duplicateDetectService
                .scanForIssues(text, chapters, 1L).stream()
                .filter(item -> item.getReason().startsWith("完全重复章节"))
                .findFirst()
                .orElseThrow();

        assertThat(issue.getSuggestedText()).isEmpty();
        assertThat(issue.getOriginalText()).doesNotContain("内容完全一致");
    }

    @Test
    void customReplaceRuleProducesConcreteReplacementText() {
        AdDetectService service = new AdDetectService(mock(TextRepairRuleRepository.class));
        TextRepairRule rule = TextRepairRule.builder()
                .id(9L)
                .name("替换站点名")
                .type(RepairIssueType.AD)
                .pattern("坏站点")
                .matchScope(MatchScope.LINE)
                .action(RepairAction.REPLACE)
                .replacement("可信来源")
                .riskLevel(RiskLevel.LOW)
                .enabled(true)
                .build();
        String text = "内容来自坏站点\n正文";

        TextRepairIssue issue = service.scanForIssues(
                        text, text.split("\n", -1), List.of(), 1L, List.of(rule))
                .stream()
                .filter(item -> "9".equals(item.getRuleId()))
                .findFirst()
                .orElseThrow();

        assertThat(issue.getSuggestedText()).isEqualTo("内容来自可信来源");
    }

    @Test
    void whitelistSuppressesAdvertisementDetection() {
        AdDetectService service = new AdDetectService(mock(TextRepairRuleRepository.class));
        TextRepairRule whitelist = TextRepairRule.builder()
                .name("可信网址")
                .type(RepairIssueType.AD)
                .pattern("example\\.com")
                .matchScope(MatchScope.LINE)
                .action(RepairAction.MARK_ONLY)
                .riskLevel(RiskLevel.LOW)
                .enabled(true)
                .whitelist(true)
                .build();
        String text = "项目主页 https://example.com";

        assertThat(service.scanForIssues(
                text, new String[]{text}, List.of(), 1L, List.of(whitelist))).isEmpty();
    }
}
