package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerTask;
import com.aibook.model.entity.CrawlerTaskLog;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.repository.CrawlerSiteRepository;
import com.aibook.repository.CrawlerTaskLogRepository;
import com.aibook.repository.CrawlerTaskRepository;
import com.aibook.service.OperationLogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CrawlerTaskServiceLoggingTest {
    @Test
    void stopsAfterConfiguredConsecutiveRequestFailures() {
        CrawlerTaskService.RequestFailureGuard guard = new CrawlerTaskService.RequestFailureGuard(5);

        for (int attempt = 1; attempt < 5; attempt++) {
            guard.failure(new IllegalStateException("代理连接失败"));
            assertThat(guard.consecutiveFailures()).isEqualTo(attempt);
        }

        assertThatThrownBy(() -> guard.failure(new IllegalStateException("代理连接失败")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("连续请求失败 5 次")
                .hasMessageContaining("代理连接失败");
    }

    @Test
    void successfulRequestResetsConsecutiveFailureCount() {
        CrawlerTaskService.RequestFailureGuard guard = new CrawlerTaskService.RequestFailureGuard(2);
        guard.failure(new IllegalStateException("第一次失败"));

        guard.success();

        guard.failure(new IllegalStateException("重新计数后的第一次失败"));
        assertThat(guard.consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void contentPreviewFlattensWhitespaceAndLimitsToFiftyCharacters() {
        String preview = CrawlerTaskService.contentPreview("  第一行\n\t第二行  " + "字".repeat(50));

        assertThat(preview).doesNotContain("\n", "\r", "\t").endsWith("…");
        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(51);
    }

    @Test
    void contentPreviewDoesNotSplitSupplementaryCharacters() {
        String preview = CrawlerTaskService.contentPreview("😀".repeat(51));

        assertThat(preview).isEqualTo("😀".repeat(50) + "…");
    }

    @Test
    void contentPreviewHandlesEmptyContent() {
        assertThat(CrawlerTaskService.contentPreview(" \n\t ")).isEqualTo("(空)");
    }

    @Test
    void matchesAnyConfiguredContentFailureMarkerIgnoringCase() {
        CrawlerSite site = CrawlerSite.builder()
                .contentFailureMarkers("以下内容为VIP专属，升级会员即可继续阅读\nAccess Denied").build();

        assertThat(CrawlerTaskService.matchedContentFailureMarker(site,
                "本章提示：以下内容为VIP专属，升级会员即可继续阅读"))
                .isEqualTo("以下内容为VIP专属，升级会员即可继续阅读");
        assertThat(CrawlerTaskService.matchedContentFailureMarker(site, "ACCESS DENIED"))
                .isEqualTo("Access Denied");
        assertThat(CrawlerTaskService.matchedContentFailureMarker(site, "正常章节正文")).isNull();
    }

    @Test
    void chapterDetailIsStoredOutsideTheSystemOperationLog() {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(3L).site(site).bookName("示例书").build();
        CrawlerTask task = CrawlerTask.builder().user(user).site(site).crawlerBook(book)
                .type(CrawlerTask.TaskType.BOOK_CONTENT).build();
        CrawlerTaskLogRepository crawlerLogs = mock(CrawlerTaskLogRepository.class);
        OperationLogService operationLogs = mock(OperationLogService.class);
        CrawlerTaskService service = new CrawlerTaskService(
                mock(CrawlerSiteRepository.class),
                mock(com.aibook.repository.CrawlerDiscoveryPageRepository.class),
                mock(CrawlerBookRepository.class),
                mock(CrawlerChapterRepository.class),
                mock(CrawlerTaskRepository.class),
                mock(com.aibook.repository.CrawlerScanResultRepository.class),
                crawlerLogs,
                mock(CrawlerManagementService.class),
                operationLogs,
                mock(CrawlerExportService.class),
                mock(CrawlerHttpClient.class),
                List.of(),
                mock(ApplicationContext.class), mock(com.aibook.service.CrawlerSettingsService.class));
        try {
            service.recordCrawlerDetail(task, "章节采集完毕", "章节：第一章；预览：正文");

            ArgumentCaptor<CrawlerTaskLog> saved = ArgumentCaptor.forClass(CrawlerTaskLog.class);
            verify(crawlerLogs).save(saved.capture());
            assertThat(saved.getValue().getCrawlerBookId()).isEqualTo(3L);
            assertThat(saved.getValue().getDescription()).contains("章节采集完毕", "示例书");
            verifyNoInteractions(operationLogs);
        } finally {
            service.shutdown();
        }
    }
}
