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
